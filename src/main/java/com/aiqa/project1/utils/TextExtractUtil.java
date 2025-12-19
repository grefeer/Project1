package com.aiqa.project1.utils;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;

import dev.langchain4j.data.document.parser.TextDocumentParser;

import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;

import org.jsoup.Jsoup;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

/**
 * 文本提取工具类
 */
public class TextExtractUtil {
    // 提取本地文档文本（PDF/Word/Txt）
    public static Document extractLocalDocument(MultipartFile file) throws IOException {
        DocumentParser parser;
        String fileName = file.getName().toLowerCase();
        if (
                fileName.endsWith(".docx") || fileName.endsWith(".doc") ||
                fileName.endsWith(".ppt") || fileName.endsWith(".pptx") ||
                fileName.endsWith(".xls") || fileName.endsWith(".xlsx")
        ) {
            parser = new ApachePoiDocumentParser();
        } else if (fileName.endsWith(".txt")) {
            parser = new TextDocumentParser();
        } else {
            parser = new ApacheTikaDocumentParser();
        }

        InputStream is = file.getInputStream();
        return parser.parse(is);
    }

    // 提取HTML正文（去除标签、广告）
    public static String extractHtmlContent(String htmlContent) {
        org.jsoup.nodes.Document doc = Jsoup.parse(htmlContent);
        // 移除广告、导航等无关标签（可扩展领域专属规则）
        doc.select("div.ad, nav, footer, script, style").remove();
        // 提取正文文本
        return doc.body().text().trim();
    }
}