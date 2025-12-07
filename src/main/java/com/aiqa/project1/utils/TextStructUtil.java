package com.aiqa.project1.utils;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 文本结构化/向量化工具类
 */
@Component
public class TextStructUtil {
    private final EmbeddingModel onnxMiniLML12V2EmbeddingModel;
    private final OpenAiChatModel douBaoLite;
    private final OllamaChatModel qwen2_5Instruct;
    private final OllamaChatModel gemma3_270m;

    // 文本分块配置
    @Value("${langchain4j.TextConfig.CHUNK_SIZE}")
    private int CHUNK_SIZE;
    @Value("${langchain4j.TextConfig.CHUNK_OVERLAP}")
    private int CHUNK_OVERLAP;

    public TextStructUtil(EmbeddingModel onnxMiniLML12V2EmbeddingModel, OpenAiChatModel douBaoLite, OllamaChatModel qwen2_5Instruct, OllamaChatModel gemma3_270m) {
        this.onnxMiniLML12V2EmbeddingModel = onnxMiniLML12V2EmbeddingModel;
        this.douBaoLite = douBaoLite;
        this.qwen2_5Instruct = qwen2_5Instruct;
        this.gemma3_270m = gemma3_270m;
    }

    // 长文本分块（适配大模型/检索）
    public List<TextSegment> splitText(Document document) {
        return DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP).split(document);
    }

    // 生成文本向量（适配Milvus）
    public Embedding generateVector(String text) {
        return onnxMiniLML12V2EmbeddingModel.embed(text).content();
    }

    // 提取关键词
    public String extractKeywords(String text, String modelName) {
        // 简化实现：基于分词+词频，可替换为LangChain4j调用LLM提取
        // 示例：return llmClient.extractKeywords(text);

        String temple = """
                # 变量定义
                原始文本：%s
              
                你现在是文本元信息提取专员，服务于通用信息处理场景，核心职责是精准识别并提取文本中的标题、作者名、时间三类关键元信息。
                
                ## 执行准则
                ### 必须遵守
                1. 仅提取标题、作者名、时间三类信息，不得遗漏或新增其他字段
                2. 若文本中某类信息缺失，对应字段标注为"无"
                3. 时间信息需转换为"YYYY-MM-DD"格式（如"2023年5月1日"→"2023-05-01"）
                
                ### 禁止操作
                1. 禁止修改原始文本内容
                2. 禁止主观推断未明确提及的信息
                3. 禁止混淆不同类型信息（如将作者名填入标题字段）
                
                ## 文本解析流程
                1. 通读原始文本，标记所有可能包含目标信息的段落
                2. 按标题→作者名→时间顺序逐一提取：
                   - 标题：优先选择文本开头加粗/大字体内容，无明显标识时选择首段核心概括句
                   - 作者名：寻找"作者""文/""by"等标识后的署名
                   - 时间：识别包含年月日的完整时间表述
                3. 验证提取结果：确认三类信息无交叉重叠，时间格式符合要求
                
                ## 输出规范
                采用JSON格式输出，包含以下三个字段：
                - title：文本标题（字符串）
                - author：作者姓名（字符串）
                - date：发布时间（格式：YYYY-MM-DD，无则为"无"）
                
                示例输出：
                {
                  "title": "人工智能发展趋势报告",
                  "author": "张明",
                  "date": "2023-10-15"
                }
                
                开始处理：<原始文本>%s</原始文本>
                
                """;
        String prompt = temple.formatted(text, text);

        String responses = "";
        try {
//            responses = deepSeek.chat(prompt);
            System.out.println("--------------------------------------");
            if (modelName.contains("qwen"))
                responses = qwen2_5Instruct.chat(prompt);
            else if (modelName.contains("gemma"))
                responses = gemma3_270m.chat(prompt);
            else
                responses = douBaoLite.chat(prompt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return responses; // 实际需替换为真实逻辑
    }
//    public String extractKeywords(String text) {
//        // 简化实现：基于分词+词频，可替换为LangChain4j调用LLM提取
//        // 示例：return llmClient.extractKeywords(text);
//
//        String prompt = "请从以下文本中提取核心关键词，要求如下：\n" +
//                "1. 关键词为名词或名词短语，优先保留文本中高频出现、语义核心的概念；\n" +
//                "2. 数量控制在 5-10 个，避免过于宽泛（如 “技术”“方法”）或过于具体的词汇（如具体版本号）；\n" +
//                "3. 按重要性降序排列，若有同义词汇，合并为最通用的表达；\n" +
//                "4. 输出格式：仅列出关键词，关键词用英文单引号括起来，并用英文逗号分隔,禁止生成其他无关内容;\n" +
//                "5. 注意：生成关键字的语言必须和文本的相同，字符必须用英文字符，比如英文文本提取出的关键字必须是英文，中文文本提取出的关键字必须是中文，中英混合则可以是中文或英文" +
//                "文本内容：" + text +
//                "比如：文本内容：轻量化智能水杯支持蓝牙连接手机 APP，可实时监测饮水量、水温，内置锂电池续航长达 7 天，还能通过语音提醒用户按时喝水，适配 iOS 和 Android 系统。”\n" +
//                "输出结果\n" +
//                "智能水杯,蓝牙,手机 APP,饮水量监测,水温监测,锂电池,续航,语音提醒,iOS 系统,Android 系统";
//        String responses = "";
//        try {
//            responses = deepSeek.chat(prompt);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//        return responses; // 实际需替换为真实逻辑
//    }

    public static double[] convertFloatToDouble(float[] floatArray) {
        if (floatArray == null) {
            return null;
        }
        double[] doubleArray = new double[floatArray.length];
        for (int i = 0; i < floatArray.length; i++) {
            doubleArray[i] = BigDecimal.valueOf(floatArray[i]).doubleValue();
        }
        return doubleArray;
    }
}