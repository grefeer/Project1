package com.aiqa.project1.pojo.tag;

import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("organization_tags")
public class OrganizationTagForCsv {

    @ExcelProperty("标签名称")
    private String tagName; // 标签名称

    @ExcelProperty("描述")
    private String description; // 描述

    @ExcelProperty("父标签ID")
    private String parentTag; // 父标签ID

} 