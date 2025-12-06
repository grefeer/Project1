package com.aiqa.project1.service.impl;

import com.aiqa.project1.controller.UserController;
import com.aiqa.project1.mapper.UserMapper;
import com.aiqa.project1.pojo.*;
import com.aiqa.project1.pojo.user.LoginDataUser;
import com.aiqa.project1.pojo.user.User;
import com.aiqa.project1.service.UserService;
import com.aiqa.project1.utils.BusinessException;
import com.aiqa.project1.utils.JwtUtils;
import com.aiqa.project1.utils.UserUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class UserServiceimpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);


    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceimpl(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
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

        userMapper.insertUser(user);
        return userMapper.getUserIdByUserName(user.getUsername());
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
}

