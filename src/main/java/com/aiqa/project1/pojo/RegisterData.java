package com.aiqa.project1.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


public class RegisterData extends ResponseData{

    private String createdTime;

    public RegisterData(Integer userId, String username, String role, String createTime) {
        super(userId, username, role);
        this.createdTime = createTime;
    }

    public RegisterData() {
    }

    public String getCreateTime() {
        return createdTime;
    }

    public void setCreateTime(String createTime) {
        this.createdTime = createTime;
    }
}
