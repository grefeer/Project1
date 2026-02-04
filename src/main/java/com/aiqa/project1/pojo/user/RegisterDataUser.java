package com.aiqa.project1.pojo.user;


import java.util.List;

public class RegisterDataUser extends UserResponseData {

    private String createdTime;

    public RegisterDataUser(Integer userId, String username, String role, String createTime, List<String> tags) {
        super(userId, username, role, tags);
        this.createdTime = createTime;
    }

    public RegisterDataUser() {
    }

    public String getCreateTime() {
        return createdTime;
    }

    public void setCreateTime(String createTime) {
        this.createdTime = createTime;
    }
}
