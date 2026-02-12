package com.aiqa.project1.mapper;

import com.aiqa.project1.pojo.tag.OrganizationTag;
import com.aiqa.project1.pojo.user.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SpecialTagMapper extends BaseMapper<OrganizationTag> {
    @Select("select * from organization_tags where parent_tag=#{parentTag}")
    public List<OrganizationTag> getTagsByParentTag(String parentTag);
}