package com.aiqa.project1.service.impl;

import com.aiqa.project1.controller.UserController;
import com.aiqa.project1.mapper.*;
import com.aiqa.project1.pojo.*;
import com.aiqa.project1.pojo.document.Document;
import com.aiqa.project1.pojo.qa.SessionChat;
import com.aiqa.project1.pojo.qa.UserChatMemory;
import com.aiqa.project1.pojo.tag.OrganizationTag;
import com.aiqa.project1.pojo.tag.TagNameCount;
import com.aiqa.project1.pojo.tag.UserTag;
import com.aiqa.project1.pojo.user.LoginDataUser;
import com.aiqa.project1.pojo.user.User;
import com.aiqa.project1.pojo.user.UserForCsv;
import com.aiqa.project1.service.UserService;
import com.aiqa.project1.utils.BusinessException;
import com.aiqa.project1.utils.JwtUtils;
import com.aiqa.project1.utils.MilvusSearchUtils1;
import com.aiqa.project1.utils.UserUtils;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import com.alibaba.fastjson.JSON;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;

@Service
public class UserServiceimpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);


    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserTagMapper userTagMapper;
    private final SpecialTagMapper specialTagMapper;
    private final DocumentMapper documentMapper;
    private final SessionChatMapper sessionChatMapper;
    private final UserChatMemoryMapper userChatMemoryMapper;
    private final MilvusSearchUtils1 milvusSearchUtils1;

    @Autowired
    public UserServiceimpl(UserMapper userMapper, PasswordEncoder passwordEncoder, UserTagMapper userTagMapper, SpecialTagMapper specialTagMapper, DocumentMapper documentMapper, DocumentTagMapper documentTagMapper, SessionChatMapper sessionChatMapper, UserChatMemoryMapper userChatMemoryMapper, MilvusSearchUtils1 milvusSearchUtils1) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.userTagMapper = userTagMapper;
        this.specialTagMapper = specialTagMapper;
        this.documentMapper = documentMapper;
        this.sessionChatMapper = sessionChatMapper;
        this.userChatMemoryMapper = userChatMemoryMapper;
        this.milvusSearchUtils1 = milvusSearchUtils1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.NESTED)
    public int register(User user) {

        User existingUser = userMapper.getUserByUserName(user.getUsername());
        if (existingUser != null) {
            throw new BusinessException(
                    ResponseCode.USERNAME_EXIST.getCode(),
                    ResponseCode.USERNAME_EXIST.getMessage(),
                    null
            );
        }

        LocalDateTime now = LocalDateTime.now();
        String createdTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        user.setCreatedTime(createdTime);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        try {
            userMapper.insertUser(user);
        } catch (DuplicateKeyException e) {
            String errMsg = "";
            if (e.getMessage().contains("uk_phone")) {
                errMsg = errMsg + "电话号码重复\n";
            }
            if (e.getMessage().contains("uk_username")) {
                errMsg = errMsg + "用户名称重复\n";
            }
            if (e.getMessage().contains("uk_email")) {
                errMsg = errMsg + "电子邮箱重复\n";
            }
            throw new BusinessException(500, errMsg, null);
        }

        return setTagsForNewUser(user);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result batchRegister(MultipartFile userCsv) {
        if (userCsv.isEmpty()) {
            throw new BusinessException(404, "上传的CSV文件不能为空！", null);
        }
        String originalFilename = userCsv.getOriginalFilename();
        if (originalFilename == null || !("csv".equalsIgnoreCase(FilenameUtils.getExtension(originalFilename)) || "xlsx".equalsIgnoreCase(FilenameUtils.getExtension(originalFilename)))) {
            System.out.println(FilenameUtils.getExtension(originalFilename));
            throw new BusinessException(404, "请上传后缀为.csv的文件！！", null);
        }

        try  {            // 替换原解析逻辑，使用CsvToBean解析为实体类
            List<UserForCsv> userList = EasyExcel.read(userCsv.getInputStream())
                    .head(UserForCsv.class)
                    .sheet()
                    .doReadSync();
//            List<UserForCsv> userList = csvToBean.parse();

            System.out.println("解析出用户数量：" + userList.size());
            // 校验用户元数据
            validateUser(userList, false);
            List<User> userList1 = userList.stream().map(this::buildUserEntity).toList();
            // 复用register
            userList1.forEach(this::register);
            // 为新用户贴标签
            for (UserForCsv user : userList) {
                String username = user.getUsername();
                if (user.getTagName() == null || user.getTagName().isEmpty())
                    continue;
                String[] tagNames = user.getTagName().split(",");

                System.out.println("TagName" + user.getTagName());

                Arrays.stream(tagNames).forEach(tagName ->
                {
                    OrganizationTag organizationTag = specialTagMapper.selectOne(new QueryWrapper<OrganizationTag>().eq("tag_name", tagName));
                    if (organizationTag == null) throw new BusinessException(404, "数据库里没有这个标签%s".formatted(tagName), null);
                    userTagMapper.insertUserTagRsByTagName(username, tagName);
                });

            }
//            userMapper.insert(userList1);
//            // 为每个用户夹在默认标签
//            userList1.forEach(this::setTagsForNewUser);

            return Result.success("用户批量导入成功，共导入" + userList.size() + "个用户", null);

        } catch (IOException e) {
            return Result.define(500, "文件读取失败：" + e.getMessage(), null);
        } catch (BusinessException e) {
            e.printStackTrace();
            return Result.define(500,  e.getMessage(), null);

        }
    }

    @Override
    public Result login(String username, String password) {
        Result resp = new Result();

        LocalDateTime now = LocalDateTime.now();
        String ExpireTime = now.plusHours(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        User user1 = userMapper.getUserByUserName(username);

        System.out.println(user1.getUserId());
        if (user1 == null || !passwordEncoder.matches(password, user1.getPassword())) {
            throw new BusinessException(
                    ResponseCode.PASSWORD_ERROR.getCode(),
                    ResponseCode.PASSWORD_ERROR.getMessage(),
                    null
            );
        }

        String token = JwtUtils.GenerateJwt(UserUtils.User2Map(user1), user1.getUserId().toString());
        user1.setToken(token);
        LoginDataUser data = UserUtils.copyDuplicateFieldsFromA2B(user1, new LoginDataUser());
        data.setExpireTime(ExpireTime);

        resp.setCode(ResponseCode.LOGIN_SUCCESS.getCode());
        resp.setMessage(ResponseCode.LOGIN_SUCCESS.getMessage());
        resp.setData(data);

        return resp;
    }

    @Override
    public User getUserInfo(String username) {

        User user = userMapper.getUserByUserName(username);
        user.setUserId(userMapper.getUserIdByUserName(username));
        if (user == null) {
            throw new BusinessException(
                    ResponseCode.RESOURCE_ERROR.getCode(),
                    ResponseCode.RESOURCE_ERROR.getMessage(),
                    null
            );
        }
        return user;
    }

    @Override
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = userMapper.getUserByUserName(username);

        String password = passwordEncoder.encode(newPassword);

        if (! passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(
                    ResponseCode.PASSWORD_ERROR.getCode(),
                    ResponseCode.PASSWORD_ERROR.getMessage(),
                    null
            );
        }
        userMapper.updateUserPasswordByUserName(username, password);
    }

    @Override
    public void changePhone(String username, String oldPhone, String newPhone) {
        User user = userMapper.getUserByUserName(username);

        if (! oldPhone.equals(user.getPhone())) {
            throw new BusinessException(
                    ResponseCode.PASSWORD_ERROR.getCode(),
                    "旧电话号码输入错误",
                    null
            );
        }

        // TODO 缺乏验证码校验过程
        userMapper.updateUserPhoneByUserName(username, newPhone);
    }

    @Override
    public void changeEmail(String username, String oldEmail, String newEmail) {
        User user = userMapper.getUserByUserName(username);

        if (! oldEmail.equals(user.getEmail())) {
            throw new BusinessException(
                    ResponseCode.PASSWORD_ERROR.getCode(),
                    "旧邮箱输入错误",
                    null
            );
        }

        // TODO 缺乏验证码校验过程
        userMapper.updateUserPhoneByUserName(username, newEmail);
    }


    @Override
    @Transactional
    public Boolean deleteUserByUserId(Integer userId) {
        try {
            // 删除该用户的所有相关信息,userTagMapper不用使用，因为User表的userId和UserTag的userId绑定，并设置了删除user其tag自动删除
//            userTagMapper.delete(new QueryWrapper<UserTag>().eq("user_id", userId));
            userMapper.deleteUserById(userId);
            documentMapper.delete(new QueryWrapper<Document>().eq("user_id", userId));
            sessionChatMapper.delete(new QueryWrapper<SessionChat>().eq("user_id", userId));
            userChatMemoryMapper.delete(new QueryWrapper<UserChatMemory>().eq("user_id", userId));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /*
    获取系统仪表盘
    用户状态：总人数，今日在线人数，部门用户分布
    问答核心：今日问答数、累计问答数
    知识库：已上传文档数、有效向量条数、待处理 / 处理失败文档数
    */
    @Override
    public Map<String, String> getDashBoard() {
        try {
            // 用户状态：总人数，今日在线人数，部门用户分布
            Long userNumber = userMapper.selectCount(new QueryWrapper<>());
            Integer todayActiveUserCount = userChatMemoryMapper.getTodayActiveUserCount();
            List<TagNameCount> tagsCountAndNames = specialTagMapper.getTagsCountAndNames();
            // 问答核心：今日问答数、累计问答数
            Integer todayActiveChatCount = userChatMemoryMapper.getTodayActiveChatCount();
            Long totalChatCount = userChatMemoryMapper.selectCount(new QueryWrapper<>());
            // 知识库：已上传文档数、有效向量条数、待处理 / 处理失败文档数
            Long documentCount = documentMapper.selectCount(new QueryWrapper<>());
            Long documentSuccessCount = documentMapper.selectCount(new QueryWrapper<Document>().eq("status", "AVAILABLE"));
            Long embeddingCount = milvusSearchUtils1.countAllValidVectors();

            // ========== 新增：过去7天每日统计 ==========
            // 1. 过去7天每天的成功上传文档数
            List<DailyCountVO> sevenDaysDailyDocCount = fillEmptyDate(documentMapper.getSevenDaysDailySuccessDocumentCount());
            // 2. 过去7天每天的问答数
            List<DailyCountVO> sevenDaysDailyChatCount = fillEmptyDate(userChatMemoryMapper.getSevenDaysDailyChatCount());

            return Map.of(
                    "userNumber", userNumber.toString(),
                    "todayActiveUserCount", todayActiveUserCount.toString(),
                    "tagsCountAndNames", CollectionUtils.isEmpty(tagsCountAndNames) ? "[]" : JSON.toJSONString(tagsCountAndNames),
                    "todayActiveChatCount", todayActiveChatCount.toString(),
                    "totalChatCount", totalChatCount.toString(),
                    "documentCount", documentCount.toString(),
                    "documentFailureCount", Long.toString(documentCount - documentSuccessCount),
                    "embeddingCount", embeddingCount.toString(),
                    "sevenDaysDailyDocCount", CollectionUtils.isEmpty(sevenDaysDailyDocCount) ? "[]" : JSON.toJSONString(sevenDaysDailyDocCount),
                    "sevenDaysDailyChatCount", CollectionUtils.isEmpty(sevenDaysDailyChatCount) ? "[]" : JSON.toJSONString(sevenDaysDailyChatCount)
            );
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("error", e.getMessage());
        }
    }

    // 示例：补全过去7天的日期，无数据则count=0
    private List<DailyCountVO> fillEmptyDate(List<DailyCountVO> originalList) {
        // 1. 生成过去7天的所有日期（yyyy-MM-dd）
        List<String> last7Days = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            last7Days.add(today.minusDays(i).toString());
        }
        // 2. 补全数据
        Map<String, Integer> countMap = originalList.stream()
                .collect(Collectors.toMap(DailyCountVO::getDate, DailyCountVO::getCount));
        return last7Days.stream()
                .map(date -> new DailyCountVO(date, countMap.getOrDefault(date, 0)))
                .collect(Collectors.toList());
    }

    private User buildUserEntity(UserForCsv userForCsv) {
        User user = new User();
        user.setUsername(userForCsv.getUsername());
        user.setPassword(userForCsv.getPassword()); // 注意：实际项目中需加密密码（比如BCrypt）
        user.setPhone(userForCsv.getPhone());
        user.setEmail(userForCsv.getEmail());
        user.setRole(userForCsv.getRole());
        user.setCreatedTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return user;
    }


    private void validateUser(List<UserForCsv> userForCsvList, Boolean coverageFlag) {
        // coverageFlag为true，则
        StringBuilder errStr = new StringBuilder();
        if (!coverageFlag) {
            String users = userForCsvList.stream()
                    .map(UserForCsv::getUsername)
                    .filter(username -> userMapper.selectOne(new QueryWrapper<User>().eq("username", username)) != null)
                    .collect(Collectors.joining(","));
            if (!users.isEmpty())
                errStr.append("用户%s已经存在，无需注册".formatted(users));
        }
        for (UserForCsv userForCsv : userForCsvList) {
            System.out.println(userForCsv);

            String username = userForCsv.getUsername();
            String password = userForCsv.getPassword();
            String phone = userForCsv.getPhone();
            String email = userForCsv.getEmail();
            // 非空校验
            if (!StringUtils.hasText(username)) {
                errStr.append("用户名不能为空");
            }
            if (!StringUtils.hasText(password)) {
                errStr.append("%s的密码不能为空\n".formatted(username));
            }
            // 手机号/邮箱格式校验（可根据业务补充正则）
            if (StringUtils.hasText(phone) && !phone.matches("^1[3-9]\\d{9}$")) {
                errStr.append("%s的手机号格式不正确\n".formatted(username));
            }
            if (StringUtils.hasText(email) && !email.matches("^\\w+([-+.']\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*$")) {
                errStr.append("%s的邮箱格式不正确\n".formatted(username));
            }
        }
        String errString = errStr.toString();
        if (!errString.isEmpty()) {
            throw new BusinessException(400, errString, null);
        }
    }
    private void validateUserParams(UserForCsv userForCsv) {
        String username = userForCsv.getUsername();
        String password = userForCsv.getPassword();
        String phone = userForCsv.getPhone();
        String email = userForCsv.getEmail();

        // 非空校验
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("---用户名不能为空");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("---密码不能为空");
        }
        // 手机号/邮箱格式校验（可根据业务补充正则）
        if (StringUtils.hasText(phone) && !phone.matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("---手机号格式不正确");
        }
        if (StringUtils.hasText(email) && !email.matches("^\\w+([-+.']\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*$")) {
            throw new IllegalArgumentException("---邮箱格式不正确");
        }
    }

    private Integer setTagsForNewUser(User user) {
        UserTag entity1 = new UserTag();
        UserTag entity2 = new UserTag();
        UserTag entity3 = new UserTag();
        Integer userId = userMapper.getUserIdByUserName(user.getUsername());

        // 2是个人标签ID，3是公共标签ID
        entity1.setTagId(2L);
        entity1.setUserId(userId);
        entity2.setTagId(3L);
        entity2.setUserId(userId);
        if ("ADMIN".equals(user.getRole())) {
            entity3.setTagId(1L);
            entity3.setUserId(userId);
            userTagMapper.insert(List.of(entity1, entity2, entity3));
        } else {
            userTagMapper.insert(List.of(entity1, entity2));
        }
        return userId;
    }

}

