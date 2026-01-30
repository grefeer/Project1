package com.aiqa.project1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// 开启了对Servlet组件的支持
//@ServletComponentScan
//@MapperScan("com.aiqa.project1.mapper")
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class Project1Application {

    public static void main(String[] args) {
        SpringApplication.run(Project1Application.class, args);
    }

}
