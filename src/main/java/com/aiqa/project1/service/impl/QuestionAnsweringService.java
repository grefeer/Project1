package com.aiqa.project1.service.impl;


import com.aiqa.project1.mapper.SessionChatMapper;
import com.aiqa.project1.mapper.UserChatMemoryMapper;
import com.aiqa.project1.nodes.*;
import com.aiqa.project1.pojo.ResponseCode;
import com.aiqa.project1.pojo.Result;
import com.aiqa.project1.pojo.qa.SessionChat;
import com.aiqa.project1.pojo.qa.UserChatMemory;
import com.aiqa.project1.utils.CacheAsideUtils;
import com.aiqa.project1.utils.RedisStoreUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.state.AgentState;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Slf4j
@Service
public class QuestionAnsweringService {
    private final RedisStoreUtils redisStoreUtils;
    private final UserChatMemoryMapper userChatMemoryMapper;
    private final CacheAsideUtils cacheAsideUtils;
    private final OpenAiChatModel douBaoLite;
    private final NaiveRAGGraph naiveRAGGraph;
    private final SubQueryRAGGraph subQueryRAGGraph;

    @Autowired
    private ApplicationContext applicationContext;

    List<ToolMetaData1> toolMetaDataList = new ArrayList<>();

    CompiledGraph<NaiveRAGState> naiveRagStateCompiledGraph;
    CompiledGraph<SubQueryRAGState> subQueryRAGStateCompiledGraph;
    CompiledGraph<AgenticRAGState> agenticRagStateCompiledGraph;

    private static final String SESSION_SUMMARY_TEMPLATE = """
    你的任务是根据提供的对话内容，总结生成一个精准、简洁且具有概括性的标题。
    首先，请阅读以下对话内容：
    <对话内容>
    %s
    </对话内容>
    在生成标题时，请遵循以下要求：
    1. 标题需准确概括对话的核心主题或主要事件，避免遗漏关键信息
    2. 语言简洁明了，避免使用冗长复杂的表述
    3. 标题长度控制在10字之内，不能超过10个字
    4. 只生成标题，不要输出其余内容
    现在，请输出标题：
    """;
    private final SessionChatMapper sessionChatMapper;
    private final AgenticRAGGraph agenticRAGGraph;

    public QuestionAnsweringService( RedisStoreUtils redisStoreUtils, UserChatMemoryMapper userChatMemoryMapper, CacheAsideUtils cacheAsideUtils, OpenAiChatModel douBaoLite, SessionChatMapper sessionChatMapper, AgenticRAGGraph agenticRAGGraph, NaiveRAGGraph naiveRAGGraph, SubQueryRAGGraph subQueryRAGGraph) throws GraphStateException {
        this.redisStoreUtils = redisStoreUtils;
        this.userChatMemoryMapper = userChatMemoryMapper;
        this.cacheAsideUtils = cacheAsideUtils;
        this.douBaoLite = douBaoLite;
        this.sessionChatMapper = sessionChatMapper;
        this.agenticRAGGraph = agenticRAGGraph;

        this.toolMetaDataList.add(ToolMetaData1.of(
                "local_hybrid_retriever",
                "包含项目文档、技术手册和知识库的本地混合检索工具"
        ));
        this.naiveRAGGraph = naiveRAGGraph;
        this.subQueryRAGGraph = subQueryRAGGraph;

        this.naiveRagStateCompiledGraph = naiveRAGGraph.buildGraph();
        this.subQueryRAGStateCompiledGraph = subQueryRAGGraph.buildGraph();
        this.agenticRagStateCompiledGraph = agenticRAGGraph.buildGraph();

        Assert.notNull(naiveRagStateCompiledGraph, "buildGraph()返回null！请检查NaiveRAGGraph的构建逻辑");
        Assert.notNull(subQueryRAGStateCompiledGraph, "buildGraph()返回null！请检查SubQueryRAGGraph的构建逻辑");
        Assert.notNull(agenticRagStateCompiledGraph, "buildGraph()返回null！请检查AgenticRagGraph的构建逻辑");
    }

