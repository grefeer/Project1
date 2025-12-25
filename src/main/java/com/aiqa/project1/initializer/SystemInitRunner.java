package com.aiqa.project1.initializer;

import com.aiqa.project1.mapper.UserMapper;
import com.aiqa.project1.pojo.user.User;
import com.aiqa.project1.utils.CacheAsideUtils;
import com.aiqa.project1.utils.JwtUtils;
import com.aiqa.project1.utils.UserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class SystemInitRunner implements CommandLineRunner {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment; // 注入环境变量
    private final CacheAsideUtils cacheAsideUtils;


    @Autowired
    public SystemInitRunner(UserMapper userMapper, PasswordEncoder passwordEncoder, Environment environment, CacheAsideUtils cacheAsideUtils) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.cacheAsideUtils = cacheAsideUtils;
    }

    @Override
    public void run(String... args) throws Exception {
        initAdminUser();
        initJwtUtils();
        initCreateFiles();
    }

    private void initCreateFiles() {
        List<String> usernameList = userMapper.getUsersUserName();
        for (String username : usernameList) {
            UserUtils.createUsersFileIfNotExists("src/main/resources/data", username);
        }
    }

    private void initAdminUser() {
        // 1. 幂等性检查（避免重复创建）
        String name = environment.getProperty("system.init.admin.name");
        String password = environment.getProperty("system.init.admin.password");

        User admin = userMapper.getUserByUserName(name);
        if (admin != null) {
            return;
        }

        String initPwd = passwordEncoder.encode(password);

        User initAdmin = new User();
        initAdmin.setUsername(name);
        initAdmin.setPassword(initPwd);
        initAdmin.setRole("ADMIN");
        initAdmin.setPhone("13800138000");
        initAdmin.setEmail("grefer@gmail.com");
        initAdmin.setCreatedTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        userMapper.insertUser(initAdmin);
        System.out.println("系统初始化：ADMIN 用户创建成功，初始密码：" + initPwd);



    }

    private void initJwtUtils() {
        JwtUtils.setEnvironment(environment);
    }

}
