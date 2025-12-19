package com.aiqa.project1.utils;

import com.google.common.hash.Hashing;
import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文本去重工具类
 */
@Component
public class TextDeduplicationUtil {

    // 哈希存储（精确去重）
    private static final Set<String> HASH_SET = ConcurrentHashMap.newKeySet();
    // 余弦相似度阈值（≥0.8视为重复）
    private static final double SIMILARITY_THRESHOLD = 0.8;

    // 嵌入模型（近似去重）
    private final EmbeddingModel embeddingModel;


    public TextDeduplicationUtil(@Qualifier("bgeM3") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    // 精确去重（MD5哈希）
    public static boolean isDuplicateByHash(String text) {
        String md5 = Hashing.md5().hashString(text, StandardCharsets.UTF_8).toString();
        return !HASH_SET.add(md5);
    }

    // 近似去重（余弦相似度）
    public boolean isDuplicateBySimilarity(String text, String targetText) {
        Embedding textEmbedding = embeddingModel.embed(text).content();
        Embedding targetEmbedding = embeddingModel.embed(targetText).content();
        // 计算余弦相似度
        double similarity = cosineSimilarity(textEmbedding.vector(), targetEmbedding.vector());
        return similarity >= SIMILARITY_THRESHOLD;
    }

    // 余弦相似度计算
    private static double cosineSimilarity(float[] vec1, float[] vec2) {
        double dotProduct = 0.0, norm1 = 0.0, norm2 = 0.0F;
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += (float) Math.pow(vec1[i], 2);
            norm2 += (float) Math.pow(vec2[i], 2);
        }
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}