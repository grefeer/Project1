package com.aiqa.project1.utils;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.CosHttpRequest;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.internal.CosServiceRequest;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.retry.RetryPolicy;
import com.qcloud.cos.utils.IOUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class TencentCOSUtil {
    // cos的SecretId
    @Value("${qclode.cos.SecretId}")
    private String secretId;
    // cos的SecretKey
    @Value("${qclode.cos.SecretKey}")
    private String secretKey;
    //文件上传后访问路径的根路径，后面要最佳文件名字与类型
    @Value("${qclode.cos.rootSrc}")
    private String rootSrc;
    //上传的存储桶的地域，可参考根路径https://qq-test-1303******.cos.地域.myqcloud
    @Value("${qclode.cos.bucketAddr}")
    private String bucketAddr;
    //存储桶的名字
    @Value("${qclode.cos.bucketName}")
    private String bucketName;
    private COSClient cosClient;

    @PostConstruct
    public void init() {
        if (cosClient == null) {
            cosClient = getCosClient();
        }
        if (secretId.isEmpty() || secretKey.isEmpty() || rootSrc.isEmpty()) {
            throw new IllegalArgumentException("COS SecretId/SecretKey/rootSrc 未配置");
        }
        if (bucketName.isEmpty() || bucketAddr.isEmpty()) {
            throw new IllegalArgumentException("COS 存储桶配置未完成");
        }
    }

    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
        }
    }


    /**
     * 1.调用静态方法getCosClient()就会获得COSClient实例
     * 2.本方法根据永久密钥初始化 COSClient的，官方是不推荐，官方推荐使用临时密钥，是可以限制密钥使用权限，创建cred时有些区别
     *
     * @return COSClient实例
     */
    private COSClient getCosClient() {
        // 1 初始化用户身份信息（secretId, secretKey）。
        System.out.println(secretId + "---" + secretKey);
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        // 2.1 设置存储桶的地域（上文获得）
        Region region = new Region(bucketAddr);
        ClientConfig clientConfig = new ClientConfig(region);
        RetryPolicy myRetryPolicy = new OnlyIOExceptionRetryPolicy();

        // 设置自定义的重试策略
        clientConfig.setRetryPolicy(myRetryPolicy);

        // 2.2 使用https协议传输
        clientConfig.setHttpProtocol(HttpProtocol.https);
        // 3 生成 cos 客户端。
        // 返回COS客户端
        return new COSClient(cred, clientConfig);
    }

    /**
     * 创建 Bucket
     * @param bucket
     * @return
     */
    public Bucket createBucket(String bucket) {
        try{
            // bucket = "examplebucket-1250000000"; //存储桶名称，格式：BucketName-APPID
            CreateBucketRequest createBucketRequest = new CreateBucketRequest(bucket);
            // 设置 bucket 的权限为 Private(私有读写)、其他可选有 PublicRead（公有读私有写）、PublicReadWrite（公有读写）
            createBucketRequest.setCannedAcl(CannedAccessControlList.Private);
            return cosClient.createBucket(createBucketRequest);
        } catch (CosServiceException serverException) {
            serverException.printStackTrace();
            throw serverException;
        } catch (CosClientException clientException) {
            clientException.printStackTrace();
            throw clientException;
        }
    }
    public String getBucketName(Bucket bucket) {
        return bucket.getName();
    }




    public String getBucketAddr(Bucket bucket) {
        return bucket.getLocation();
    }

    /**
     * 获取下载输入流来下载文件
     * @param key
     * @return
     */
    public void downloadFileByStream(String key, String documentName, HttpServletResponse response) throws Exception {
        // Bucket 的命名格式为 BucketName-APPID ，此处填写的存储桶名称必须为此格式
        // 指定文件在 COS 上的路径，即对象键。例如对象键为 folder/picture.jpg，则表示下载的文件 picture.jpg 在 folder 路径下
        InputStream  cosObjectInput = null;
        OutputStream outputStream = null;
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, key);

        try {
            COSObject cosObject = cosClient.getObject(getObjectRequest);
            cosObjectInput = cosObject.getObjectContent();

            // 处理下载到的流
            String encodedDocumentName = URLEncoder.encode(documentName, StandardCharsets.UTF_8);

            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
            response.setContentType("application/octet-stream"); // 二进制流类型
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedDocumentName + "\"; filename*=UTF-8''" + encodedDocumentName);

            response.setHeader("Content-Length", String.valueOf(cosObject.getObjectMetadata().getContentLength()));

            // 5. 写入文件流到响应体
            outputStream = response.getOutputStream();
            IOUtils.copy(cosObjectInput, outputStream); // 高效拷贝流（替代手动读字节数组）
            outputStream.flush();
            // 6. 强制释放所有资源（关键！防止泄漏）

        } catch (CosServiceException e) {
            // COS 服务端异常（如文件不存在、权限不足）
            e.printStackTrace();
            try {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("文件下载失败：" + e.getMessage());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (CosClientException e) {
            // COS 客户端异常（如网络问题）
            e.printStackTrace();
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("客户端连接失败：" + e.getMessage());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (Exception e) {
            // 其他通用异常
            e.printStackTrace();
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("下载失败：" + e.getMessage());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } finally {
            if (cosObjectInput != null) cosObjectInput.close(); // 关闭 COS 输入流
            if (outputStream != null) outputStream.close();   // 关闭响应输出流
        }
    }

    /**
     * 下载文件到本地的路径，例如 把文件下载到本地的 /path/to/路径下的localFile文件中
     * @param key
     * @param outputFilePath
     * @return
     */
    public ObjectMetadata downloadFile2Local(String key, String outputFilePath) {
        File downFile = new File(outputFilePath);
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, key);
        return cosClient.getObject(getObjectRequest, downFile);
    }

    /**
     * 删除文件
     * @param key
     */
    public void deleteFile(String key) {
        cosClient.deleteObject(bucketName, key);
    }

    /**
     * 只要调用静态方法upLoadFile(MultipartFile multipartFile)就可以获取上传后文件的全路径
     *
     * @param file
     * @return 返回文件的浏览全路径
     */
    public String upLoadFile(MultipartFile file, String ossPath) {
        try {
            // 获取上传的文件的输入流
            InputStream inputStream = file.getInputStream();

            // 避免文件覆盖，获取文件的原始名称，如123.jpg,然后通过截取获得文件的后缀，也就是文件的类型
            String originalFilename = file.getOriginalFilename();
            //获取文件的类型
            String fileType = originalFilename;
            if (originalFilename != null && originalFilename.contains(".")) {
                fileType = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // 指定文件上传到 COS 上的路径，即对象键。最终文件会传到存储桶名字中的images文件夹下的fileName名字
            String key = "docs/" + ossPath;
            // 创建上传Object的Metadata
            ObjectMetadata objectMetadata = new ObjectMetadata();
            // - 使用输入流存储，需要设置请求长度
            objectMetadata.setContentLength(inputStream.available());
            // - 设置缓存
            objectMetadata.setCacheControl("no-cache");
            // - 设置Content-Type
            objectMetadata.setContentType(fileType);
            // 存入原始名称
            objectMetadata.addUserMetadata("original-filename", originalFilename);
            //上传文件
            System.out.println("--44--");
            System.out.println(key);
            PutObjectResult putResult = cosClient.putObject(bucketName, key, inputStream, objectMetadata);
            System.out.println(putResult.getRequestId());

            // 创建文件的网络访问路径
            return rootSrc + key;

        } catch (Exception e) {
            e.printStackTrace();
            // 发生IO异常、COS连接异常等，返回空
            return null;
        }
    }


    public String getOssPath(String userId, String documentId, String documentName, Long version) {
        return URLEncoder.encode(userId + "/" + documentId + "/" + version + "/" + documentName, StandardCharsets.UTF_8);
    }

    // 自定义重试策略
    public class OnlyIOExceptionRetryPolicy extends RetryPolicy {
        @Override
        public <X extends CosServiceRequest> boolean shouldRetry(CosHttpRequest<X> cosHttpRequest,
                                                                 org.apache.http.HttpResponse httpResponse,
                                                                 Exception e,
                                                                 int i) {
            // 如果是客户端的 IOException 异常则重试，否则不重试
            if (e.getCause() instanceof IOException) {
                return true;
            }
            return false;
        }
    }


}

