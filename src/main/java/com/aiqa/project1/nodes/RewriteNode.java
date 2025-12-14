package com.aiqa.project1.nodes;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class RewriteNode implements Node {
    private final OpenAiChatModel douBaoLite;
    private static final String QUERY_REWRITING_TEMPLATE = """
            你的任务是根据提供的历史对话和后续用户查询，生成一个简洁、独立的综合查询。该查询需完整融入历史对话的上下文信息，清晰保持用户的原始意图，且无需依赖额外背景即可理解。
            首先，请阅读以下历史对话：
            <历史对话>
            %s
            </历史对话>
            接下来，请阅读用户的后续查询：
            <用户查询>
            %s
            </用户查询>
            请严格按照以下要求生成综合查询：
            1. 必须包含历史对话中的关键上下文信息
            2. 必须准确反映用户的核心需求和意图
            3. 表述需清晰、具体，避免模糊
            4. 生成的查询需独立完整，无需参考原始对话即可理解
            5. 保持简洁，避免冗余信息，必须少于100词
            至关重要的是，你只需输出重新表述后的独立查询文本，不得添加任何额外说明、解释或格式标记！
            """;

    public RewriteNode(OpenAiChatModel douBaoLite) {
        this.douBaoLite = douBaoLite;
    }

    @Override
    public State run(State state) {
        // 问题重写
        String chatHistory = state.getChatMemory().messages().stream().map(Object::toString).collect(Collectors.joining("\n"));

        String prompt = QUERY_REWRITING_TEMPLATE.formatted(chatHistory, state.getQuery());
        state.setRetrievalQuery(douBaoLite.chat(prompt));
        return state;
    }
}
