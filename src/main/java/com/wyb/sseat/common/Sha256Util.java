package com.wyb.sseat.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码工具：需求文档规定使用 SHA-256 加密存储密码。
 * 加密结果是不可逆的 64 位十六进制字符串，数据库里看不到明文。
 */
public final class Sha256Util {

    private Sha256Util() {}

    public static String hash(String plainText) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(plainText.getBytes(StandardCharsets.UTF_8));
            // 每个字节转成两位十六进制拼接
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** 校验：把用户输入的明文再加密一次，和库里存的密文比对 */
    public static boolean matches(String plainText, String hashed) {
        return hash(plainText).equalsIgnoreCase(hashed);
    }
}
