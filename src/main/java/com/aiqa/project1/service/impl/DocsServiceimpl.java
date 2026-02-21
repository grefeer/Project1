package com.aiqa.project1.service.impl;

import com.aiqa.project1.controller.UserController;
import com.aiqa.project1.mapper.DocumentMapper;
import com.aiqa.project1.mapper.DocumentTagMapper;
import com.aiqa.project1.mapper.DocumentVersionMapper;
import com.aiqa.project1.mapper.SpecialTagMapper;
import com.aiqa.project1.pojo.*;
import com.aiqa.project1.pojo.document.*;
import com.aiqa.project1.pojo.tag.DocumentTag;
import com.aiqa.project1.pojo.tag.OrganizationTag;
import com.aiqa.project1.service.DocsService;
import com.aiqa.project1.utils.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class DocsServiceimpl implements DocsService {
    private final DocumentMapper documentMapper;
    private final SnowFlakeUtil snowFlakeUtil;
    private final DocumentVersionMapper versionMapper;
    private final RabbitTemplate rabbitTemplate;

    @Autowired
    private MilvusSearchUtils1 milvusSearchUtils1;
    @Autowired
    private MinIOStoreUtils minIOStoreUtils;
    @Autowired
    private MinioClient minioClient;
    @Autowired
    private DocumentTagMapper documentTagMapper;
    @Autowired
    private SpecialTagMapper specialTagMapper;
    @Autowired
    private SpecialTagService specialTagService;

    public DocsServiceimpl(DocumentMapper documentMapper, TencentCOSUtil tencentCOSUtil, SnowFlakeUtil snowFlakeUtil, DocumentVersionMapper versionMapper, RabbitTemplate rabbitTemplate) {
        this.documentMapper = documentMapper;
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
    public Result uploadSingleDocument(MultipartFile file, String description, Integer tagId, String userId, String sessionId) {
        String abstractStr = null;
        try {
            // 创建collection
            milvusSearchUtils1.createMilvusCollection();

            // 删除旧的
            try {
                milvusSearchUtils1.deleteDocumentEmbeddingsByName(file.getOriginalFilename());
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
            Result result = (Result) uploadSingleDocumentUnits(file, description, tagId, userId, sessionId);
            DocumentUploadData data = (DocumentUploadData) result.getData();
            dto.setDocumentId(data.getDocumentId());
            dto.setTagName(((DocumentUploadData) result.getData()).getTagType());
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
    public Result uploadMultiDocuments(List<MultipartFile> files, String userId, String sessionId, Integer tagId) {
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
                    milvusSearchUtils1.deleteDocumentEmbeddingsByName(multipartFile.getOriginalFilename());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                DocumentTransferDTO dto = new DocumentTransferDTO();
                dto.setFileName(multipartFile.getOriginalFilename());
                dto.setFileBytes(multipartFile.getBytes()); // 读取文件字节（注意大文件需考虑分片，此处按现有逻辑）
                dto.setUserId(userId);
                dto.setSessionId(sessionId);

                Result result_ = (Result) uploadSingleDocumentUnits(multipartFile, "", tagId, userId, sessionId);
                DocumentUploadData data_ = (DocumentUploadData) result_.getData();
                dto.setDocumentId(data_.getDocumentId());
                dto.setTagName(((DocumentUploadData) result.getData()).getTagType());

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
    public Object uploadSingleDocumentUnits(MultipartFile file, String description, Integer tagId, String userId, String sessionId) {
        Result res = new Result();
        String ossPath = null ;
        String documentName;
        String fileType;
        DocumentUploadData data = new DocumentUploadData();

        try {
            documentName = file.getOriginalFilename();
            fileType = documentName.substring(documentName.lastIndexOf("."));
            QueryWrapper<OrganizationTag> tagQueryWrapper = new QueryWrapper<>();
            tagQueryWrapper.eq("tag_id", tagId);
            OrganizationTag organizationTag = specialTagMapper.selectOne(tagQueryWrapper);

            System.out.println(organizationTag);

            if (organizationTag == null) {
                return Result.error("标签ID错误", null);
            }

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
            // 将其保存到本地
            ossPath = minIOStoreUtils.getOssPath(userId, documentId, documentName, document.getCurrentVersion());
            String previewUrl = minIOStoreUtils.uploadAndGetPublicUrl("data", ossPath, file);

            if (previewUrl == null) {
                throw new BusinessException(500, "COS文件上传失败，事务回滚", null);
            }

            document.setDescription(description);
            document.setUserId(userId);
            document.setFileSize(file.getSize());
            document.setUpdateTime(LocalDateTime.now());
            document.setStatus("NOT_EMBEDDED");

            // document和tag绑定
            document.setTagType(organizationTag.getTagName());
            DocumentTag entity = new DocumentTag();
            entity.setDocumentId(documentId);
            entity.setTagId(tagId);

            System.out.println(document);

            if (insertFlag) {
                documentMapper.insert(document);
            } else {
                documentMapper.update(document, wrapper);
            }

            documentTagMapper.insert(entity);

            DocumentVersion version = new DocumentVersion();
            version.setDocumentId(documentId);
            version.setVersion(document.getCurrentVersion());
            version.setOssPath(ossPath);
            version.setFileSize(file.getSize());
            version.setPreviewUrl(previewUrl);
            version.setUploadTime(LocalDateTime.now());
            versionMapper.insert(version);

            UserUtils.copyDuplicateFieldsFromA2B(version, data);
            UserUtils.copyDuplicateFieldsFromA2B(document, data);

            System.out.println("1111\n" + data.getTagType());

            res.setData(data);
            res.setCode(200);
            res.setMessage("文档上传成功");
        } catch (Exception e) {
            // 捕获异常后，手动删除已上传的MinIO文件
            try {
                if (ossPath != null) {
                    minioClient.removeObject(
                            RemoveObjectArgs.builder()
                                    .bucket("data")
                                    .object(ossPath)
                                    .build());
                }
            } catch (Exception ex) {
                // 记录文件删除失败的日志，便于后续人工处理
                System.err.println("回滚MinIO文件失败：" + ex.getMessage());
            }
            // 重新抛出异常，触发数据库事务回滚
            throw new RuntimeException("操作失败，已回滚文件和数据库操作", e);
        }

        log.info(res.getMessage());
        log.info(res.getData().toString());
        log.info(res.getCode().toString());
        return res;
    }

    private boolean authIfUserHaveTag(Integer tagId, String userId) {
        List<OrganizationTag> data1 = specialTagService.getAllTagsByUserId_(Integer.valueOf(userId));
        for (OrganizationTag organizationTag : data1) {
            if(Objects.equals(tagId, organizationTag.getTagId())) {
               return true;
            }
        }
        return false;
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
            documentTagMapper.delete(new QueryWrapper<DocumentTag>().eq("document_id", documentId));

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
            // 从本地下载
            String key = minIOStoreUtils.getOssPath(
                    authInfo.getUserId(),
                    documentId,
                    "",
                    (version == null) ? document.getCurrentVersion() : version
            );
            response.setHeader("filename", document.getDocumentName());
            minIOStoreUtils.download("data", key, document.getDocumentName(), response);
//            tencentCOSUtil.downloadFileByStream(key, document.getDocumentName(), response);

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
                // 获取user的所有tag
                List<OrganizationTag> tagList = specialTagService.getAllTagsByUserId_(Integer.valueOf(userId));
                List<String> tagNameList = new ArrayList<>(tagList.stream().map(OrganizationTag::getTagName).toList());
                tagNameList.remove("PERSONAL");
                // 过滤出符合标签的文档
                queryWrapper
                        .eq("user_id", userId)
                        .or()
                        .in("tag_type", tagNameList);
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
            System.out.println("Total documents: " + total);
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
