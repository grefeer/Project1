//package com.aiqa.project1.utils;
//
//
//import io.minio.*;
//import org.junit.jupiter.api.Test;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.net.URLConnection;
//
//
//public class MinIOStoreUtilsTest {
//    private final static String ENDPOINT = "http://localhost:9000";
//
//    @Test
//    public void testMinIOStoreUtils() throws Exception {
//
//        String bucketName = "test";
//        String filePath = "D:\\Chisco\\3637874.pdf";
//        File file = new File(filePath);
//        String fileName = file.getName();
//        if (!file.exists() || fileName.isEmpty()) return;
//        String contentType = URLConnection.guessContentTypeFromName(fileName);
//        System.out.println(contentType);
//        if (contentType == null) {
//            // 如果猜不到类型，设置为通用的二进制流，或者根据后缀手动判断
//            contentType = "application/octet-stream";
//        }
//
//        // 1. 初始化客户端
//        MinioClient minioClient = MinioClient.builder()
//                .endpoint(ENDPOINT)
//                .credentials("minio12306admin", "minio9527admin")
//                .build();
//
//        // 2. 检查并创建 Bucket
//        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
//        if (!found) {
//            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
//            // 设置存储桶策略为“公共只读” (重点)
//            // 这个 JSON 定义了所有人都可以对该 bucket 下的所有文件执行 s3:GetObject 操作
//            String policyJson = "{\n" +
//                    "  \"Version\": \"2012-10-17\",\n" +
//                    "  \"Statement\": [\n" +
//                    "    {\n" +
//                    "      \"Action\": [\"s3:GetObject\"],\n" +
//                    "      \"Effect\": \"Allow\",\n" +
//                    "      \"Principal\": {\"AWS\": [\"*\"]},\n" +
//                    "      \"Resource\": [\"arn:aws:s3:::" + bucketName + "/*\"],\n" +
//                    "      \"Sid\": \"\"\n" +
//                    "    }\n" +
//                    "  ]\n" +
//                    "}";
//            minioClient.setBucketPolicy(
//                    SetBucketPolicyArgs.builder().bucket(bucketName).config(policyJson).build()
//            );
//        }
//
//        // 3. 上传文件
//        FileInputStream inputStream = new FileInputStream(file);
//        minioClient.putObject(
//                PutObjectArgs.builder()
//                        .bucket(bucketName)
//                        .object(fileName)
//                        .stream(inputStream, inputStream.available(), -1) // 示例：10MB part size
//                        .contentType(contentType) // 根据实际类型设置，方便浏览器直接预览
//                        .build()
//        );
//
//        // 4. 生成长期/永久链接
//        // 只要策略是公开的，链接就是：Endpoint + / + Bucket名 + / + 文件名
//        String url =  ENDPOINT + "/" + bucketName + "/" + fileName;
//        System.out.println(url);
//    }
//}
