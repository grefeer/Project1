package com.aiqa.project1.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


// Spring Boot 跨域配置示例
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${custom.host}")
    private String host;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
//                .allowedOriginPatterns("*") // 允许的前端域名（生产环境指定具体域名）
                .allowedOrigins("http://%s:5173".formatted(host))
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                // 暴露自定义响应头（让前端能读取Content-Disposition/filename）
                .exposedHeaders("Content-Type", "X-Token", "Cache-Control", "Connection", "Content-Disposition", "filename")
                .allowCredentials(true)
                .maxAge(3600);
    }
}