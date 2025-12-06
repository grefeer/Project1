package com.aiqa.project1.pojo.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {

    private Integer userId;
    private String username;
    private String password;
    private String role;
    private String phone;
    private String email;
    private String createdTime;
    private String token;

}
