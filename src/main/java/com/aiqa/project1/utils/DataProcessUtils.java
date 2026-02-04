package com.aiqa.project1.utils;

import com.aiqa.project1.pojo.document.DocumentMetadata;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;


/**
 * 数据处理主流程 - 适配InputStream输入
 * 负责文档的提取、清洗、分块、向量化和存储
 */
@Component
public class DataProcessUtils {
    private static final Logger log = LoggerFactory.getLogger(DataProcessUtils.class);
    private static final int MAX_RETRY_TIMES = 3;
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final String DOUBAO_MODEL = "douBao";

    private final TextStructUtil textStructUtil;
    private final MilvusSearchUtils milvusSearchUtils;
    private final MilvusClientV2 milvusClient;
    private final AsyncTaskExecutor asyncTaskExecutor;
    private final Gson gson;
    private final ChildParentTextStructUtil childParentTextStructUtil;
    private final MilvusSearchUtils1 milvusSearchUtils1;

    @Value("${milvus.collection-name}")
    private String collectionName;

    @Autowired
    public DataProcessUtils(
            TextStructUtil textStructUtil,
            MilvusSearchUtils milvusSearchUtils,
            MilvusClientV2 milvusClient,
            AsyncTaskExecutor asyncTaskExecutor, ChildParentTextStructUtil childParentTextStructUtil, MilvusSearchUtils1 milvusSearchUtils1) {
        this.textStructUtil = textStructUtil;
        this.milvusSearchUtils = milvusSearchUtils;
        this.milvusClient = milvusClient;
        this.asyncTaskExecutor = asyncTaskExecutor;
        this.gson = new Gson();
        this.childParentTextStructUtil = childParentTextStructUtil;
        this.milvusSearchUtils1 = milvusSearchUtils1;
    }


    public String processDocument(String userId, String tagName, MultipartFile file) {
        try {
            return processDocument(userId, file.getInputStream(), file.getOriginalFilename(), tagName);
        } catch (IOException e) {
            throw new BusinessException(500, e.getMessage(), null);
        }
    }

