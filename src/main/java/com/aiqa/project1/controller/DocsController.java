package com.aiqa.project1.controller;

import com.aiqa.project1.pojo.AuthInfo;
import com.aiqa.project1.pojo.Result;
import com.aiqa.project1.service.impl.DocsServiceimpl;
import com.aiqa.project1.utils.BusinessException;
import com.aiqa.project1.utils.JwtUtils;
import com.aiqa.project1.utils.TencentCOSUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/document")
public class DocsController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final DocsServiceimpl docsService;
    private final TencentCOSUtil tencentCOSUtil;

    public DocsController(DocsServiceimpl docsService, TencentCOSUtil tencentCOSUtil) {
        this.docsService = docsService;
        this.tencentCOSUtil = tencentCOSUtil;
    }

//    @PostMapping("/update")
//    public Result updateDocs(String name, String username, MultipartFile file) throws IOException {
//        Result resp = new Result();
//        DocumentUploadData data = new DocumentUploadData();
//            try {
//                resp.setCode(200);
//                resp.setMessage("上传成功");
//
//                log.info("接收参数{}, {}, {}", name, username, file);
//                String filename = docsService.uploadSingleDocument(name, username, file);
//
//                LocalDateTime now = LocalDateTime.now();
//                String uploadTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
//
//                String id = name + username + filename;
//
//        } catch (IOException e) {
//        }
//    }

    @PostMapping("/upload")
    public Result upload(@RequestParam("file") MultipartFile file,
                         @RequestParam("sessionId") String sessionId,
                         @RequestParam(required = false) String documentName,
                         @RequestParam(required = false) String description,
                         @RequestHeader("Authorization") String token) {
        String userId = JwtUtils.getUserIdFromToken(token);

        return docsService.uploadSingleDocument(file, description, userId, sessionId);

    }

    @PostMapping("/batch-upload")
    public Result upload(@RequestParam("files") List<MultipartFile> files,
                         @RequestParam("sessionId") String sessionId,
//                         @RequestParam(name = "documentNames", required = false) List<String> documentNames,
                         @RequestHeader("Authorization") String token) {
        String userId = JwtUtils.getUserIdFromToken(token);
        Result resp = null;
        try {
            resp = docsService.uploadMultiDocuments(files, userId, sessionId);

        } catch (BusinessException e) {
            resp = new Result(e.getCode(), e.getMessage(), e.getData());
        }
        System.out.println(resp);
        return resp;
    }

    @GetMapping("/{documentId}")
    public Result getDocumentByDocumentId(@PathVariable String documentId, HttpServletRequest request) {
        AuthInfo authInfo = (AuthInfo)request.getAttribute("authInfo");
        try {
            return docsService.getSingleDocument(documentId, authInfo, 0L);
        } catch (Exception e) {
            return new Result(500, e.getMessage(), e.getCause());
        }
    }

    @GetMapping("/{documentId}/{version}")
    public Result getDocumentByDocumentIdAndVersion(
            @PathVariable("documentId") String documentId,
            @PathVariable("version") Long version,
            HttpServletRequest request) {
        AuthInfo authInfo = (AuthInfo)request.getAttribute("authInfo");
        try {
            return docsService.getSingleDocument(documentId, authInfo, version);
        } catch (Exception e) {
            return new Result(500, e.getMessage(), e.getCause());
        }
    }

    @GetMapping("/list")
    public Result getDocumentList(
            @RequestParam(name = "pageNum", required = false) Integer pageNum,
            @RequestParam(name = "pageSize", required = false) Integer pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            HttpServletRequest request
    ) {
        AuthInfo authInfo = (AuthInfo)request.getAttribute("authInfo");
        return docsService.requestDocumentList(pageNum, pageSize, keyword, authInfo);
    }

    @GetMapping("/download")
    public void downloadDocument(
            @RequestParam("documentId") String documentId,
            @RequestParam(name = "keyword", required = false) Long version,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        docsService.downloadSingleDocument(documentId, version, request, response);
    }

    @DeleteMapping("/delete/{documentId}")
    public Result deleteDocument(@PathVariable String documentId, HttpServletRequest request) {
        return docsService.deleteSingleDocument(documentId, request);
    }
}
