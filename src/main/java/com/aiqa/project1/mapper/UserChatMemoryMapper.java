package com.aiqa.project1.mapper;

import com.aiqa.project1.pojo.DailyCountVO;
import com.aiqa.project1.pojo.qa.UserChatMemory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserChatMemoryMapper extends BaseMapper<UserChatMemory> {
    @Select("""
    select count(distinct userchatmemory.user_id) as today_active_user_count
    from userchatmemory
    where date(last_active_time) = CURDATE()
""")
    Integer getTodayActiveUserCount();

    @Select("""
    select count( userchatmemory.content) as today_active_chat_count
    from userchatmemory
    where date(last_active_time) = CURDATE()
""")
    Integer getTodayActiveChatCount();

    // 新增：过去7天每天的问答数（按日期分组）
    @Select("""
        SELECT
        DATE(last_active_time) as date,
        COUNT(content) as count
        FROM userchatmemory
        WHERE last_active_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
        -- 若有回答成功状态字段，补充：AND status = 'SUCCESS'
        GROUP BY DATE(last_active_time)
        ORDER BY date
    """)
    List<DailyCountVO> getSevenDaysDailyChatCount();

}


