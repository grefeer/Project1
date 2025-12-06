package com.aiqa.project1.pojo.user;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
@Data
@EqualsAndHashCode(callSuper = true)
public class LoginDataUser extends UserResponseData {

    private String expireTime;

    private String token;

    public LoginDataUser() {
    }

    public LoginDataUser(Integer userId, String username, String role, String expireTime, String token) {
        super(userId, username, role);
        this.expireTime = expireTime;
        this.token = token;
    }

}
