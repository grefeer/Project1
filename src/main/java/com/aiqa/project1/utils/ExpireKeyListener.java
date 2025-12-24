package com.aiqa.project1.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;


@Component
public class ExpireKeyListener {

    @Autowired
    private RedisStoreUtils redisStoreUtils;

    /**
     * 监听键过期事件，触发修改关联键
     * @param expiredKey 过期的键名（字节数组转字符串）
     */
    public void onKeyExpired(String expiredKey) {
        // 若过期的键是 ""chatMemory:userId:%s:sessionId:%s""，则修改 "activateSession:userId:%s"
        if (expiredKey.startsWith("chatMemory:userId:")) {
            // 提取关联的用户ID
            String userId = expiredKey.replace("chatMemory:userId:", "").split(":sessionId")[0];
            String sessionId = expiredKey.replace("chatMemory:userId:", "").split(":sessionId")[1];
            // TODO 需要更精细的删除策略
//            redisStoreUtils.deleteActivateSessionIdBy(Integer.valueOf(userId));

            System.out.println("键 " + expiredKey + " 过期");
        }
    }
}
