//package com.aiqa.project1.chat;
//
//import dev.langchain4j.data.message.*;
//import dev.langchain4j.http.client.jdk.JdkHttpClientBuilderFactory;
//import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
//import dev.langchain4j.model.chat.response.ChatResponse;
//import dev.langchain4j.model.embedding.EmbeddingModel;
//import dev.langchain4j.model.openai.OpenAiChatModel;
//
//import dev.langchain4j.model.output.Response;
//
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import java.util.ArrayList;
//import java.util.List;
//
//@SpringBootTest
//public class ChatTest {
//
//    @Autowired
//    private OpenAiChatModel douBaoLite;
//
//    @Test
//    public void testChat() {
//
//        System.out.println(System.getenv("DOUBAO_API_KEY"));
//
//        List<ChatMessage> chatMessages = new ArrayList<>();
//
//        List<Content> contents = new ArrayList<>();
//        contents.add(TextContent.from("用户消息"));
//
//        UserMessage userMessage = UserMessage.from("用户1",contents);
//        SystemMessage systemMessage = SystemMessage.from("你是一个AI助手");
//
//        chatMessages.add(systemMessage);
//        chatMessages.add(userMessage);
//
//        ChatResponse response = douBaoLite.chat(chatMessages);
//        System.out.println(response);
//
//        /*
//                  ChatResponse {
//                      aiMessage = AiMessage {
//                          text = "你好！我是你的AI助手，随时准备为你提供帮助。无论是回答问题、" +
//                                  "协助解决问题，还是进行创意讨论，我都可以为你服务。请告诉我你需要什么帮助吧！ 😊"
//                          toolExecutionRequests = null
//                      },
//                      metadata = OpenAiChatResponseMetadata{
//                          id='6d87cd80-7700-4649-84c0-1e91bc92064c',
//                          modelName='deepseek-chat',
//                          tokenUsage=OpenAiTokenUsage{
//                              inputTokenCount = 10,
//                              inputTokensDetails=InputTokensDetails[cachedTokens=0],
//                              outputTokenCount = 38,
//                              outputTokensDetails=null,
//                              totalTokenCount = 48
//                          },
//                          finishReason=STOP,
//                          created=1765186237,
//                          serviceTier='null',
//                          systemFingerprint='fp_eaab8d114b_prod0820_fp8_kvcache'
//                      }
//                  }
//         */
//
//    }
//}
