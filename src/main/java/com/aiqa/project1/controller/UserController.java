package com.aiqa.project1.controller;

import com.aiqa.project1.pojo.*;
import com.aiqa.project1.pojo.user.InfoDataUser;
import com.aiqa.project1.pojo.user.RegisterDataUser;
import com.aiqa.project1.pojo.user.User;
import com.aiqa.project1.service.UserService;
import com.aiqa.project1.utils.BusinessException;
import com.aiqa.project1.utils.UserUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final UserService userService; // 依赖Service，而非DAO

    @Autowired
    public UserController(UserService userService) { // 注入Service
        this.userService = userService;
    }


    @PostMapping("/register")
    public Result Register(@RequestBody User user) {
        Result resp = new Result();

        try {
            int userId = userService.register(user);
            user.setUserId(userId);

            log.info("--------------用户注册--------------");
            log.info("状态码为：" + ResponseCode.REGISTER_SUCCESS.getCode());
            log.info("创建时间为：" + user.getCreatedTime());
            log.info("账户名称为：" + user.getUsername());
            log.info(ResponseCode.REGISTER_SUCCESS.getMessage());
            log.info("----------------------------------");


            resp.setCode(ResponseCode.REGISTER_SUCCESS.getCode());
            resp.setMessage(ResponseCode.REGISTER_SUCCESS.getMessage());
            resp.setData(UserUtils.copyDuplicateFieldsFromA2B(user, new RegisterDataUser()));
        } catch (BusinessException e) {
            resp.setCode(e.getCode());
            resp.setMessage(e.getMessage());
            resp.setData(null);
        }
        catch (Exception e) {
            e.printStackTrace();
            resp.setCode(ResponseCode.SERVER_ERROR.getCode());
            resp.setMessage(ResponseCode.SERVER_ERROR.getMessage());
            resp.setData(null);
        }
        return resp;
    }

    @PostMapping("/register/batch")
    public Result BatchRegister(@RequestParam("file") MultipartFile file) {
        Result resp = new Result();

        try {
            Result result = userService.batchRegister(file);

            log.info("--------------用户注册--------------");
            log.info("状态码为：" + ResponseCode.REGISTER_SUCCESS.getCode());
            log.info(ResponseCode.REGISTER_SUCCESS.getMessage());
            log.info("----------------------------------");

            return result;
        } catch (BusinessException e) {
            resp.setCode(e.getCode());
            resp.setMessage(e.getMessage());
            resp.setData(null);
        }
        catch (Exception e) {
            e.printStackTrace();
            resp.setCode(ResponseCode.SERVER_ERROR.getCode());
            resp.setMessage(ResponseCode.SERVER_ERROR.getMessage());
            resp.setData(null);
        }
        return resp;
    }


    @PostMapping("/login")
    public Result Login(@RequestBody User user) {
        Result resp = null;

        try {
            resp = userService.login(user.getUsername(), user.getPassword());

            log.info("--------------用户登录--------------");
            log.info("输入的账户名称为：" + user.getUsername());
            log.info("匹配的账户名称为：" + user.getUsername());
            log.info(ResponseCode.LOGIN_SUCCESS.getMessage());
            log.info("----------------------------------");

        } catch (BusinessException e) {
            resp = (resp == null) ? new Result() : resp;
            resp.setCode(e.getCode());
            resp.setMessage(e.getMessage());
            resp.setData(null);

            log.info("--------------登录失败--------------");
            log.info("输入的账户名称为：" + user.getUsername());
            log.info("匹配的账户名称为：null");
            log.info(e.getMessage());
            log.info("----------------------------------");

        } catch (Exception e) {
            e.printStackTrace();
            resp.setCode(ResponseCode.SERVER_ERROR.getCode());
            resp.setMessage(ResponseCode.SERVER_ERROR.getMessage());
            resp.setData(null);
        }
        return resp;
    }

    @GetMapping("/info")
    public Result info(HttpServletRequest request) {
        Result resp = new Result();

        User user = null;
        try {
            AuthInfo authInfo = (AuthInfo)request.getAttribute("authInfo");
            user = userService.getUserInfo(authInfo.getUsername());
            resp.setCode(200);
            resp.setMessage("查询成功");
            resp.setData(UserUtils.copyDuplicateFieldsFromA2B(user, new InfoDataUser()));
        } catch (BusinessException e) {
            resp.setCode(e.getCode());
            resp.setMessage(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resp.setCode(ResponseCode.SERVER_ERROR.getCode());
            resp.setMessage(ResponseCode.SERVER_ERROR.getMessage());
            resp.setData(null);
        }

        return resp;
    }

    @PutMapping("/change-pwd")
    public Result changePassword(@RequestBody Map<String, String> paramMap) {

        String oldPassword = paramMap.get("oldPassword");
        String newPassword = paramMap.get("newPassword");
        String username = paramMap.get("username");

        Result resp = new Result();
        resp.setData(null);

        log.info("--------------修改密码--------------");
        log.info("账户名称为：" + username);
        log.info("旧密码为：" + oldPassword);
        log.info("新密码为：" + newPassword);

        try {
            userService.changePassword(username, oldPassword, newPassword);
            resp.setCode(200);
            resp.setMessage("密码修改成功");
            log.info("--------------修改成功--------------");
        } catch (BusinessException e) {
            log.info("------------原密码输入错误-----------");
            resp.setCode(e.getCode());
            resp.setMessage(e.getMessage());
        }
        catch (Exception e) {
            e.printStackTrace();
            resp.setCode(ResponseCode.SERVER_ERROR.getCode());
            resp.setMessage(ResponseCode.SERVER_ERROR.getMessage());
        }
        return resp;
    }

    @DeleteMapping("/{userId}")
    public Result deleteUserByUserId(@PathVariable Integer userId) {
        System.out.printf("删除用户：%s 成功%n", userId);
        Boolean deleted = userService.deleteUserByUserId(userId);
        if (deleted) {
            return Result.success("删除用户：%s 成功".formatted(userId),null);
        }
        return Result.error("删除用户：%s 失败".formatted(userId),null);
    }
}

