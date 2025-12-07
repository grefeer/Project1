package com.aiqa.project1.utils;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;

import dev.langchain4j.data.document.parser.TextDocumentParser;

import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;

import org.jsoup.Jsoup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * 文本提取工具类
 */
public class TextExtractUtil {
    // 提取本地文档文本（PDF/Word/Txt）
    public static Document extractLocalDocument(File file) throws FileNotFoundException {
        DocumentParser parser;
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".pdf")) {
            parser =  new ApachePdfBoxDocumentParser();
        } else if (
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

        InputStream is = new FileInputStream(file);
        Document document = parser.parse(is);
        return document;
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