    /**
     * 将问题交给多智能体回答
     * @param userId
     * @param sessionId
     * @param memoryId
     * @param query
     */
    @Async("qaTaskExecutor")
    public void answerQuestion(Integer userId, Integer sessionId, Integer memoryId, String query, Integer ragMode) {
//        stateProducer.run(userId, sessionId, memoryId, query);
        // 1. 关键校验：检查naiveRAGGraph是否成功注入（最容易被忽略的点）
        Assert.notNull(agenticRAGGraph, "naiveRAGGraph未成功注入！请检查Spring配置或NaiveRAGGraph的@Component注解");

//        CompiledGraph<AgenticRAGState> ragStateCompiledGraph = agenticRAGGraph.buildGraph();
//        // 校验CompiledGraph是否为null
//        Assert.notNull(ragStateCompiledGraph, "buildGraph()返回null！请检查NaiveRAGGraph的构建逻辑");

        Map<String, Object> initialStateData = new HashMap<>();
        initialStateData.put("query", query);          // 用户查询
        initialStateData.put("user_id", userId);            // 用户ID
        initialStateData.put("session_id", sessionId);      // 会话ID
        initialStateData.put("memory_id", memoryId);        // 记忆ID
        initialStateData.put("tool_metadata_list", toolMetaDataList);

        // 2. 检查initialStateData中是否有null值（框架可能不允许字段值为null）
        for (Map.Entry<String, Object> entry : initialStateData.entrySet()) {
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("初始状态数据中键[" + entry.getKey() + "]的值为null！");
            }
        }
        redisStoreUtils.setChatMemory(userId,sessionId, "<用户问题>" + query);
        // 3. 执行图（增加异常捕获，避免直接get()掩盖真实错误）
        if (ragMode == null) {
            agenticRagStateCompiledGraph.invoke(initialStateData);
        } else if (ragMode == 1) {
            naiveRagStateCompiledGraph.invoke(initialStateData);
        } else if (ragMode == 2) {
            subQueryRAGStateCompiledGraph.invoke(initialStateData);
        } else if (ragMode == 3) {
            agenticRagStateCompiledGraph.invoke(initialStateData);
        } else {
            agenticRagStateCompiledGraph.invoke(initialStateData);
        }

//        redisStoreUtils.setChatMemory(userId,sessionId, "<用户问题>" + query);
//        // 3. 执行图（增加异常捕获，避免直接get()掩盖真实错误）
//        AsyncGenerator<?> asyncGenerator;
//        if (ragMode == null) {
//            asyncGenerator = agenticRagStateCompiledGraph.stream(initialStateData);
//        } else if (ragMode == 1) {
//            asyncGenerator = naiveRagStateCompiledGraph.stream(initialStateData);
//        } else if (ragMode == 2) {
//            asyncGenerator = subQueryRAGStateCompiledGraph.stream(initialStateData);
//        } else if (ragMode == 3) {
//            asyncGenerator = agenticRagStateCompiledGraph.stream(initialStateData);
//        } else {
//            asyncGenerator = agenticRagStateCompiledGraph.stream(initialStateData);
//        }
//
//        asyncGenerator.forEachAsync(async -> {}).exceptionally(ex -> {
//            // 处理异常
//            System.err.println("流式执行异常：" + ex.getMessage());
//            return null;
//        });


//        Optional<AgenticRAGState> future = ragStateCompiledGraph.invoke(initialStateData);
//        AgenticRAGState finalState = future.get();
//
//        // 4. 提取最终回答（增加空值校验）
//        String answer = finalState.getAnswer();
//        Assert.notNull(answer, "最终回答为null！请检查RAG流程是否正常生成回答");
//        System.out.println("最终回答：" + answer);

    }

    /**
     * 根据sessionId，userId以及memoryId获取思考过程以及回答
     * @param sessionId
     * @param userId
     * @param memoryId
     * @return
     */
    @NotNull
    public Result getAnswerStatus(Integer sessionId, Integer userId, Integer memoryId) {
        // 判断ai是否更新了思考过程或者回答
        Integer currentChatMemoryCount = Math.toIntExact(cacheAsideUtils.getChatMemoryCount(userId, sessionId));
        if (currentChatMemoryCount < memoryId.longValue()) {
            System.out.println(currentChatMemoryCount + "::" + memoryId);
            return Result.define(202, "AI 正在思考中...", null);
        } else {
            int count = currentChatMemoryCount - memoryId;
            if (count <= 0) return Result.define(202, "AI 正在思考中...", null); // 至少获取当前最新的一条

            System.out.println(currentChatMemoryCount + ":" + memoryId);
            // 从 Redis 获取当前最新的回答内容

            List<String> currentAnswer = redisStoreUtils.getChatMemory(
                    userId,
                    sessionId,
                    count)
                    .stream()
                    .map(Object::toString)
                    .toList();

            Map<String, Object> data = new HashMap<>();
            data.put("answer", currentAnswer);
            data.put("sessionId", sessionId);
            data.put("currentChatMemoryCount", currentChatMemoryCount);
            data.put("memoryIds",
                    IntStream.rangeClosed(memoryId + 1, currentChatMemoryCount)
                            .boxed()
                            .collect(Collectors.toList()));

            if (currentAnswer == null) {
                return Result.define(202, "AI 正在思考中...", data);
            } else if (currentAnswer.getLast().contains("<最终回答>")) {
                // 状态：已完成
                return Result.define(200, "回答完毕", data);
            } else {
                return Result.define(206, "正在回答", data);
            }
        }
    }

    @NotNull
    public Result queryRegister(Integer userId, Integer sessionId, String question, Integer ragMode) {
        try {
            Map<String, Object> data = new HashMap<>();

            Integer memoryId= Math.toIntExact(cacheAsideUtils.getChatMemoryCount(userId, sessionId));

            QuestionAnsweringService proxy = applicationContext.getBean(QuestionAnsweringService.class);
            proxy.answerQuestion(userId, sessionId, memoryId, question, ragMode);
//            if (! tasksMap.containsKey(taskKey)) {
//                // 2. 定义数据检查任务逻辑
//                Runnable dataCheckTask = () -> {
//                    List<?> chatMemoryList = redisStoreUtils.getAllChatMemory(userId, sessionId);
//                    if (chatMemoryList == null || chatMemoryList.isEmpty()) return;
//
//                    // 根据redis更新mysql
//                    userChatMemoryMapper.insertOrUpdate(
//                            chatMemoryList.stream()
//                                    .map(o -> new UserChatMemory(userId, sessionId, memoryId, (String) o))
//                                    .toList());
//                };
//
//                ScheduledFuture<?> dataCheckFuture = scheduler.scheduleAtFixedRate(
//                        dataCheckTask,
//                        Instant.now(),  // 初始延迟（毫秒）
//                        Duration.ofSeconds(30)   // 执行周期（毫秒）
//                );
//                tasksMap.put(taskKey, dataCheckFuture);
//            }
            data.put("userId", userId);
            data.put("sessionId", sessionId);
            data.put("memoryId", memoryId);
            System.out.println("-----\n" + data);
            return Result.define(200, "任务已提交", data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
        }
    }

    /**
     * 从MySQL中读取sessionId会话并返回给前端
     * @param userId
     * @return
     */
    @NotNull
    public Result getChatMemoryBySessionId(Integer userId, Integer sessionId) {
        try {
            // 获取user的所有session的chat memory
            QueryWrapper<UserChatMemory> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId).eq("session_id", sessionId);
            List<UserChatMemory> userChatMemoryList = userChatMemoryMapper.selectList(queryWrapper);

            // 过滤注销以及异常对话
            userChatMemoryList = userChatMemoryList.stream().filter(UserChatMemory::checkStatus).toList();

            userChatMemoryList.forEach(userChatMemory -> {
                // 如果对话状态为已过期，从MySQL中加载对话
                if ( userChatMemory.getStatus() == 1) {
                    redisStoreUtils.setChatMemory(userId, userChatMemory.getSessionId(), userChatMemory.getContent());
                    userChatMemory.setStatus(0);
                    userChatMemoryMapper.updateById(userChatMemory);
                }
                // 判断未过期的Status是否真的未过期
                if ( userChatMemory.getStatus() == 0) {
                    LocalDateTime userChatMemoryLastActiveTime = userChatMemory.getLastActiveTime();
                    // 过期
                    if (LocalDateTime.now().isAfter(userChatMemoryLastActiveTime.plusHours(1))) {
                        redisStoreUtils.setChatMemory(userId, userChatMemory.getSessionId(), userChatMemory.getContent());
                    }
                }
            });
            return Result.define(200, "任务已提交", userChatMemoryList);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
        }
    }

    /**
     * 从Redis或者MySQL中读取session的整体内容并返回
     * @param userId
     * @return
     */
    @NotNull
    public Result getChatMemory(Integer userId) {
        try {
            Map<String, String> userChatMemoryList = cacheAsideUtils.getAllSessionChat(userId);
            return Result.define(200, "任务已提交", userChatMemoryList);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
        }
    }

