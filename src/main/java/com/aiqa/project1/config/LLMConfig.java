package com.aiqa.project1.config;

import com.aiqa.project1.nodes.StateGraph1;
import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LLMConfig {
    @Bean
    OpenAiChatModel deepSeek() {
        return OpenAiChatModel.builder()
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .baseUrl("https://api.deepseek.com")
                .modelName("deepseek-chat")
                .logRequests(true)
                .logResponses(true)
                .httpClientBuilder(new SpringRestClientBuilder())
                .build();
    }

    @Bean
    OpenAiChatModel douBaoLite() {
        return OpenAiChatModel.builder()
                .apiKey(System.getenv("DOUBAO_API_KEY"))
                .baseUrl("https://ark.cn-beijing.volces.com/api/v3")
                .modelName("doubao-1-5-lite-32k-250115")
                .logRequests(true)
                .logResponses(true)
                .httpClientBuilder(new SpringRestClientBuilder())
                .build();
    }


    @Bean
    OllamaChatModel qwen2_5Instruct() {
        return OllamaChatModel.builder()
                .baseUrl("http://127.0.0.1:11434")
                .modelName("qwen2.5:0.5b-instruct")
                .httpClientBuilder(new SpringRestClientBuilder())
                .build();
    }

    @Bean
    OllamaChatModel gemma3_270m() {
        return OllamaChatModel.builder()
                .baseUrl("http://127.0.0.1:11434")
                .modelName("gemma3:270m")
                .httpClientBuilder(new SpringRestClientBuilder())
                .build();
    }

}

