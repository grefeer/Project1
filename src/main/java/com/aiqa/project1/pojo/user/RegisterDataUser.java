package com.aiqa.project1.pojo.user;


public class RegisterDataUser extends UserResponseData {

    private String createdTime;

    public RegisterDataUser(Integer userId, String username, String role, String createTime) {
        super(userId, username, role);
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
