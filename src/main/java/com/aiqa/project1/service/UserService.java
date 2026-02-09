package com.aiqa.project1.service;

import com.aiqa.project1.pojo.Result;
import com.aiqa.project1.pojo.user.RegisterDataUser;
import com.aiqa.project1.pojo.user.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface UserService {

    /**
     * 用户注册
     */
    int register(User user);

    Result batchRegister(MultipartFile userCsv);
    /**
     * 用户登录
     * @return 包含用户信息、Token、过期时间的DTO
     */
    Result login(String username, String password);


    /**
     * 根据用户名查询用户信息
     */
    User getUserInfo(String username);

    /**
     * 修改密码
     */
    void changePassword(String username, String oldPassword, String newPassword);

    /**
     * 删除用户
     */
    Boolean deleteUserByUserId(Integer userId);
}
