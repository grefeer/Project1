package com.aiqa.project1.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingModelConfig {
    private static final String base_url="http://127.0.0.1:11434";
    private static final String model_name="qwen3-embedding-0.6b-q8";

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(base_url)
                .modelName(model_name)
                .httpClientBuilder(new SpringRestClientBuilder())
                .build();
    }


//    @Bean
//    public EmbeddingModel onnxBGEM3EmbeddingModel() {
//        PoolingMode poolingMode = PoolingMode.MEAN;
//        return new OnnxEmbeddingModel(
//                "C:\\Users\\Grefer\\.ollama\\models\\bge_m3_onnx\\model_quantized.onnx",
//                "C:\\Users\\Grefer\\.ollama\\models\\bge_m3_onnx\\tokenizer.json",
//                poolingMode);
//    }

    @Bean
    public EmbeddingModel onnxMiniLML12V2EmbeddingModel() {
        PoolingMode poolingMode = PoolingMode.MEAN;
        return new OnnxEmbeddingModel(
                "D:\\Docker\\Milvus\\paraphrase_multilingual_MiniLM_L12_v2\\model_fp16.onnx",
                "D:\\Docker\\Milvus\\paraphrase_multilingual_MiniLM_L12_v2\\tokenizer.json",
                poolingMode);
    }

}
