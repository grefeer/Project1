package com.aiqa.project1.pojo.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    @TableField("user_id")
    private Integer userId;
    @TableField("username")
    private String username;
    @TableField("password")
    private String password;
    @TableField("role")
    private String role;
    @TableField("phone")
    private String phone;
    @TableField("email")
    private String email;
    @TableField("created_time")
    private String createdTime;
    @TableField("token")
    private String token;
}
