//package com.aiqa.project1.preprocess;
//
//
//import dev.langchain4j.data.document.DocumentParser;
//import dev.langchain4j.data.document.DocumentSplitter;
//import dev.langchain4j.data.document.Metadata;
//import dev.langchain4j.data.document.parser.TextDocumentParser;
//import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
//import dev.langchain4j.data.document.splitter.DocumentSplitters;
//import dev.langchain4j.data.segment.TextSegment;
//import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
//import dev.langchain4j.model.embedding.EmbeddingModel;
//import dev.langchain4j.model.ollama.OllamaChatModel;
//import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
//import dev.langchain4j.model.ollama.OllamaModel;
//import org.junit.jupiter.api.Test;
//import dev.langchain4j.data.document.Document;
//
//import java.io.InputStream;
//import java.util.List;
//
//public class DocumentSpliterTest {
//
//    DocumentSplitter spliter = DocumentSplitters.recursive(
//            200,
//            20
//    );
//    String base_url="http://127.0.0.1:11434";
//    String model_name="qwen2.5:0.5b-instruct";
//
//
//    OllamaChatModel model = OllamaChatModel.builder()
//            .baseUrl(base_url)
//            .modelName(model_name)
//            .httpClientBuilder(new JdkHttpClientBuilder())
//            .build();
//
//
//
//    @Test
//    public void test() {
//        DocumentParser parser = new ApacheTikaDocumentParser();
//        InputStream inputStream = DocumentSpliterTest.class.getClassLoader().getResourceAsStream("2106.09685v2.pdf");
//
//        Document document = parser.parse(inputStream);
//
//        List<TextSegment> textSegmentList= spliter.split(document);
//        textSegmentList.forEach(System.out::println);
//
////        String question = "请翻译以下句子：" + textSegmentList.get(5).text();
////        String answer = model.chat(question);
////        System.out.println(question + "\n" + answer);
//    }
//
//
//}
