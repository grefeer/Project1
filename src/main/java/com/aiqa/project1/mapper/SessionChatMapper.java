package com.aiqa.project1.mapper;

import com.aiqa.project1.pojo.qa.SessionChat;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SessionChatMapper extends BaseMapper<SessionChat> {

    @Update("update sessionchat set session_name=#{sessionName} where user_id=#{userId} && session_id=#{sessionId}")
    public void updateSessionNameByUserIdAndSessionId(Integer userId, Integer sessionId, String sessionName);
}