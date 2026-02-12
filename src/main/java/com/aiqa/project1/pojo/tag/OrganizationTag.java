package com.aiqa.project1.pojo.tag;

import com.aiqa.project1.pojo.user.User;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("organization_tags")
public class OrganizationTag implements Serializable {
    private static final long serialVersionUID = 9527L;

    @TableId(type = IdType.AUTO)
    @TableField("tag_id")
    private Long tagId; // 标签唯一标识

    @TableField("tag_name")
    private String tagName; // 标签名称

    @TableField("description")
    private String description; // 描述

    @TableField("parent_tag")
    private String parentTag = null; // 父标签ID

    @TableField("created_by")
    private Integer createdBy; // 创建者ID

    @TableField
    private LocalDateTime createdAt; // 创建时间
} 