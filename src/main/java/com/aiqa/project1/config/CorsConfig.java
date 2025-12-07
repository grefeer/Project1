package com.aiqa.project1.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


// Spring Boot 跨域配置示例
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*") // 允许的前端域名（生产环境指定具体域名）
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                // 暴露自定义响应头（让前端能读取Content-Disposition/filename）
                .exposedHeaders("Content-Disposition", "filename")
                .allowCredentials(true)
                .maxAge(3600);
    }
}