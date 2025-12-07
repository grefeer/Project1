package com.aiqa.project1.utils;

import com.aiqa.project1.controller.UserController;
import com.aiqa.project1.pojo.document.DocumentResponseData;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 数据处理主流程
 */
@Component
public class DataProcessUtils {
    private final TextStructUtil textStructUtil;
    private final MilvusSearchUtils milvusSearchUtils;
    private final MilvusClientV2 milvusClient;
    private final ExecutorService uploadExecutor;

    private static final int MAX_RETRY_TIMES = 3; // 重试次数常量，便于统一修改
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType(); // 类型令牌，保证类型安全


    private static final Logger log = LoggerFactory.getLogger(DataProcessUtils.class);
    @Value("${milvus.collection-name}")
    private String collectionName;

    @PreDestroy
    public void destroy() {
        uploadExecutor.shutdown();
        try {
            if (!uploadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                uploadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            uploadExecutor.shutdownNow();
        }
    }

    public DataProcessUtils(TextStructUtil textStructUtil, MilvusSearchUtils milvusSearchUtils, MilvusClientV2 milvusClient, ExecutorService uploadExecutor) {
        this.textStructUtil = textStructUtil;
        this.milvusSearchUtils = milvusSearchUtils;
        this.milvusClient = milvusClient;
        this.uploadExecutor = uploadExecutor;
    }

    public void DataProcess(Integer userId, File rawFile) {

        // 文本提取
        Document extractContent = null;
        Gson gson = new Gson();

        try {
            extractContent = TextExtractUtil.extractLocalDocument(rawFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
//        System.out.println(extractContent.text());
//        System.out.println(extractContent.metadata());
//        System.out.println(extractContent.toTextSegment());

        // 文本清洗
        String cleanContent = TextCleanUtil.cleanText(extractContent.text());


        // 文本分块+向量化
        Document document = Document.from(cleanContent);

        List<TextSegment> segments = textStructUtil.splitText(document);
        Map<Embedding, TextSegment> map = new ConcurrentHashMap<>();
        String firstSegmentText = segments.getFirst().text();
        if (segments.isEmpty() || firstSegmentText.isEmpty())
            throw new RuntimeException("空文件");
        System.out.println(segments.getFirst().text());


        String keywordStr = null;
        Map<String, Object> keywordMap = null;
        String author = null;
        String title = null;
        String date = null;

        for (int retry = 0; retry < MAX_RETRY_TIMES; retry++) {
            try {
                keywordStr = textStructUtil.extractKeywords(firstSegmentText, "douBao");
                break; // 成功获取则退出循环
            } catch (Exception e) {
                e.printStackTrace();
                // 最后一次重试失败则不再重试
                if (retry == MAX_RETRY_TIMES - 1) {
                    System.err.println("关键词提取失败，已达到最大重试次数：" + MAX_RETRY_TIMES);
                }
            }
        }

        if (keywordStr != null) {
            try {
                keywordMap = gson.fromJson(keywordStr, MAP_TYPE);
                author = (String) keywordMap.get("author");
                title = (String) keywordMap.get("title");
                date = (String) keywordMap.get("date");

            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("JSON解析关键词失败，原始字符串：" + keywordStr);
            }
        }

        System.out.println(keywordMap);

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (TextSegment segment : segments) {
            CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
                try {

                    // TODO 是否需要将英文翻译为中文再生成向量？
                    Embedding embedding = textStructUtil.generateVector(segment.text());

                    map.put(embedding, segment);
                } catch (Exception e) {
                    log.error("处理文本分块失败", e);
                    throw new CompletionException(e); // 抛出异常，让主线程感知
                }
            }, uploadExecutor);
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException e) {
            log.error("部分分块处理失败，终止任务", e.getCause());
            // 可选：清理已处理的数据，避免脏数据
            map.clear();
            throw new RuntimeException("文本分块并发处理失败", e.getCause());
        }

        if (!map.isEmpty()) {

            log.info("开始批量写入向量库，待写入分块数：{}", map.size());
            List<JsonObject> data = new ArrayList<>();


            for (Map.Entry<Embedding, TextSegment> entry : map.entrySet()) {
                Embedding embedding = entry.getKey();
                TextSegment textSegment = entry.getValue();
                JsonObject row = new JsonObject();
                row.addProperty("text", textSegment.text());
                row.add("text_dense", gson.toJsonTree(embedding.vector()));
                row.addProperty("come_from", rawFile.getName());

                // 补充文件元数据
                if (keywordMap != null) {
                    row.addProperty("author", author);
                    row.addProperty("title", title);
                    row.addProperty("timestamp", date);
                }
                row.addProperty("fileName", rawFile.getName());
                data.add(row);

            }
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(collectionName + "_" + userId.toString())
                    .data(data)
                    .build();

            milvusSearchUtils.createMilvusCollection(userId);
            milvusClient.insert(insertReq);
            log.info("向量库写入完成：{}", rawFile.getName());
        }
    }
}