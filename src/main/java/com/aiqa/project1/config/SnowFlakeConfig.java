package com.aiqa.project1.config;

import com.aiqa.project1.utils.SnowFlakeUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SnowFlakeConfig {
    @Value("${snowflake.datacenterId:1}")
    private long workerId;

    @Value("${snowflake.machineId:1}")
    private long machineId;

    @Bean
    public SnowFlakeUtil snowFlakeUtil() {
        return new SnowFlakeUtil(workerId, machineId);
    }
}