//    /**
//     * 从Redis或者MySQL中创建session的整体内容
//     * @param userId
//     * @return
//     */
//    @NotNull
//    public Result createSession(Integer userId) {
//        try {
//            QueryWrapper<SessionChat> queryWrapper = new QueryWrapper<>();
//            QueryWrapper<UserChatMemory> queryWrapper_ = new QueryWrapper<>();
//
//            queryWrapper.eq("user_id", userId)
//                    .select("max(session_id) as session_id");
//            SessionChat sessionChat = sessionChatMapper.selectOne(queryWrapper);
//            Integer maxSessionId;
//            List<UserChatMemory> userChatMemories = null;
//            if(sessionChat != null) {
//                maxSessionId = sessionChat.getSessionId();
//                queryWrapper_.eq("user_id", userId)
//                        .eq("session_id", sessionChat.getSessionId());
//                userChatMemories = userChatMemoryMapper.selectList(queryWrapper_);
//            } else {
//                maxSessionId = 1;
//            }
//
//
//
//            if(!(userChatMemories == null || userChatMemories.isEmpty())) {
//                maxSessionId += 1;
//            }
//            String result = cacheAsideUtils.getSessionChat(userId, maxSessionId);
//            if (result != null) {
//                return Result.define(200, "会话已存在", Map.of("sessionName", result, "sessionId", maxSessionId));
//            } else {
//                cacheAsideUtils.setSessionChat(userId, maxSessionId, "新会话" + maxSessionId);
//                return Result.define(200, "任务已提交", Map.of("sessionName", "新会话" + maxSessionId, "sessionId", maxSessionId));
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
//        }
//    }
    /**
     * 从 Redis 优先创建/获取 session
     * @param userId 用户ID
     */
    @NotNull
    public Result createSession(Integer userId) {
        try {
            // 1. 获取当前最大 SessionId (Redis 优先)
            Integer currentMaxId = cacheAsideUtils.getOrSyncMaxSessionId(userId);

            // 2. 检查该会话是否已有对话内容
            // 利用已有的逻辑：如果 redis/mysql 都没有内容，count 为 0
            Long memoryCount = cacheAsideUtils.getChatMemoryCount(userId, currentMaxId);

            // 3. 策略判定
            if (currentMaxId > 0 && memoryCount == 0) {
                // 情况 A: 存在一个从未发送过消息的空会话，直接复用
                String sessionName = cacheAsideUtils.getSessionChat(userId, currentMaxId);
                log.info("用户 {} 复用空会话: {}", userId, currentMaxId);
                return Result.define(200, "复用空会话",
                        Map.of("sessionName", sessionName, "sessionId", currentMaxId));
            } else {
                // 情况 B: 当前没有会话，或最后的会话已有内容，需创建新 ID
                int nextSessionId = currentMaxId + 1;
                String newName = "新会话" + nextSessionId;

                // 执行同步保存逻辑，这里收藏默认为false
                cacheAsideUtils.saveNewSession(userId, nextSessionId, newName, false);

                log.info("用户 {} 创建新会话: {}", userId, nextSessionId);
                return Result.define(200, "创建新会话成功",
                        Map.of("sessionName", newName, "sessionId", nextSessionId));
            }
        } catch (Exception e) {
            log.error("创建会话异常", e);
            return Result.define(ResponseCode.SERVER_ERROR.getCode(),
                    ResponseCode.SERVER_ERROR.getMessage(), null);
        }
    }

    /**
     * 给session重命名
     * @param userId
     * @return
     */
    @NotNull
    public Result reNameChatMemoryBySessionId(Integer userId, Integer sessionId, String newName) {
        try {
            if (newName != null && !newName.isEmpty()) {
                cacheAsideUtils.setSessionChat(userId, sessionId, newName);
            } else {
                String chatMemory = cacheAsideUtils.getChatMemory(userId, sessionId).stream().collect(Collectors.joining("\n"));
                String prompt = SESSION_SUMMARY_TEMPLATE.formatted(chatMemory);
                newName = douBaoLite.chat(prompt);
            }
            cacheAsideUtils.setSessionChat(userId, sessionId, newName);
            return Result.define(200, "任务已提交", newName);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
        }
    }

    public Result deleteChatMemory(Integer userId, Integer sessionId, List<Long> memoryIds) {
        try {
            log.info("执行chat删除操作");
            memoryIds.forEach(memoryId -> cacheAsideUtils.deleteChatMemory(userId, sessionId, Math.toIntExact(memoryId)));
            return Result.define(200, "删除成功", null);
        } catch (Exception e){
            e.printStackTrace();
            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
        }
    }

    public Result deleteChatMemoryByMemoryId(Integer userId, Integer sessionId, Long memoryId) {
        try {
            cacheAsideUtils.deleteChatMemory(userId, sessionId, Math.toIntExact(memoryId));
            return Result.define(200, "删除成功", null);
        } catch (Exception e){
            e.printStackTrace();
            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
        }
    }

    public Result setChatFavorite(Integer userId, Integer sessionId) {
        sessionChatMapper.updateFavoritesByUserIdAndSessionId(userId,sessionId, 1);
        return Result.define(200, "喜好设置成功", null);
    }

    public Result resetChatFavorite(Integer userId, Integer sessionId) {
        try {
            sessionChatMapper.updateFavoritesByUserIdAndSessionId(userId,sessionId, 0);
            return Result.define(200, "对话收藏成功", null);
        } catch (Exception e) {
            return Result.error("对话收藏失败", null);
        }
    }


