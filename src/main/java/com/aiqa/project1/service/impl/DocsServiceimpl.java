package com.aiqa.project1.service.impl;

import com.aiqa.project1.controller.UserController;
import com.aiqa.project1.mapper.DocumentMapper;
import com.aiqa.project1.mapper.DocumentVersionMapper;
import com.aiqa.project1.pojo.*;
import com.aiqa.project1.pojo.document.*;
import com.aiqa.project1.service.DocsService;
import com.aiqa.project1.utils.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DocsServiceimpl implements DocsService {
    private final DocumentMapper documentMapper;
    private final TencentCOSUtil tencentCOSUtil;
    private final SnowFlakeUtil snowFlakeUtil;
    private final DocumentVersionMapper versionMapper;
    private final RabbitTemplate rabbitTemplate;

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    @Autowired
    private DataProcessUtils dataProcessUtils;
    @Autowired
    private MilvusSearchUtils milvusSearchUtils;

    public DocsServiceimpl(DocumentMapper documentMapper, TencentCOSUtil tencentCOSUtil, SnowFlakeUtil snowFlakeUtil, DocumentVersionMapper versionMapper, RabbitTemplate rabbitTemplate) {
        this.documentMapper = documentMapper;
        this.tencentCOSUtil = tencentCOSUtil;
        this.snowFlakeUtil = snowFlakeUtil;
        this.versionMapper = versionMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

//    @Override
//    public String uploadSingleDocument(String path, String username, MultipartFile file) throws IOException {
//        String fileName = file.getOriginalFilename();
//        file.transferTo(new File(path + "/" + username + "/" + fileName));
//        return fileName;
//    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result uploadSingleDocument(MultipartFile file, String description, String userId, String sessionId) {
        String abstractStr = null;
        try {
            // 创建collection
            milvusSearchUtils.createMilvusCollection(userId);

            // 删除旧的
            try {
                milvusSearchUtils.deleteDocumentEmbeddingsByName(file.getOriginalFilename(), userId);
            } catch (Exception e) {
                e.printStackTrace();
            }
//            dataProcessUtils.processDocument(userId, Integer.valueOf(sessionId), file);
            DocumentTransferDTO dto = new DocumentTransferDTO();
            dto.setFileName(file.getOriginalFilename());
            dto.setFileBytes(file.getBytes()); // 读取文件字节（注意大文件需考虑分片，此处按现有逻辑）
            dto.setUserId(userId);
            dto.setSessionId(sessionId);

            description = (description == null) ? "abstract" : description;
            Result result = (Result) uploadSingleDocumentUnits(file, description, userId, new DocumentUploadData(), sessionId);
            DocumentUploadData data = (DocumentUploadData) result.getData();
            dto.setDocumentId(data.getDocumentId());
            // TODO 将文本转化为嵌入的任务传输给rabbitmq后，后台异步处理该任务（无阻塞），界面无法显示是否已经完成嵌入操作，所以需要在document的状态（STATUS）在嵌入操作前改为NOT_EMBEDDED（无嵌入）
            //  上传的文件（多个文件）分别进行嵌入操作后，修改文件的状态为（AVAILABLE）
            //  以上内容已完成，需要在前端展示文件状态（NOT_EMBEDDED，AVAILABLE）
            rabbitTemplate.convertAndSend("TextProcess", "text.divide", dto);

            return result;
        }
        catch (BusinessException e) {
            return new Result(e.getCode(), e.getMessage(), e.getData());
        } catch (IOException e) {
            throw new BusinessException(500, e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result uploadMultiDocuments(List<MultipartFile> files, String userId, String sessionId) {
        Result result = new Result();
        Boolean deleteFinishedFlag = false;
        DocumentInfoList data = new DocumentInfoList();
        List<String> documentNames = new ArrayList<>();
        files.forEach(e -> documentNames.add(e.getOriginalFilename()));
        int fileSize = documentNames.size();
        AtomicInteger successCount = new AtomicInteger();

//        List<CompletableFuture<?>> futures = new ArrayList<>();
        String abstractStr = null;

        for (int i = 0; i < fileSize; i++) {
            MultipartFile multipartFile = files.get(i);
            try {
                // 删除旧的
                try {
                    milvusSearchUtils.deleteDocumentEmbeddingsByName(multipartFile.getOriginalFilename(), userId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                DocumentTransferDTO dto = new DocumentTransferDTO();
                dto.setFileName(multipartFile.getOriginalFilename());
                dto.setFileBytes(multipartFile.getBytes()); // 读取文件字节（注意大文件需考虑分片，此处按现有逻辑）
                dto.setUserId(userId);
                dto.setSessionId(sessionId);

                Result result_ = (Result) uploadSingleDocumentUnits(multipartFile, "", userId, new DocumentUploadData(), sessionId);
                DocumentUploadData data_ = (DocumentUploadData) result_.getData();
                dto.setDocumentId(data_.getDocumentId());

                rabbitTemplate.convertAndSend("TextProcess", "text.divide", dto);

                data.getSuccessList().add(result_);
                successCount.getAndIncrement();

            } catch (BusinessException e) {
                log.error(e.getMessage());
                data.getFailList().add(e);
            } catch (IOException e) {
                throw new BusinessException(500, e.getMessage(), null);
            }
        }



        result.setData(data);
        result.setMessage("批量上传成功（成功" + successCount.get() + "个，失败" + (fileSize - successCount.get())+"个）");

        result.setCode(successCount.get() == fileSize ? 200: 400);
        return result;
    }

    @Override
    public Result getSingleDocument(String documentId, AuthInfo authInfo, Long version) {
        Result result = new Result();


        try {
            // 鉴权
            result = authenticationAndExistenceCheck(documentId, authInfo);
            Document document = (Document) result.getData();
            if (document == null)
                return result;

            result.setCode(200);
            result.setMessage("查询成功");

            DocumentSingleListData data = UserUtils.copyDuplicateFieldsFromA2B(document, new DocumentSingleListData());

            QueryWrapper<DocumentVersion> Wrapper = new QueryWrapper<>();
            Wrapper.eq("document_id", documentId);
            List<DocumentVersion> versionDocuments = versionMapper.selectList(Wrapper);
            List<DocumentVersionList> versionList = new ArrayList<>();

            if (versionDocuments == null || versionDocuments.isEmpty()) {
                result.setCode(404);
                result.setMessage("版本库已经被删除了，但是主库还有");
                return result;
            }
            if (version > versionDocuments.size()) {
                result.setCode(404);
                result.setMessage("没有这个版本的文件");
                return result;
            }

            if (version != 0 && version != null) {
                versionDocuments.stream()
                        .filter(e -> Objects.equals(e.getVersion(), version))
                        .forEach(versionDoc  -> versionList.add(UserUtils.copyDuplicateFieldsFromA2B(versionDoc, new DocumentVersionList())));
            } else {
                versionDocuments.forEach(e-> versionList.add(UserUtils.copyDuplicateFieldsFromA2B(e, new DocumentVersionList())));
            }

            data.setPreviewUrl(versionDocuments.getFirst().getPreviewUrl());
            data.setDocumentVersionList(versionList);
            result.setData(data);


        } catch (Exception e) {
            // 出错
            result.setCode(500);
            result.setMessage(e.getMessage());
            result.setData(null);
        }

        return result;

    }


    @Transactional(rollbackFor = Exception.class, propagation = Propagation.NESTED)
    public Object uploadSingleDocumentUnits(MultipartFile file, String description, String userId, Object data, String sessionId) {
        Result res = new Result();

        String documentName = file.getOriginalFilename();
        String fileType = documentName.substring(documentName.lastIndexOf("."));


        try {
            QueryWrapper<Document> wrapper = new QueryWrapper<>();
            wrapper.eq("document_name", documentName).eq("user_id", userId);
            Document document = documentMapper.selectOne(wrapper);

            boolean insertFlag = (document == null);
            String documentId; // 定义文档主表的唯一 ID

            // 用于存储当前文档关联的所有sessionId（去重）
            Set<String> sessionIdSet = new LinkedHashSet<>();

            // 如果是新的
            if (insertFlag) {
                documentId = String.valueOf(snowFlakeUtil.nextId());
                document = new Document();
                document.setDocumentId(documentId);
                document.setDocumentName(documentName);
                document.setFileType(fileType);
                document.setUploadTime(LocalDateTime.now());
                document.setCurrentVersion(1L); // 新文档，版本号为 1
            } else {
                System.out.println("--55--");
                System.out.println(document);
                documentId = document.getDocumentId();

                if (document.getStatus().equals("DELETED")) {
                    QueryWrapper<DocumentVersion> wrapperVersion =  new QueryWrapper<>();
                    wrapperVersion.eq("document_id", documentId);
                    versionMapper.delete(wrapperVersion);
                    document.setCurrentVersion(1L);
                } else {

                    // 未删除文档：历史sessionId + 新增当前sessionId（去重）
                    String currentSessionId = document.getSessionId();
                    if (StringUtils.hasText(currentSessionId)) {
                        String[] oldSessions = currentSessionId.split(",");
                        sessionIdSet.addAll(Arrays.asList(oldSessions));
                    }

                    document.setCurrentVersion(document.getCurrentVersion() + 1); // 版本号加 1
                }
            }

            sessionIdSet.add(sessionId);
            // 核心：将集合转为纯逗号分隔字符串（无重复、无空格）
            String finalSessionIds = String.join(",", sessionIdSet);
            document.setSessionId(finalSessionIds);

            String ossPath = tencentCOSUtil.getOssPath(userId, documentId, documentName, document.getCurrentVersion());
            String previewUrl = tencentCOSUtil.upLoadFile(file, ossPath);
            if (previewUrl == null) {
                throw new BusinessException(500, "COS文件上传失败，事务回滚", null);
            }

            document.setDescription(description);
            document.setUserId(userId);
            document.setFileSize(file.getSize());
            document.setUpdateTime(LocalDateTime.now());
            document.setStatus("NOT_EMBEDDED");

            if (insertFlag) {
                documentMapper.insert(document);
            } else {
                documentMapper.update(document, wrapper);
            }

            DocumentVersion version = new DocumentVersion();
            version.setDocumentId(documentId);
            version.setVersion(document.getCurrentVersion());
            version.setOssPath(ossPath);
            version.setFileSize(file.getSize());
            version.setPreviewUrl(previewUrl);
            version.setUploadTime(LocalDateTime.now());
            versionMapper.insert(version);

            data = UserUtils.copyDuplicateFieldsFromA2B(version, data);
            data = UserUtils.copyDuplicateFieldsFromA2B(document, data);
            res.setData(data);
            res.setCode(200);
            res.setMessage("文档上传成功");
        } catch (Exception e) {
            throw new BusinessException(500, "文档上传失败", e);
        }

        log.info(res.getMessage());
        log.info(res.getData().toString());
        log.info(res.getCode().toString());
        return res;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result deleteSingleDocument(String documentId, HttpServletRequest request) {
        Result result = null;
        try {
            // 从数据库获取地址，并将数据库中对应数据标记为删除
            // 鉴权并检查是否有对应文件
            AuthInfo authInfo = (AuthInfo) request.getAttribute("authInfo");
            result = authenticationAndExistenceCheck(documentId, authInfo);
            Document document = (Document) result.getData();
            if (document == null)
                return result;

            UpdateWrapper<Document> wrapperVersion = new UpdateWrapper<>();
            wrapperVersion.eq("document_id", documentId).eq("user_id", authInfo.getUserId()).set("status", "DELETED").set("session_id", "");
            documentMapper.update(document, wrapperVersion);

            // 向rabbitmq发送删除请求
            rabbitTemplate.convertAndSend(
                    "update",
                    "update.embedding",
                    Map.of(
                            "documentName", document.getDocumentName(),
                            "userId", authInfo.getUserId()
                    ));
//            String key = "docs/" + tencentCOSUtil.getOssPath(
//                    authInfo.getUserId(),
//                    documentId,
//                    document.getDocumentName(),
//                    document.getCurrentVersion()
//            );
//
//            // 删除OSS文件
//            tencentCOSUtil.deleteFile(key);
            result.setCode(200);
            result.setMessage("删除成功");
            result.setData(null);

        } catch (Exception e) {
            if (result == null) {
                result = new Result(); // 初始化result
            }
            e.printStackTrace();
            result.setCode(500);
            result.setMessage(e.getMessage());
            result.setData(null);
        }
        return result;
    }

    @Override
    public Result downloadSingleDocument(String documentId, Long version, HttpServletRequest request, HttpServletResponse response) {
        Result result = null;

        try {
            AuthInfo authInfo = (AuthInfo) request.getAttribute("authInfo");
            System.out.println("--33--");
            System.out.println(authInfo);

            // 鉴权并检查是否有对应文件
            result = authenticationAndExistenceCheck(documentId, authInfo);
            Document document = (Document) result.getData();
            if (document == null)
                return result;

            String key = "docs/" + tencentCOSUtil.getOssPath(
                    authInfo.getUserId(),
                    documentId,
                    document.getDocumentName(),
                    (version == null) ? document.getCurrentVersion() : version
            );
//            response.setHeader("filename", document.getDocumentName());

            tencentCOSUtil.downloadFileByStream(key, document.getDocumentName(), response);
            result.setCode(200);
            result.setMessage("下载成功");
            result.setData(null);
        } catch (Exception e) {
            if (result == null) {
                result = new Result(); // 初始化result
            }
            e.printStackTrace();
            result.setCode(500);
            result.setMessage(e.getMessage());
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result requestDocumentList(Integer pageNum, Integer pageSize, String keyword, AuthInfo authInfo) {
        Result result = new Result();
        DocumentQueryList data = new DocumentQueryList();

        try {
            pageSize = pageSize == null ? 10 : pageSize;
            pageNum = pageNum == null ? 1 : pageNum;
            keyword = keyword == null ? "." : keyword;
            String userId = authInfo.getUserId();


            IPage<Document> page = new Page<>(pageNum, pageSize);

            QueryWrapper<Document> queryWrapper = new QueryWrapper<>();
            if (! authInfo.getRole().equals("ADMIN")) {
                queryWrapper.eq("user_id", userId);
            }
            queryWrapper.like("document_name", keyword);

            Long totalDocumentCount = documentMapper.selectCount(queryWrapper);
            int totalpages = (int)(totalDocumentCount / pageSize + 1);

            data.setTotal(totalDocumentCount);
            data.setPages(totalpages);
            data.setPageSize(pageSize);
            data.setPageNum(pageNum);


            IPage<Document> documentPage = documentMapper.selectPage(page, queryWrapper); // 调用 selectPage 方法
            List<Document> documentList = documentPage.getRecords();

            long total = documentPage.getTotal();
            System.out.println("Total users (age > 25): " + total);
            for (Document document : documentList) {
                System.out.println("User: " + document);
            }
            List<DocumentUploadData> documentUploadDataList = new ArrayList<>();
            documentList.stream()
                    .filter(document -> document.getStatus().equals("AVAILABLE") || document.getStatus().equals("NOT_EMBEDDED") )
                    .forEach(document -> documentUploadDataList.add(UserUtils.copyDuplicateFieldsFromA2B(document, new DocumentUploadData())));
            data.setList(documentUploadDataList);
            result.setMessage("查询成功");
            result.setData(data);
            result.setCode(200);

            System.out.println(data);

        } catch (Exception e) {
            result.setMessage("查询失败");
            result.setData(e);
            result.setCode(500);;
            e.printStackTrace();
        }
        return result;
    }


    private Result authenticationAndExistenceCheck(String documentId, AuthInfo authInfo) {
        Result result = new Result();
        // 鉴权并检查是否有对应文件
        QueryWrapper<Document> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("document_id", documentId);
        Document document = documentMapper.selectOne(queryWrapper);
        if (document == null || document.getStatus().equals("DELETED")){
            // 资源不存在
            result.setCode(404);
            result.setMessage("文件不存在");
            result.setData(null);
            return result;
        }
        if (! document.getUserId().equals(authInfo.getUserId()) && authInfo.getRole().equals("USERS")) {
            // 无权限访问
            result.setCode(403);
            result.setMessage("无权限访问");
            return result;
        }
        result.setData(document);
        return result;
    }


}
