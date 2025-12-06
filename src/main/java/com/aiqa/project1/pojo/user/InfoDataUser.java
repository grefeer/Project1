package com.aiqa.project1.pojo.user;

import lombok.*;


@Setter
@Getter
@Data
@EqualsAndHashCode(callSuper = true)
public class InfoDataUser extends UserResponseData {
    private String phone;
    private String email;
    private String createdTime;
    private String status = "NORMAL";

    public InfoDataUser() {}

    public InfoDataUser(Integer userId, String username, String role, String phone, String email, String createdTime, String status) {
        super(userId, username, role);
        this.phone = phone;
        this.email = email;
        this.createdTime = createdTime;
        this.status = status;
    }

}
