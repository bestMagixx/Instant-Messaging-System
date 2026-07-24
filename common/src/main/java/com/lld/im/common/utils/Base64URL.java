package com.lld.im.common.utils;

import cn.hutool.core.codec.Base64;
import java.nio.charset.StandardCharsets;

/**
 * @description: 标准 Base64URL 编码解码工具类
 * @author: lld
 * @version: 1.0
 */
public class Base64URL {

    /**
     * 标准 Base64URL 编码（替换 +/→-_，自动去除填充符 =）
     */
    public static byte[] base64EncodeUrl(byte[] input) {
        // 1. 标准Base64编码
        String base64 = Base64.encode(input);
        // 2. 替换为URL安全字符 +→- /→_
        base64 = base64.replace('+', '-')
                .replace('/', '_')
                .replace("=", ""); // 去除填充符
        return base64.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 编码（保留 = 填充符，不删除）
     */
    public static byte[] base64EncodeUrlNotReplace(byte[] input) {
        String base64 = Base64.encode(input);
        base64 = base64.replace('+', '-')
                .replace('/', '_');
        return base64.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 标准 Base64URL 解码（自动补全 =）
     */
    public static byte[] base64DecodeUrl(byte[] input) {
        String str = new String(input, StandardCharsets.UTF_8);
        // 1. 反向替换 -→+ _→/
        str = str.replace('-', '+')
                .replace('_', '/');
        // 2. 补全Base64填充符 =
        while (str.length() % 4 != 0) {
            str += "=";
        }
        // 3. 解码
        return Base64.decode(str);
    }

    /**
     * 解码（原字符串自带 =，无需补全）
     */
    public static byte[] base64DecodeUrlNotReplace(byte[] input) {
        String str = new String(input, StandardCharsets.UTF_8);
        str = str.replace('-', '+')
                .replace('_', '/');
        return Base64.decode(str);
    }
}