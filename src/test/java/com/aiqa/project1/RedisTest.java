package com.aiqa.project1;

import com.aiqa.project1.utils.RedisStoreUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
public class RedisTest {
    @Autowired
    private RedisStoreUtils redisStoreUtils;

    @Test
    public void test() {
        redisStoreUtils.putRetrievalCount(0,0,3);
        redisStoreUtils.putRetrievalCount(0,1,3);
        redisStoreUtils.decreaseRetrievalCount(0,1);

        System.out.println(redisStoreUtils.getRetrievalCount(0, 0));
        System.out.println(redisStoreUtils.getRetrievalCount(0, 1));
    }
}