//    public Result deleteChatMemory1(Integer userId, Integer sessionId, String queries) {
//        try {
//            String[] queryStrings = queries.split("<问答分隔符>");
//            if (queryStrings.length != 2)
//                return Result.define(404, "格式错误", null);
//            String startQuery = queryStrings[0];
//            String endQuery = queryStrings[1];
//            LambdaQueryWrapper<UserChatMemory> lambdaWrapper = new LambdaQueryWrapper<>();
//            lambdaWrapper
//                    .select(UserChatMemory::getMemoryId)
//                    .in(UserChatMemory::getContent, startQuery, endQuery);
//
//            List<Integer> userChatMemories = userChatMemoryMapper
//                    .selectList(lambdaWrapper)
//                    .stream()
//                    .map(UserChatMemory::getMemoryId)
//                    .toList();
//
//            // 2. 删除这个 ID 区间内的所有记录
//            LambdaQueryWrapper<UserChatMemory> deleteWrapper = new LambdaQueryWrapper<>();
//            deleteWrapper.ge(UserChatMemory::getMemoryId, userChatMemories.getFirst()) // 大于等于起始ID
//                    .le(UserChatMemory::getMemoryId, userChatMemories.getLast()); // 小于等于结束ID
//
//            userChatMemoryMapper.delete(deleteWrapper);
//
//
//            return Result.define(200, "删除成功", null);
//        } catch (Exception e){
//            e.printStackTrace();
//            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
//        }
//    }

}
