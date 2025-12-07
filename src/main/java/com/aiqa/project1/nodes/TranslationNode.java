package com.aiqa.project1.nodes;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TranslationNode implements Node{
    private final OpenAiChatModel douBaoLite;
    private final String TRANSLATION_TEMPLATE = """
                请将以下文本准确翻译成其他的语言，仅返回翻译结果，不添加任何额外内容：
                <文本>
                %s
                </文本>
                要求：
                1、如果是文本语言是中文，将其翻译为英文；
                2、如果是文本语言是英文，将其翻译为中文；
                现在，请按照要求翻译文本。
                """;
    public TranslationNode(OpenAiChatModel douBaoLite) {
        this.douBaoLite = douBaoLite;
    }


    @Override
    public State run(State state) {

        Integer userId = state.getUserId();
        ChatMemory chatMemory = state.getChatMemory();
        String query = state.getRetrievalQuery();

        String prompt = TRANSLATION_TEMPLATE.formatted(query);
        String translatedSentence = douBaoLite.chat(prompt);

        query = query + "(%s)".formatted(translatedSentence);

        chatMemory.add(UserMessage.from("用户ID: " + userId.toString(), query));

        state.setRetrievalQuery(query);

        return state;
    }
}
