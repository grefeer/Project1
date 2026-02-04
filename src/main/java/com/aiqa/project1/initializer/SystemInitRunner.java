package com.aiqa.project1.initializer;

import com.aiqa.project1.mapper.SpecialTagMapper;
import com.aiqa.project1.mapper.UserMapper;
import com.aiqa.project1.mapper.UserTagMapper;
import com.aiqa.project1.pojo.tag.OrganizationTag;
import com.aiqa.project1.pojo.tag.UserTag;
import com.aiqa.project1.pojo.user.User;
import com.aiqa.project1.utils.CacheAsideUtils;
import com.aiqa.project1.utils.JwtUtils;
import com.aiqa.project1.utils.MilvusSearchUtils1;
import com.aiqa.project1.utils.UserUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
    private final SpecialTagMapper specialTagMapper;
    private final MilvusSearchUtils1 milvusSearchUtils1;
    private final UserTagMapper userTagMapper;


    @Autowired
    public SystemInitRunner(UserMapper userMapper, PasswordEncoder passwordEncoder, Environment environment, CacheAsideUtils cacheAsideUtils, SpecialTagMapper specialTagMapper, MilvusSearchUtils1 milvusSearchUtils1, UserTagMapper userTagMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.cacheAsideUtils = cacheAsideUtils;
        this.specialTagMapper = specialTagMapper;
        this.milvusSearchUtils1 = milvusSearchUtils1;
        this.userTagMapper = userTagMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        initTag();
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
        Integer userId = userMapper.getUserIdByUserName(name);

        UserTag userTag = new UserTag();
        UserTag userTag1 = new UserTag();
        UserTag userTag2 = new UserTag();

        userTag.setTagId(1L);
        userTag.setUserId(userId);
        userTag1.setTagId(2L);
        userTag1.setUserId(userId);
        userTag2.setTagId(3L);
        userTag2.setUserId(userId);
        userTagMapper.insert(List.of(userTag, userTag1, userTag2));
        System.out.println("系统初始化：ADMIN 用户创建成功，初始密码：" + initPwd);



    }

    private void initJwtUtils() {
        JwtUtils.setEnvironment(environment);
    }

    private void initTag() {
        // 所以id=1是管理员标签，2是个人标签，3是公开标签
        createTag("ADMIN", "管理员标签", "", 0);
        createTag("PERSONAL", "私人标签", "", 0);
        createTag("PUBLIC", "公开标签", "", 0);
    }

    private void createTag(String tagName, String description, String parentTag, Integer tagCreatedBy) {
        QueryWrapper<OrganizationTag> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("tag_name", tagName);
        OrganizationTag organizationTag = specialTagMapper.selectOne(queryWrapper);

        if (organizationTag == null) {
            OrganizationTag entity = new OrganizationTag();
            entity.setCreatedAt(LocalDateTime.now());
            entity.setParentTag(parentTag);
            entity.setDescription(description);
            entity.setCreatedBy(tagCreatedBy);
            entity.setTagName(tagName);
            specialTagMapper.insert(entity);
//            milvusSearchUtils1.createPartition(tagName);

        }
    }
}
