//package com.aiqa.project1.service.impl;
//
//import com.aiqa.project1.nodes.AnswerNode;
//import com.aiqa.project1.nodes.Node;
//import com.aiqa.project1.nodes.IRQCNode;
//import dev.langchain4j.model.openai.OpenAiChatModel;
//import dev.langchain4j.rag.content.Content;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.concurrent.ExecutionException;
//import java.util.concurrent.Semaphore;
//import java.util.concurrent.TimeUnit;
//
//
//import java.util.List;
//import java.util.concurrent.Semaphore;
//
//@Service
//public class QuestionAnsweringService {
//    private static final String QUALITY_CHECK_TEMPLATE = """
//            你是多智能体系统中的回答质检节点，核心任务是判断生成的回答是否能完整回应用户的问题。请严格遵循以下步骤执行质检：
//
//            首先，明确质检对象：
//            <用户问题>
//            %s
//            </用户问题>
//
//            <待质检回答>
//            %s
//            </待质检回答>
//
//            接下来，依据以下标准完成判断：
//            1. 完整性：生成回答是否覆盖了用户问题的所有核心诉求？是否存在关键信息缺失？
//            2. 相关性：回答内容是否与用户问题直接相关？是否存在答非所问的情况？
//            3. 准确性：若问题涉及事实性内容，回答是否准确无误？
//
//            然后，按照以下规则输出结果：
//            - 如果生成回答完全满足上述标准，能够完整回答用户问题，请直接返回“无”
//            - 如果生成回答存在缺陷（如信息缺失、答非所问、准确性不足等），请清晰指出具体需要改进的地方，要求：
//            a. 明确指出问题类型（如“未回答用户关于XX的疑问”“遗漏了XX关键信息”）
//            b. 具体说明需要补充或修正的内容方向
//
//            请确保你的判断客观、精准，改进建议具有可操作性。无需额外解释，直接输出结果即可。
//            """;
//
//    private final Node rootNode;
//    private final AnswerNode answerNode;
//    private final OpenAiChatModel douBaoLite;
//    private final AppConfig appConfig;
//    private final Cache<String, List<Content>> retrievalCache;
//    private final Semaphore concurrencySemaphore;
//
//    public QuestionAnsweringService(IRQCNode rootNode, AnswerNode answerNode,
//                                    OpenAiChatModel douBaoLite, AppConfig appConfig) {
//        this.rootNode = rootNode;
//        this.answerNode = answerNode;
//        this.douBaoLite = douBaoLite;
//        this.appConfig = appConfig;
//
//        // 初始化缓存
//        this.retrievalCache = Caffeine.newBuilder()
//                .expireAfterWrite(appConfig.getCacheExpireMinutes(), TimeUnit.MINUTES)
//                .maximumSize(1000)
//                .build();
//
//        // 初始化并发控制信号量
//        this.concurrencySemaphore = new Semaphore(appConfig.getMaxConcurrentRequests());
//    }
//
//    public String answerQuestion(String query) {
//        return answerQuestion(0, query);
//    }
//
//    public String answerQuestion(Integer userId, String query) {
//        String chatMemory = "用户问题：" + query + "\n";
//        String retrievalInfo = "";
//        String finalAnswer = "";
//
//        try {
//            // 获取并发许可
//            concurrencySemaphore.acquire();
//
//            for (int i = 0; i < 3; i++) {
//                List<Content> contents;
//                try {
//                    // 尝试从缓存获取
//                    contents = retrievalCache.get(buildCacheKey(userId, query, chatMemory),
//                            k -> rootNode.run(userId, chatMemory, retrievalInfo, query));
//                } catch (ExecutionException e) {
//                    // 缓存获取失败时直接执行
//                    contents = rootNode.run(userId, chatMemory, retrievalInfo, query);
//                }
//
//                // 构建检索信息字符串
//                StringBuilder retrievalInfoBuilder = new StringBuilder();
//                for (Content content : contents) {
//                    if (content.metadata().isEmpty()) {
//                        retrievalInfoBuilder.append("数据库检索节点结果: ");
//                    } else {
//                        retrievalInfoBuilder.append("网络检索节点结果: ");
//                    }
//                    retrievalInfoBuilder.append(content.toString()).append("\n");
//                }
//                retrievalInfo = retrievalInfoBuilder.toString();
//
//                // 生成回答
//                List<Content> answerContents = answerNode.run(userId, chatMemory, retrievalInfo, query);
//                if (answerContents.isEmpty()) {
//                    finalAnswer = "无法生成有效的回答";
//                    break;
//                }
//                finalAnswer = answerContents.getFirst().textSegment().text();
//                System.out.println("生成的回答: " + finalAnswer);
//
//                // 更新对话历史
//                chatMemory += ("智能体回答:" + finalAnswer + "\n");
//
//                // 质检回答
//                String qualityCheckResult = douBaoLite.chat(QUALITY_CHECK_TEMPLATE.formatted(query, finalAnswer));
//
//                // 如果质检通过，结束循环
//                if ("无".equals(qualityCheckResult)) {
//                    break;
//                }
//
//                // 否则，将质检结果加入对话历史
//                chatMemory += String.format("""
//                        用户可能会有以下方面的需求，请根据此需求和问题重新回答：
//                        <需求>
//                        %s
//                        </需求>
//                        """, qualityCheckResult);
//            }
//
//            return finalAnswer;
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            return "处理请求时被中断: " + e.getMessage();
//        } catch (Exception e) {
//            return "处理请求时发生错误: " + e.getMessage();
//        } finally {
//            // 释放并发许可
//            concurrencySemaphore.release();
//        }
//    }
//
//    private String buildCacheKey(Integer userId, String query, String chatMemory) {
//        // 简单的缓存键生成策略，可根据实际需求调整
//        return userId + ":" + query.hashCode() + ":" + chatMemory.hashCode();
//    }
//}