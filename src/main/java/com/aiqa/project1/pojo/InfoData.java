package com.aiqa.project1.pojo;

public class InfoData extends ResponseData{
    private String phone;
    private String email;
    private String createdTime;
    private String status = "NORMAL";

    public InfoData() {}

    public InfoData(Integer userId, String username, String role, String phone, String email, String createdTime, String status) {
        super(userId, username, role);
        this.phone = phone;
        this.email = email;
        this.createdTime = createdTime;
        this.status = status;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
