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
    // 原有MultipartFile重载方法（保留兼容）
    public static Document extractLocalDocument(MultipartFile file) throws IOException {
        return extractLocalDocument(file.getInputStream(), file.getOriginalFilename());
    }

    // 新增InputStream重载方法（核心修改）
    public static Document extractLocalDocument(InputStream inputStream, String fileName) throws IOException {
        DocumentParser parser;
        String fileNameLower = fileName.toLowerCase();
        if (fileNameLower.endsWith(".docx") || fileNameLower.endsWith(".doc") ||
                fileNameLower.endsWith(".ppt") || fileNameLower.endsWith(".pptx") ||
                fileNameLower.endsWith(".xls") || fileNameLower.endsWith(".xlsx")) {
            parser = new ApachePoiDocumentParser();
        } else if (fileNameLower.endsWith(".txt")) {
            parser = new TextDocumentParser();
        } else {
            parser = new ApacheTikaDocumentParser(); // 兼容PDF等其他格式
        }
        return parser.parse(inputStream);
    }

    // 提取HTML正文（去除标签、广告）
    public static String extractHtmlContent(String htmlContent) {
        org.jsoup.nodes.Document doc = Jsoup.parse(htmlContent);
        doc.select("div.ad, nav, footer, script, style").remove();
        return doc.body().text().trim();
    }
}