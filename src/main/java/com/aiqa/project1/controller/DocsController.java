package com.aiqa.project1.controller;


import com.aiqa.project1.pojo.Response;
import com.aiqa.project1.service.impl.DocsServiceimpl;
import com.aiqa.project1.utils.TencentCOSUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/docs")
public class DocsController {
    private final DocsServiceimpl docsServiceimpl;
    private final TencentCOSUtil tencentCOSUtil;
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    public DocsController(DocsServiceimpl docsServiceimpl, TencentCOSUtil tencentCOSUtil) {
        this.docsServiceimpl = docsServiceimpl;
        this.tencentCOSUtil = tencentCOSUtil;
    }

    @PostMapping("/update")
    public Response updateSingleDocument(@RequestParam String username, @RequestBody MultipartFile file, HttpServletRequest request) {
        Response resp = new Response();
        try {
            System.out.println("===== 调试信息 =====");
            System.out.println("请求Content-Type: " + request.getContentType()); // 必须包含multipart/form-data
            System.out.println("username: " + username);
            System.out.println("file是否为null: " + (file == null));
            String docsName = docsServiceimpl.updateSingleDocument("C:\\Users\\Grefer\\IdeaProjects\\Project1\\src\\main\\resources\\static", username, file);
            resp.setCode(200);
            resp.setMessage(docsName);
        } catch (IOException e) {
            e.printStackTrace();
            resp.setCode(500);
            resp.setMessage(e.getMessage());
        }

        return resp;
    }

    @PostMapping("/upload")
    public Response upload(@RequestBody MultipartFile file) {
        Response resp = new Response();
        resp.setCode(200);
        resp.setMessage(null);
        log.info("正在上传，文件名{}", file.getOriginalFilename());
        String url = tencentCOSUtil.upLoadFile(file);
        log.info("文件的Url：{}", url);

        return resp;
    }

}
