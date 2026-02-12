package com.aiqa.project1.mapper;

import com.aiqa.project1.pojo.tag.UserTag;
import com.aiqa.project1.pojo.tag.UserTagHierarchyVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserTagMapper extends BaseMapper<UserTag> {
    /**
     * 查询指定用户的所有标签（含多级子标签）
     * @param userId 目标用户ID
     * @return 标签层级列表
     */
    List<UserTagHierarchyVO> selectUserAllTagsWithChildren(@Param("userId") Integer userId);

    @Insert("""
        INSERT INTO user_tag (user_id, tag_id)
        SELECT u.user_id, ot.tag_id
        FROM user u
        INNER JOIN organization_tags ot ON u.username = #{username} AND ot.tag_name = #{tagName}
        ON DUPLICATE KEY UPDATE user_tag.user_id = user_tag.user_id;
        """)
    int insertUserTagRsByTagName(@Param("username") String username, @Param("tagName") String tagName);
}
