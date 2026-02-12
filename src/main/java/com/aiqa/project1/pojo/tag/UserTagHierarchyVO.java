package com.aiqa.project1.pojo.tag;

import lombok.Data;

/**
 * 标签层级查询结果实体
 */
@Data
public class UserTagHierarchyVO {
    /** 标签ID */
    private Long tagId;
    /** 根标签ID（用户直接绑定的标签） */
    private Long rootTagId;
    /** 层级（0=直接标签，1=一级子标签，2=二级子标签...） */
    private Integer level;
}