package com.aiqa.project1.config;

/**
 * 系统配置常量类
 */
public class SystemConfig {
    
    // RabbitMQ配置
    public static final String START_EXCHANGE = "Start";
    public static final String RETRIEVE_EXCHANGE = "Retrieve";
    public static final String GATHER_TOPIC = "gather.topic";
    public static final String ANSWER_TOPIC = "answer.topic";
    public static final String REFLECTION_DIRECT = "refection.direct";
    
    // 队列名称
    public static final String START_QUEUE = "start";
    public static final String REWRITE_ROUTE_QUEUE = "rewrite.route";
    public static final String GATHER_QUEUE = "gather";
    public static final String ANSWER_QUEUE = "answerWorker.queue";
    public static final String REFLECTION_QUEUE = "reflection";
    public static final String RESULT_QUEUE = "result";
    
    // 路由键
    public static final String START_KEY = "start";
    public static final String HAVE_PROBLEM_KEY = "have.problem";
    public static final String NEW_PROBLEM_KEY = "new.problem";
    public static final String HAVE_GATHERED_RETRIEVE_KEY = "have.gathered.retrieve";
    public static final String NO_PROBLEM_KEY = "no.problem";
    public static final String HAVE_PROBLEM_ROUTE_KEY = "have.problem";
    
    // 系统参数
    public static final int MAX_CHAT_HISTORY_SIZE = 10;
    public static final int MAX_REWRITE_HISTORY_SIZE = 5;
    public static final int MAX_RETRIEVAL_COUNT = 5;
    public static final int MAX_REFLECTION_COUNT = 3;
    public static final int MAX_QUERY_LENGTH = 100;
    public static final int CONSTANT_DIRECT_ANSWER_MODE=9527;

    // 延迟配置（毫秒）
    public static final long REDIS_SAVE_DELAY = 100;
    public static final long MESSAGE_SEND_DELAY = 50;
    
    // 超时配置（毫秒）
    public static final long AI_CHAT_TIMEOUT = 30000;
    public static final long REDIS_OPERATION_TIMEOUT = 5000;
    public static final long RABBITMQ_SEND_TIMEOUT = 10000;
    
    // 并发控制
    public static final int MAX_CONCURRENT_REQUESTS = 100;
    public static final int THREAD_POOL_SIZE = 10;
    
    // Redis连接池配置
    public static final int REDIS_POOL_MAX_TOTAL = 20;
    public static final int REDIS_POOL_MAX_IDLE = 10;
    public static final int REDIS_POOL_MIN_IDLE = 5;
    public static final int REDIS_POOL_TIMEOUT = 3000;
    public static final int REDIS_POOL_MAX_WAIT = 5000;
    public static final long REDIS_POOL_MIN_EVICTABLE_IDLE_TIME = 30000;
    public static final long REDIS_POOL_TIME_BETWEEN_EVICTION_RUNS = 60000;
    public static final int REDIS_POOL_NUM_TESTS_PER_EVICTION_RUN = 3;
    
    // Redis重试配置
    public static final int REDIS_MAX_RETRY_COUNT = 3;
    public static final long REDIS_RETRY_BASE_DELAY = 100;
    
    // Redis键格式
    public static final String REDIS_RETRIEVAL_KEY_FORMAT = "retrieval:userId:%d:sessionId:%d:memoryId:%d";
    public static final String REDIS_RETRIEVAL_COUNT_KEY_FORMAT = "retrievalCount:userId:%d:sessionId:%d:memoryId:%d:subtask:%s";
    public static final String REDIS_SUBTASKS_COUNT_KEY_FORMAT = "subtasksCount:userId:%d:sessionId:%d:memoryId:%d";


    // 消息过期时间
    public static final long MESSAGE_EXPIRATION_TIME = 60000; // 1分钟
    
    // 缓存配置
    public static final long CACHE_DEFAULT_EXPIRE_TIME = 1800000; // 30分钟
    public static final long CACHE_AI_CHAT_EXPIRE_TIME = 3600000; // 1小时
    public static final long CACHE_KEYWORDS_EXPIRE_TIME = 7200000; // 2小时
    public static final long CACHE_RETRIEVAL_EXPIRE_TIME = 1800000; // 30分钟
    public static final String CACHE_KEY_AI_CHAT_PREFIX = "cache:ai:chat";
    public static final String CACHE_KEY_KEYWORDS_PREFIX = "cache:keywords";
    public static final String CACHE_KEY_RETRIEVAL_PREFIX = "cache:retrieval";
    
    // 检索器配置
    public static final String RETRIEVER_MILVUS_FILTER = "milvus_filter";
    public static final String RETRIEVER_MILVUS_HYBRID = "milvus_hybrid";
    public static final String RETRIEVER_MILVUS_QUERY = "milvus_query";
    public static final String RETRIEVER_WEB_SEARCH = "web_search";
    public static final String RETRIEVER_DEFAULT = RETRIEVER_MILVUS_HYBRID;

    // ChatMemory配置
    public static final String CHAT_MEMORY = "chatMemory:userId:%s:sessionId:%s";
    public static final String USER_ACTIVATE_SESSION = "activateSession:userId:%s";
    public static final String SESSION_SUMMARY = "sessionSummary:userId:%s";
    public static final String LAST_SESSION_ID = "lastSessionId:userId:%s";

    public static final String DOCUMENT_PROCESS_STATUS = "documentProcessStatus:userId:%s:sessionId:%s";


}