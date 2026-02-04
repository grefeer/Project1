package com.aiqa.project1.service.impl;

import com.aiqa.project1.mapper.UserTagMapper;
import com.aiqa.project1.pojo.AuthInfo;
import com.aiqa.project1.pojo.ResponseCode;
import com.aiqa.project1.pojo.Result;
import com.aiqa.project1.mapper.SpecialTagMapper;
import com.aiqa.project1.mapper.UserMapper;
import com.aiqa.project1.pojo.tag.OrganizationTag;
import com.aiqa.project1.pojo.tag.TagQueryList;
import com.aiqa.project1.pojo.tag.UserQueryList;
import com.aiqa.project1.pojo.tag.UserTag;
import com.aiqa.project1.pojo.user.User;
import com.aiqa.project1.utils.BusinessException;
import com.aiqa.project1.utils.MilvusSearchUtils1;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class SpecialTagService {
    private final SpecialTagMapper specialTagMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserTagMapper userTagMapper;
    private final MilvusSearchUtils1 milvusSearchUtils1;

    @Autowired
    public SpecialTagService(SpecialTagMapper specialTagMapper, UserMapper userMapper, PasswordEncoder passwordEncoder, UserTagMapper userTagMapper, MilvusSearchUtils1 milvusSearchUtils1) {
        this.specialTagMapper = specialTagMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.userTagMapper = userTagMapper;
        this.milvusSearchUtils1 = milvusSearchUtils1;
    }

    @Transactional
    public Result createTag(Integer userId, String tagName, String description, String parentTag) {
        // 检查tag是否已经存在
        QueryWrapper<OrganizationTag> wrapper = new QueryWrapper<>();
        wrapper.eq("tag_name", tagName);
        OrganizationTag existingTag = specialTagMapper.selectOne(wrapper);
        if (existingTag != null) {
            throw new BusinessException(400, "Tag already exists", null);
        }

        // 创建新tag
        OrganizationTag specialTag = new OrganizationTag();
        specialTag.setCreatedAt(LocalDateTime.now());
        specialTag.setDescription(description);
        specialTag.setParentTag(parentTag);
        specialTag.setTagName(tagName);
        specialTag.setCreatedBy(userId);

        specialTagMapper.insert(specialTag);
//
//        // 创建该tag的milvus分区
//        milvusSearchUtils1.createPartition(tagName);


        return new Result(200, "Tag created successfully", null);
    }

    @Transactional
    public Result joinTag(String tagName, String userName) {
        // 检查tag是否存在
        QueryWrapper<OrganizationTag> wrapper = new QueryWrapper<>();
        wrapper.eq("tag_name", tagName);
        OrganizationTag specialTag = specialTagMapper.selectOne(wrapper);
        if (specialTag == null) {
            throw new BusinessException(404, "Tag not found", null);
        }
        User user = userMapper.getUserByUserName(userName);
        if (user == null) {
            return Result.define(404, "User not found", null);
        }

        if (specialTag.getTagName().contains("ADMIN")) {
            userMapper.updateUserRoleById(user.getUserId(), "ADMIN");
        }

        // 添加用户到tag
        UserTag entity = new UserTag();
        entity.setUserId(user.getUserId());
        entity.setTagId(specialTag.getTagId());
        userTagMapper.insert(entity);
        return new Result(200, "Joined tag successfully", null);
    }

    /**
     * 根据tag名称获取其余信息
     * @param tagName
     * @return
     */
    public Result getTag(String tagName) {
        QueryWrapper<OrganizationTag> wrapper = new QueryWrapper<>();
        wrapper.eq("tag_name", tagName);
        OrganizationTag specialTag = specialTagMapper.selectOne(wrapper);
        if (specialTag == null) {
            throw new BusinessException(404, "Tag not found", null);
        }
        return new Result(200, "Tag get successfully", specialTag);
    }

    /**
     * 获取所有的标签
     * @return
     */
    public Result getAllTags(HttpServletRequest request) {
        AuthInfo authInfo = (AuthInfo)request.getAttribute("authInfo");
        if (! authInfo.getRole().equals("ADMIN")) {
            return Result.define(ResponseCode.AUTH_ERROR.getCode(), ResponseCode.AUTH_ERROR.getMessage(),null);
        }
        List<OrganizationTag> organizationTags = specialTagMapper.selectList(new QueryWrapper<>());
        return new Result(200, "All tags successfully", organizationTags);
    }

    /**
     * 获取某个用户所有的标签
     * @param authInfo
     * @return
     */
    public Result getAllTagsByUserId(AuthInfo authInfo) {
        String userId = authInfo.getUserId();
        List<OrganizationTag> organizationTags = getAllTagsByUserId_(Integer.valueOf(userId));
        return new Result(200, "All tags successfully", organizationTags);
    }

    /**
     * 获取某个用户所有的标签
     * @param userId
     * @return
     */
    public List<OrganizationTag> getAllTagsByUserId_(Integer userId) {
        QueryWrapper<UserTag> userTagQueryWrapper = new QueryWrapper<>();
        System.out.println("11111122222222");
        System.out.println(userId);
        userTagQueryWrapper.eq("user_id", userId);
        List<UserTag> userTagList = userTagMapper.selectList(userTagQueryWrapper);
        System.out.println(userTagList.isEmpty());
        List<Long> tagIds = new ArrayList<>(userTagList.stream()
                .map(UserTag::getTagId)
                .toList());

        if (tagIds.isEmpty()) {
            return new ArrayList<>(); // 无标签时直接返回空列表
        }
        return specialTagMapper.selectList(new QueryWrapper<OrganizationTag>().in("tag_id", tagIds));
    }

    @Transactional(rollbackFor = Exception.class)
    public Result requestUserInfoList(Integer pageNum, Integer pageSize, String keyword, AuthInfo authInfo) {
        Result result = new Result();
        UserQueryList data = new UserQueryList();

        try {
            if (! authInfo.getRole().equals("ADMIN")) {
                return Result.define(ResponseCode.AUTH_ERROR.getCode(), ResponseCode.AUTH_ERROR.getMessage(),null);
            }

            pageSize = pageSize == null ? 10 : pageSize;
            pageNum = pageNum == null ? 1 : pageNum;
            keyword = keyword == null ? "" : keyword;

            IPage<User> page = new Page<>(pageNum, pageSize);

            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("role", "USERS");
            if (! keyword.isEmpty()) {
                queryWrapper.like("username", keyword);
            }

            QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
            userQueryWrapper.eq("role", "USERS");
            Long totalUserCount = userMapper.selectCount(userQueryWrapper);

            int totalpages = (int)(totalUserCount / pageSize + 1);

            data.setTotal(totalUserCount);
            data.setPages(totalpages);
            data.setPageSize(pageSize);
            data.setPageNum(pageNum);


            IPage<User> userPage = userMapper.selectPage(page, queryWrapper); // 调用 selectPage 方法
            List<User> userList = userPage.getRecords();

            long total = userPage.getTotal();
            System.out.println("Total documents: " + total);
            for (User user : userList) {
                System.out.println("User: " + user);
            }

            data.setList(userList);
            result.setMessage("查询成功");
            result.setData(data);
            result.setCode(200);

            System.out.println(data);

        } catch (Exception e) {
            result.setMessage("查询失败");
            result.setData(e);
            result.setCode(500);;
            e.printStackTrace();
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Result requestTagInfoList(Integer pageNum, Integer pageSize, String keyword, AuthInfo authInfo) {
        Result result = new Result();
        TagQueryList data = new TagQueryList();

        try {
            if (! authInfo.getRole().equals("ADMIN")) {
                return Result.define(ResponseCode.AUTH_ERROR.getCode(), ResponseCode.AUTH_ERROR.getMessage(),null);
            }

            pageSize = pageSize == null ? 10 : pageSize;
            pageNum = pageNum == null ? 1 : pageNum;
            keyword = keyword == null ? "" : keyword;

            IPage<OrganizationTag> page = new Page<>(pageNum, pageSize);

            QueryWrapper<OrganizationTag> queryWrapper = new QueryWrapper<>();
            if (! keyword.isEmpty()) {
                queryWrapper.like("tag_name", keyword);
            }

            QueryWrapper<OrganizationTag> tagQueryWrapper = new QueryWrapper<>();
            Long totalUserCount = specialTagMapper.selectCount(tagQueryWrapper);

            int totalPages = (int)(totalUserCount / pageSize + 1);

            data.setTotal(totalUserCount);
            data.setPages(totalPages);
            data.setPageSize(pageSize);
            data.setPageNum(pageNum);


            IPage<OrganizationTag> tagPage = specialTagMapper.selectPage(page, queryWrapper); // 调用 selectPage 方法
            List<OrganizationTag> tagList = tagPage.getRecords();

            long total = tagPage.getTotal();
            System.out.println("Total documents: " + total);
            for (OrganizationTag organizationTag : tagList) {
                System.out.println("标签: " + organizationTag);
            }

            data.setList(tagList);
            result.setMessage("查询成功");
            result.setData(data);
            result.setCode(200);

            System.out.println(data);

        } catch (Exception e) {
            result.setMessage("查询失败");
            result.setData(e);
            result.setCode(500);;
            e.printStackTrace();
        }
        return result;
    }

    public Result getAllUsersByTagId(Long tagId) {
        // 直接调用自定义SQL的Mapper方法
        try {
            return Result.success("success", userMapper.selectUserByTagId(tagId));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(e.getMessage(), null);
        }
    }

}