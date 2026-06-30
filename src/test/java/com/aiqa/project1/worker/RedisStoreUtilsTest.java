//package com.aiqa.project1.worker;
//
//import com.aiqa.project1.config.SystemConfig;
//import com.aiqa.project1.nodes.State;
//import com.aiqa.project1.utils.RedisStoreUtils;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//
//
//@SpringBootTest
//class RedisStoreUtilsTest {
//
//    @Autowired
//    private RedisStoreUtils redisStoreUtils;
//
//
//    @Test
//    @DisplayName("测试子任务计数减少逻辑")
//    void testDecreaseSubtaskCount() {
//        // 1. 准备数据
//        Integer userId = 1, sessionId = 100, memoryId = 200;
//        String key = SystemConfig.REDIS_SUBTASKS_COUNT_KEY_FORMAT.formatted(userId, sessionId, memoryId);
//
//        // 2. 执行方法
//        redisStoreUtils.putSubtaskCount(userId, sessionId, memoryId,3);
//        Integer subtaskCount = redisStoreUtils.getSubtaskCount(userId, sessionId, memoryId);
//        assertEquals(3, subtaskCount);
//
//        Long result = redisStoreUtils.decreaseSubtaskCount(userId, sessionId, memoryId);
//
//
//        assertEquals(2L, result);
//    }
//
//    @Test
//    @DisplayName("测试意图识别为[直接回答]时的路由逻辑")
//    void testRouterDirectAnswer() {
//        // 1. 模拟输入状态
//        State state = new State();
//        state.setUserId(1);
//        state.setSessionId(100);
//        state.setMemoryId(200);
//        state.setQuery("你好，请问你是谁？");
//
//
//
//    }
//}
