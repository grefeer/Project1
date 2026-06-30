//package com.aiqa.project1.nodes;
//
//
//import com.aiqa.project1.utils.ChatMemoryManager;
//import com.aiqa.project1.utils.DataProcessUtils;
//import dev.langchain4j.rag.content.Content;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import java.io.File;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//
//@SpringBootTest
//public class StateGraphTest {
//    @Autowired
//    private  StateGraph stateGraph;
//    @Autowired
//    private  DataProcessUtils dataProcessUtils;
//    @Autowired
//    private ChatMemoryManager chatMemoryManager;
//
//    @Test
//    public void stateGraphTest() {
//        System.out.println(MilvusHybridRetrieveNode.class.getSimpleName());
//
//        List<Content> threadSafeRetrievalInfo = Collections.synchronizedList(new ArrayList<>());
////        State state = new State(
////                0,
////                chatMemoryManager.getChatMemory(0),
////                threadSafeRetrievalInfo,
////                "介绍一下2412.06695v3.pdf这篇论文主要讲了什么内容",
////                3,
////                ""
////        );
//        State state = new State(
//                0,
//                chatMemoryManager.getChatMemory(0),
//                threadSafeRetrievalInfo,
//                "介绍一下关于EEG检索文本的研究",
//                3,
//                1000,
//                ""
//        );
//        state = stateGraph.run(state);
//        System.out.println("----------------------------------");
//        state.getChatMemory().messages().forEach(System.out::println);
//        System.out.println("----------------------------------");
//        System.out.println(state.getChatMemory().messages().get(state.getChatMemory().messages().size() - 2));
//    }
//
//    @Test
//    public void dataProcessTest() {
//        File file = new File("C:\\Users\\Grefer\\IdeaProjects\\Project1\\src\\main\\resources\\2412.06695v3.pdf");
//        dataProcessUtils.DataProcess(0, file);
//    }
//}
