package com.aiqa.project1.controller;

import com.aiqa.project1.pojo.AuthInfo;
import com.aiqa.project1.pojo.ResponseCode;
import com.aiqa.project1.pojo.Result;
import com.aiqa.project1.service.impl.SpecialTagService;
import com.aiqa.project1.utils.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tag")
public class SpecialTagController {
    private final SpecialTagService specialTagService;

    @Autowired
    public SpecialTagController(SpecialTagService specialTagService) {
        this.specialTagService = specialTagService;
    }

    /**
     * 创建Tag（只有管理员可以）
     * @param paramMap
     * @return
     */
    @PostMapping("/create")
    public Result createTag(@RequestBody Map<String, String> paramMap, HttpServletRequest request) {
        AuthInfo authInfo = (AuthInfo)request.getAttribute("authInfo");
        if (!"ADMIN".contains(authInfo.getRole()))
            return Result.define(ResponseCode.AUTH_ERROR.getCode(), ResponseCode.AUTH_ERROR.getMessage(),null);

        String tag = paramMap.get("tag");
        String parentTag = paramMap.get("parentTag");
        String description = paramMap.get("description");
        return specialTagService.createTag(Integer.valueOf(authInfo.getUserId()), tag, description, parentTag);
    }

    /**
     * 为普通用户添加Tag
     * @param paramMap
     * @param request
     * @return
     */
    @PostMapping("/join")
    public Result joinTag(@RequestBody Map<String, String> paramMap, HttpServletRequest request) {
        AuthInfo authInfo = (AuthInfo) request.getAttribute("authInfo");
        if (!"ADMIN".contains(authInfo.getRole()))
            return Result.define(ResponseCode.AUTH_ERROR.getCode(), ResponseCode.AUTH_ERROR.getMessage(),null);

        String tag = paramMap.get("tag");
        String userName = paramMap.get("username");
        return specialTagService.joinTag(tag, userName);
    }

    /**
     * 获取所有普通用户的信息，包括标签
     * @param pageNum
     * @param pageSize
     * @param request
     * @return
     */
    @GetMapping("/list")
    public Result getUserList(
            @RequestParam(name = "pageNum") Integer pageNum,
            @RequestParam(name = "pageSize") Integer pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            HttpServletRequest request
    ) {
        AuthInfo authInfo = (AuthInfo)request.getAttribute("authInfo");
        return specialTagService.requestUserInfoList(pageNum, pageSize, keyword, authInfo);
    }

    /**
     * 获取所有普通用户的信息，包括标签
     * @param pageNum
     * @param pageSize
     * @param request
     * @return
     */
    @GetMapping("/tagList")
    public Result getTagList(
            @RequestParam(name = "pageNum") Integer pageNum,
            @RequestParam(name = "pageSize") Integer pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            HttpServletRequest request
    ) {
        AuthInfo authInfo = (AuthInfo)request.getAttribute("authInfo");
        return specialTagService.requestTagInfoList(pageNum, pageSize, keyword, authInfo);
    }


    /**
     * 根据ID获取指定普通用户的信息
     * @param userId
     * @param request
     * @return
     */
    @GetMapping("/list/tag/admin")
    public Result getUserList(
            @RequestParam(name = "userId") Integer userId,
            HttpServletRequest request
    ) {
        AuthInfo authInfo = (AuthInfo)request.getAttribute("authInfo");
        authInfo.setUserId(String.valueOf(userId));
        if (! authInfo.getRole().equals("ADMIN")) {
            return Result.define(ResponseCode.AUTH_ERROR.getCode(), ResponseCode.AUTH_ERROR.getMessage(),null);
        }
        return specialTagService.getAllTagsByUserId(authInfo);
    }

    /**
     * 根据Tag ID获取有该标签的所有用户
     * @param tagId
     * @param request
     * @return
     */
    @GetMapping("/list/users")
    public Result getUserList(
            @RequestParam(name = "tagId") Long tagId,
            HttpServletRequest request
    ) {
        AuthInfo authInfo = (AuthInfo)request.getAttribute("authInfo");
        if (! authInfo.getRole().equals("ADMIN")) {
            return Result.define(ResponseCode.AUTH_ERROR.getCode(), ResponseCode.AUTH_ERROR.getMessage(),null);
        }
        return specialTagService.getAllUsersByTagId(tagId);
    }

    /**
     * 根据ID获取指定普通用户的信息,用于文档上传前获取自己的标签
     * @param request
     * @return
     */
    @GetMapping("/list/tag/user")
    public Result getUser(
            HttpServletRequest request
    ) {
        AuthInfo authInfo = (AuthInfo)request.getAttribute("authInfo");
        return specialTagService.getAllTagsByUserId(authInfo);
    }

    /**
     * 获取所有标签的信息
     * @param request
     * @return
     */
    @GetMapping("/list/tag/all")
    public Result getAllTag(HttpServletRequest request
    ) {
        return specialTagService.getAllTags(request);
    }




}