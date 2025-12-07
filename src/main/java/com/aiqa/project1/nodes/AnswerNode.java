package com.aiqa.project1.nodes;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.internal.chat.AssistantMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Component
public class AnswerNode implements Node{

    private final OpenAiChatModel douBaoLite;
    private static final String ANSWER_TEMPLATE = """
                你是多智能体系统的问答节点，核心任务是结合历史对话上下文与检索信息，准确全面回答用户当前问题。
                
                首先，阅读以下历史对话，理解背景及用户此前交互：
                <历史对话>
                %s
                </历史对话>
                
                接下来，查看回答当前问题的检索信息：
                <检索信息>
                %s
                </检索信息>
                
                当前用户问题：
                <用户查询>
                %s
                </用户查询>
                
                回答规则：
                1. 结合历史上下文逻辑，确保回答与之前交互连贯一致
                2. 回答内容需基于检索信息，禁止编造未提及内容
                3. 若无直接相关检索信息，明确告知无法回答
                4. 回答简洁明了，保证信息完整准确
                
                现在，请开始回答。
                """;

    public AnswerNode(OpenAiChatModel douBaoLite) {
        this.douBaoLite = douBaoLite;
    }

    public State run(State state) {
        ChatMemory chatMemory = state.getChatMemory();
        List<Content> retrievalInfo = state.getRetrievalInfo();
        String chatHistory = chatMemory.messages().stream().map(Object::toString).collect(Collectors.joining("\n"));
        String retrievalInfoText = retrievalInfo
                .stream()
                .map(content -> content.metadata().isEmpty() ? "网络检索节点结果: " + content.toString(): "数据库检索节点结果: " + content.toString())
                .distinct()
                .collect(Collectors.joining("\n"));

        String query = state.getQuery();

        String prompt = ANSWER_TEMPLATE.formatted(chatHistory, retrievalInfoText, query);
        chatMemory.add(AiMessage.from(douBaoLite.chat(prompt)));

        return state;
    }
}
