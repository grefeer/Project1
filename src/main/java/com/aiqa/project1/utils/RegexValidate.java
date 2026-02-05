package com.aiqa.project1.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexValidate {

    /**
     * 提取字符串中所有《》内的内容
     * @param content 待处理的字符串
     * @return 提取到的内容列表
     */
    public static List<String> extractContentInBookMark(String content) {
        Set<String> resultList = new HashSet<>();
        if (content == null || content.isEmpty()) {
            return resultList.stream().toList(); // 空字符串直接返回空列表
        }

        // 核心正则：匹配《》并分组内部内容
        String regex = "《([^》]+)》";
        // 编译正则（复用更高效）
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);

        // 循环查找所有匹配项
        while (matcher.find()) {
            // group(1) 获取第一个分组（括号内的内容），group(0)是包含《》的完整匹配
            String innerContent = matcher.group(1);
            resultList.add(innerContent);
        }
        if (resultList.isEmpty()) {
            return null;
        }
        return resultList.stream().toList();
    }
}