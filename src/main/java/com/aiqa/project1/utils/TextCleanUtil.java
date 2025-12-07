package com.aiqa.project1.utils;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文本清洗工具类
 */
public class TextCleanUtil {

    public static String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.codePoints() // 处理补充平面字符（如emoji），比 chars() 更通用
                .filter(c -> {
                    // 排除控制字符（含 ASCII 控制符、Unicode 控制符）
                    if (Character.isISOControl(c)) {
                        // 例外：保留换行、回车、制表符
                        return c == '\n' || c == '\r' || c == '\t';
                    }
                    // 排除乱码占位符
                    if (c == 0xFFFD) {
                        return false;
                    }
                    // 保留空格、可见字符，排除其他空白字符（如制表符、换行符可按需保留）
                    return Character.isDefined(c) && (c == 32 || !Character.isWhitespace(c));
                })
                .mapToObj(c -> new String(Character.toChars(c)))
                .collect(Collectors.joining());
    }
}

