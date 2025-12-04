package com.aiqa.project1.pojo;

public class LoginData extends ResponseData{

    private String expireTime;

    private String token;

    public LoginData() {
    }

    public LoginData(Integer userId, String username, String role, String expireTime, String token) {
        super(userId, username, role);
        this.expireTime = expireTime;
        this.token = token;
    }

    public String getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(String expireTime) {
        this.expireTime = expireTime;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
