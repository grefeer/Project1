package com.aiqa.project1.service.impl;

import com.aiqa.project1.controller.UserController;
import com.aiqa.project1.mapper.*;
import com.aiqa.project1.pojo.*;
import com.aiqa.project1.pojo.document.Document;
import com.aiqa.project1.pojo.qa.SessionChat;
import com.aiqa.project1.pojo.qa.UserChatMemory;
import com.aiqa.project1.pojo.tag.DocumentTag;
import com.aiqa.project1.pojo.tag.OrganizationTag;
import com.aiqa.project1.pojo.tag.UserTag;
import com.aiqa.project1.pojo.user.LoginDataUser;
import com.aiqa.project1.pojo.user.User;
import com.aiqa.project1.service.UserService;
import com.aiqa.project1.utils.BusinessException;
import com.aiqa.project1.utils.JwtUtils;
import com.aiqa.project1.utils.UserUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class UserServiceimpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);


    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserTagMapper userTagMapper;
    private final SpecialTagMapper specialTagMapper;
    private final DocumentMapper documentMapper;
    private final DocumentTagMapper documentTagMapper;
    private final SessionChatMapper sessionChatMapper;
    private final UserChatMemoryMapper userChatMemoryMapper;

    @Autowired
    public UserServiceimpl(UserMapper userMapper, PasswordEncoder passwordEncoder, UserTagMapper userTagMapper, SpecialTagMapper specialTagMapper, DocumentMapper documentMapper, DocumentTagMapper documentTagMapper, SessionChatMapper sessionChatMapper, UserChatMemoryMapper userChatMemoryMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.userTagMapper = userTagMapper;
        this.specialTagMapper = specialTagMapper;
        this.documentMapper = documentMapper;
        this.documentTagMapper = documentTagMapper;
        this.sessionChatMapper = sessionChatMapper;
        this.userChatMemoryMapper = userChatMemoryMapper;
    }

    @Override
    @Transactional
    public int register(User user) {

        User existingUser = userMapper.getUserByUserName(user.getUsername());
        if (existingUser != null) {
            throw new BusinessException(
                    ResponseCode.USERNAME_EXIST.getCode(),
                    ResponseCode.USERNAME_EXIST.getMessage(),
                    null
            );
        }

        LocalDateTime now = LocalDateTime.now();
        String createdTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        user.setCreatedTime(createdTime);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        try {
            userMapper.insertUser(user);
        } catch (DuplicateKeyException e) {
            String errMsg = "";
            if (e.getMessage().contains("uk_phone")) {
                errMsg = errMsg + "电话号码重复\n";
            }
            if (e.getMessage().contains("uk_username")) {
                errMsg = errMsg + "用户名称重复\n";
            }
            if (e.getMessage().contains("uk_email")) {
                errMsg = errMsg + "电子邮箱重复\n";
            }
            throw new BusinessException(500, errMsg, null);
        }

        UserTag entity1 = new UserTag();
        UserTag entity2 = new UserTag();
        Integer userId = userMapper.getUserIdByUserName(user.getUsername());

        // 2是个人标签ID，3是公共标签ID
        entity1.setTagId(2L);
        entity1.setUserId(userId);
        entity2.setTagId(3L);
        entity2.setUserId(userId);
        userTagMapper.insert(List.of(entity1, entity2));

        return userId;
    }

    @Override
    public Result login(String username, String password) {
        Result resp = new Result();

        LocalDateTime now = LocalDateTime.now();
        String ExpireTime = now.plusHours(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        User user1 = userMapper.getUserByUserName(username);

        System.out.println(user1.getUserId());
        if (user1 == null || !passwordEncoder.matches(password, user1.getPassword())) {
            throw new BusinessException(
                    ResponseCode.PASSWORD_ERROR.getCode(),
                    ResponseCode.PASSWORD_ERROR.getMessage(),
                    null
            );
        }

        String token = JwtUtils.GenerateJwt(UserUtils.User2Map(user1), user1.getUserId().toString());
        user1.setToken(token);
        LoginDataUser data = UserUtils.copyDuplicateFieldsFromA2B(user1, new LoginDataUser());
        data.setExpireTime(ExpireTime);

        resp.setCode(ResponseCode.LOGIN_SUCCESS.getCode());
        resp.setMessage(ResponseCode.LOGIN_SUCCESS.getMessage());
        resp.setData(data);

        return resp;
    }

    @Override
    public User getUserInfo(String username) {

        User user = userMapper.getUserByUserName(username);
        user.setUserId(userMapper.getUserIdByUserName(username));
        if (user == null) {
            throw new BusinessException(
                    ResponseCode.RESOURCE_ERROR.getCode(),
                    ResponseCode.RESOURCE_ERROR.getMessage(),
                    null
            );
        }
        return user;
    }

    @Override
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = userMapper.getUserByUserName(username);

        String password = passwordEncoder.encode(newPassword);

        if (! passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(
                    ResponseCode.PASSWORD_ERROR.getCode(),
                    ResponseCode.PASSWORD_ERROR.getMessage(),
                    null
            );
        }
        userMapper.updateUserPasswordByUserName(username, password);
    }

    @Override
    @Transactional
    public Boolean deleteUserByUserId(Integer userId) {
        try {
            // 删除该用户的所有相关信息
            userTagMapper.delete(new QueryWrapper<UserTag>().eq("user_id", userId));
            userMapper.deleteUserById(userId);
            documentMapper.delete(new QueryWrapper<Document>().eq("user_id", userId));
            sessionChatMapper.delete(new QueryWrapper<SessionChat>().eq("user_id", userId));
            userChatMemoryMapper.delete(new QueryWrapper<UserChatMemory>().eq("user_id", userId));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}

