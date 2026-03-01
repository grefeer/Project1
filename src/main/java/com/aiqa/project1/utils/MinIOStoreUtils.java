package com.aiqa.project1.utils;

import io.lettuce.core.ScriptOutputType;
import io.minio.*;
import io.minio.errors.*;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Component
public class MinIOStoreUtils {
    private final MinioClient minioClient;

    @Value("${minio.endpoint}")
    private String ENDPOINT;

    public MinIOStoreUtils(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public String uploadAndGetPublicUrl(String bucketName, String savePath, MultipartFile file) throws Exception {
        if (file.isEmpty()) return null;

        String contentType = file.getContentType();
        System.out.println(contentType);
        if (contentType == null) {
            // 如果猜不到类型，设置为通用的二进制流
            contentType = "application/octet-stream";
        }


        // 2. 检查并创建 Bucket
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            // 设置存储桶策略为“公共只读” (重点)
            // 这个 JSON 定义了所有人都可以对该 bucket 下的所有文件执行 s3:GetObject 操作
            String policyJson = "{\n" +
                    "  \"Version\": \"2012-10-17\",\n" +
                    "  \"Statement\": [\n" +
                    "    {\n" +
                    "      \"Action\": [\"s3:GetObject\"],\n" +
                    "      \"Effect\": \"Allow\",\n" +
                    "      \"Principal\": {\"AWS\": [\"*\"]},\n" +
                    "      \"Resource\": [\"arn:aws:s3:::" + bucketName + "/*\"],\n" +
                    "      \"Sid\": \"\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder().bucket(bucketName).config(policyJson).build()
            );
        }

        // 3. 上传文件
        InputStream inputStream = file.getInputStream();

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(savePath)
                        .stream(inputStream, inputStream.available(), -1)
                        .contentType(contentType) // 根据实际类型设置，方便浏览器直接预览
                        .build()
        );

        // 4. 生成长期/永久链接
        // 只要策略是公开的，链接就是：Endpoint + / + Bucket名 + / + 文件名
        return ENDPOINT + "/" + bucketName + "/" + savePath;
    }

    public String getOssPath(String tagId, String documentId, String documentName, Long version) {
//        return URLEncoder.encode(userId + "/" + documentId + "/" + version + "/" + documentName, StandardCharsets.UTF_8);
        return tagId + "/" + documentId + "/" + version + "/" + documentName;
    }

    // 修改方法签名，增加 displayName
    public void download(String bucketName, String ossKey, String displayName, HttpServletResponse response) throws Exception {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!found) {
            throw new FileNotFoundException("bucket not found: " + bucketName);
        }
        // 1. 获取文件流
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(ossKey + displayName).build())) {

            response.setCharacterEncoding("utf-8");

            // 自动探测 Content-Type (如 pdf, png)
            String contentType = URLConnection.guessContentTypeFromName(displayName);
            System.out.println("Content-Type为：" + contentType);
            response.setContentType(contentType != null ? contentType : "application/octet-stream");
//            response.setContentType("application/force-download");

            // 仅对“显示名称”进行编码，不要对整个路径编码
            String encodedFileName = URLEncoder.encode(displayName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName);

//            IOUtils.copy(stream, response.getOutputStream());

            stream.transferTo(response.getOutputStream());
            response.flushBuffer();
        }
    }

//    public void download(String bucketName, String fileName, HttpServletResponse response) throws Exception {
//        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
//        if (!found) {
//            throw new FileNotFoundException("bucket not found: " + bucketName);
//        }
//        // 2. 获取文件流
//        try (GetObjectResponse stream = minioClient.getObject(
//                GetObjectArgs.builder()
//                        .bucket(bucketName)
//                        .object(fileName)
//                        .build())) {
//
//            // 3. 设置响应头
//            response.setCharacterEncoding("utf-8");
//            String contentType = URLConnection.guessContentTypeFromName(fileName);
//            response.setContentType(contentType != null ? contentType : "application/octet-stream");
////            response.setContentType("application/octet-stream");
////            // 注意：Content-Disposition 需要对中文名进行编码，否则下载的文件名会乱码
//            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
//            response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName);
//
////            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
////            response.setContentType("application/octet-stream"); // 二进制流类型
////            response.setHeader("Content-Disposition",
////                    "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);
//
//
//            // 4. 将流拷贝到响应体
//            IOUtils.copy(stream, response.getOutputStream());
//            response.flushBuffer();
//        }
//    }
}
