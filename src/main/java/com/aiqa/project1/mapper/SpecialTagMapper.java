package com.aiqa.project1.mapper;

import com.aiqa.project1.pojo.tag.OrganizationTag;
import com.aiqa.project1.pojo.tag.TagNameCount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SpecialTagMapper extends BaseMapper<OrganizationTag> {
    @Select("select * from organization_tags where parent_tag=#{parentTag}")
    public List<OrganizationTag> getTagsByParentTag(String parentTag);

    @Select("""
    select t1.tag_name, COUNT(DISTINCT t2.user_id) as count
    from organization_tags t1
    left join user_tag t2
    on t1.tag_id = t2.tag_id
    group by t1.tag_id, t1.tag_name
    order by count desc
    """)
    public List<TagNameCount> getTagsCountAndNames();
}