    /**
     * 处理文档数据的主流程（核心修改：替换为InputStream输入）
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param inputStream 文档IO流
     * @param fileName 文件名（含后缀，用于解析文档类型）
     * @return 文档摘要
     */
    public String processDocument(String userId, Integer sessionId, InputStream inputStream, String fileName) {
        try {
            // 1. 文档提取和清洗（适配InputStream）
            String cleanContent = extractAndCleanText(inputStream, fileName);

            // 2. 父子分块（逻辑完全复用）
            List<TextSegment> segments = splitDocument(cleanContent);

            // 3. 提取元数据
            DocumentMetadata metadata = extractMetadata(segments, fileName);

            // 4. 向量化处理
            Map<Embedding, TextSegment> vectorizedSegments = vectorizeSegments(segments);

            // 5. 存储到Milvus向量库
            storeToVectorDatabase(userId, sessionId, vectorizedSegments, metadata, fileName);

            return metadata.getAbstractText();
        } catch (Exception e) {
            log.error("文档处理失败: {}", fileName, e);
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理文档数据的主流程（核心修改：替换为InputStream输入）
     * @param userId 用户ID
     * @param inputStream 文档IO流
     * @param fileName 文件名（含后缀，用于解析文档类型）
     * @return 文档摘要
     */
    public String processDocument(String userId, InputStream inputStream, String fileName, String tagName) {
        try {
            // 1. 文档提取和清洗（适配InputStream）
            String cleanContent = extractAndCleanText(inputStream, fileName);

            // 2. 父子分块（逻辑完全复用）
            List<TextSegment> segments = splitDocument(cleanContent);

            // 3. 提取元数据
            DocumentMetadata metadata = extractMetadata(segments, fileName);

            // 4. 向量化处理
            Map<Embedding, TextSegment> vectorizedSegments = vectorizeSegments(segments);

            // 5. 存储到Milvus向量库
            storeToVectorDatabase(userId, vectorizedSegments, metadata, fileName, tagName);

            return metadata.getAbstractText();
        } catch (Exception e) {
            log.error("文档处理失败: {}", fileName, e);
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取和清洗文档内容（适配InputStream）
     */
    private String extractAndCleanText(InputStream inputStream, String fileName) {
        try {
            // 使用重载后的方法提取文档
            Document document = TextExtractUtil.extractLocalDocument(inputStream, fileName);
            String cleanContent = TextCleanUtil.cleanText(document.text());

            if (cleanContent == null || cleanContent.trim().isEmpty()) {
                throw new IllegalArgumentException("文档内容为空");
            }
            return cleanContent;
        } catch (IOException e) {
            throw new RuntimeException("文档提取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 分割文档为父子文本段（原有逻辑完全复用）
     */
    private List<TextSegment> splitDocument(String cleanContent) {
        Document document = Document.from(cleanContent);
        // 调用父子分块逻辑
        List<TextSegment> segments = childParentTextStructUtil.splitTextParentChild(document);

        if (segments.isEmpty()) {
            throw new RuntimeException("文档分块失败，未生成有效文本段");
        }
        log.debug("文档父子分块完成，共生成{}个子块", segments.size());
        return segments;
    }

    /**
     * 提取文档元数据（原有逻辑复用）
     */
    private DocumentMetadata extractMetadata(List<TextSegment> segments, String filename) {
        String firstSegmentText = segments.getFirst().text();
        String fullText = segments.stream()
                .map(TextSegment::text)
                .collect(Collectors.joining(" "));

        String keywordStr = extractWithRetry(() ->
                        textStructUtil.extractDocumentInfo(firstSegmentText, DOUBAO_MODEL),
                "关键词提取失败");

        String abstractStr = extractWithRetry(() ->
                        textStructUtil.extractAbstract(fullText, DOUBAO_MODEL),
                "摘要提取失败");

        Map<String, Object> keywordMap = parseKeywordMap(keywordStr);
        String tittle = (String) keywordMap.get("title");

        return new DocumentMetadata(
                (String) keywordMap.get("author"),
                tittle== null ? filename : tittle,
                (String) keywordMap.get("date"),
                filename,
                abstractStr
        );
    }

    /**
     * 带重试机制的信息提取（原有逻辑复用）
     */
    private String extractWithRetry(Supplier<String> extractor, String errorMessage) {
        for (int retry = 0; retry < MAX_RETRY_TIMES; retry++) {
            try {
                String result = extractor.get();
                if (result != null && !result.trim().isEmpty()) {
                    return result;
                }
            } catch (Exception e) {
                log.warn("{}，重试次数: {}", errorMessage, retry + 1, e);
                if (retry == MAX_RETRY_TIMES - 1) {
                    log.error("{}，已达到最大重试次数: {}", errorMessage, MAX_RETRY_TIMES);
                }
            }
        }
        return null;
    }

    /**
     * 解析关键词映射（原有逻辑复用）
     */
    private Map<String, Object> parseKeywordMap(String keywordStr) {
        if (keywordStr == null || keywordStr.trim().isEmpty()) {
            return new ConcurrentHashMap<>();
        }
        try {
            return gson.fromJson(keywordStr, MAP_TYPE);
        } catch (Exception e) {
            log.error("JSON解析关键词失败，原始字符串: {}", keywordStr, e);
            return new ConcurrentHashMap<>();
        }
    }

    /**
     * 向量化处理文本段（原有逻辑复用）
     */
    private Map<Embedding, TextSegment> vectorizeSegments(List<TextSegment> segments) {
        Map<Embedding, TextSegment> result = new HashMap<>();

//        List<Supplier<Void>> tasks = createVectorizationTasks(segments, result);

        try {
//            CompletableFuture<Void> allFuture = asyncTaskExecutor.submitAll(tasks);
//            allFuture.join();

            segments.forEach(segment -> {
                try {
                    Embedding embedding = textStructUtil.generateVector(segment.text());
                    result.put(embedding, segment);
                } catch (Exception e) {
                    log.error("处理文本段失败: {}", segment.text().substring(0, Math.min(50, segment.text().length())), e);
                    throw new CompletionException(e);
                }
            });

            if (result.isEmpty()) {
                throw new RuntimeException("所有文本段向量化失败");
            }
            log.info("文本段向量化完成，成功处理{}个文本段", result.size());
            return result;
        } catch (CompletionException e) {
            log.error("文本段向量化失败", e.getCause());
            throw new RuntimeException("文本段向量化失败", e.getCause());
        }
    }

    /**
     * 创建向量化任务列表（原有逻辑复用）
     */
    private List<Supplier<Void>> createVectorizationTasks(
            List<TextSegment> segments,
            Map<Embedding, TextSegment> result
    ) {
        return segments.stream()
                .map(segment -> (Supplier<Void>) () -> {
                    try {
                        Embedding embedding = textStructUtil.generateVector(segment.text());
                        result.put(embedding, segment);
                        return null;
                    } catch (Exception e) {
                        log.error("处理文本段失败: {}", segment.text().substring(0, Math.min(50, segment.text().length())), e);
                        throw new CompletionException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 存储到Milvus向量数据库（原有逻辑复用）
     */
    private void storeToVectorDatabase(
            String userId,
            Integer sessionId,
            Map<Embedding, TextSegment> vectorizedSegment,
            DocumentMetadata metadata,
            String filename
    ) {
        try {
            // 创建Milvus集合（按用户隔离）
            milvusSearchUtils.createMilvusCollection(userId);

            // 准备插入数据（包含父子块信息）
            List<JsonObject> data = prepareInsertData(vectorizedSegment, metadata, filename, sessionId);

            // 执行插入
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(collectionName + "_" + userId)
                    .data(data)
                    .build();

            milvusClient.insert(insertReq);
            log.info("向量库写入完成: {}, 共{}个文本段", filename, data.size());
        } catch (Exception e) {
            log.error("向量库写入失败: {}", filename, e);
            throw new RuntimeException("向量库写入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 存储到Milvus向量数据库（原有逻辑复用）
     */
    private void storeToVectorDatabase(
            String userId,
            Map<Embedding, TextSegment> vectorizedSegment,
            DocumentMetadata metadata,
            String filename,
            String tagName
    ) {
        try {
            // 创建Milvus集合
            milvusSearchUtils1.createMilvusCollection();
//            milvusSearchUtils1.createPartition(tagName);

            // 准备插入数据（包含父子块信息）
            List<JsonObject> data = prepareInsertData(vectorizedSegment, metadata, filename, tagName, Integer.valueOf(userId));

            // 执行插入
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(collectionName)
//                    .partitionName(tagName)
                    .data(data)
                    .build();

            milvusClient.insert(insertReq);
            log.info("向量库写入完成: {}, 共{}个文本段", filename, data.size());
        } catch (Exception e) {
            log.error("向量库写入失败: {}", filename, e);
            throw new RuntimeException("向量库写入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 准备Milvus插入数据（包含父子块元信息）
     */
    private List<JsonObject> prepareInsertData(
            Map<Embedding, TextSegment> vectorizedSegments,
            DocumentMetadata metadata,
            String filename,
            Integer sessionId
    ) {
        List<JsonObject> data = new ArrayList<>();

        for (Map.Entry<Embedding, TextSegment> entry : vectorizedSegments.entrySet()) {
            JsonObject row = new JsonObject();
            TextSegment segment = entry.getValue();

            // 基础文本字段
            row.addProperty("text", segment.text());
            row.add("text_dense", gson.toJsonTree(entry.getKey().vector()));
            row.addProperty("come_from", filename);
            row.addProperty("fileName", filename);
            row.addProperty("session_id", sessionId);

            // 父子块关联信息（核心）
            String parentText = segment.metadata().getString("parent_text");
            String parentId = segment.metadata().getString("parent_id");
            if (parentText != null) {
                row.addProperty("parent_text", parentText);
            }
            if (parentId != null) {
                row.addProperty("parent_id", parentId);
            }

            // 文档元数据
            if (metadata.getAuthor() != null) {
                row.addProperty("author", metadata.getAuthor());
            }
            if (metadata.getTitle() != null) {
                row.addProperty("title", metadata.getTitle());
            }
            if (metadata.getDate() != null) {
                row.addProperty("timestamp", metadata.getDate());
            }
            data.add(row);
        }
        return data;
    }

    /**
     * 准备Milvus插入数据（包含父子块元信息）
     */
    private List<JsonObject> prepareInsertData(
            Map<Embedding, TextSegment> vectorizedSegments,
            DocumentMetadata metadata,
            String filename,
            String tagName,
            Integer userId
    ) {
        List<JsonObject> data = new ArrayList<>();

        for (Map.Entry<Embedding, TextSegment> entry : vectorizedSegments.entrySet()) {
            JsonObject row = new JsonObject();
            TextSegment segment = entry.getValue();

            // 基础文本字段
            row.addProperty("text", segment.text());
            row.add("text_dense", gson.toJsonTree(entry.getKey().vector()));
            row.addProperty("come_from", filename);
            row.addProperty("fileName", filename);
            row.addProperty("user_id", userId);

            // 父子块关联信息（核心）
            String parentText = segment.metadata().getString("parent_text");
            String parentId = segment.metadata().getString("parent_id");
            if (parentText != null) {
                row.addProperty("parent_text", parentText);
            }
            if (parentId != null) {
                row.addProperty("parent_id", parentId);
            }
            if (tagName != null) {
                row.addProperty("tag_name", tagName);
            }

            // 文档元数据
            if (metadata.getAuthor() != null) {
                row.addProperty("author", metadata.getAuthor());
            }
            if (metadata.getTitle() != null) {
                row.addProperty("title", metadata.getTitle());
            }
            if (metadata.getDate() != null) {
                row.addProperty("timestamp", metadata.getDate());
            }
            data.add(row);
        }
        return data;
    }
}