package com.aiqa.project1.service;

import com.aiqa.project1.pojo.AuthInfo;
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
     * 修改电话号码
     */
    void changePhone(String username, String oldPhone, String newPhone);

    /**
     * 修改邮箱
     */
    void changeEmail(String username, String oldEmail, String newEmail);

    /**
     * 删除用户
     */
    Boolean deleteUserByUserId(Integer userId);

    /**
     * 获取系统仪表盘
     * 用户状态：总人数，今日在线人数，部门用户分布
     * 问答核心：今日问答数、累计问答数、平均响应时长
     * 知识库：已上传文档数、有效向量条数、待处理 / 处理失败文档数
     */
    Map<String, String> getDashBoard();
}
