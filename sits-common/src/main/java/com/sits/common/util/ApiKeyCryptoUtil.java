package com.sits.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API Key 加解密工具类。
 *
 * <p>使用 AES-256-GCM 加密，密钥从配置中读取。
 * 密钥长度不足 32 字节时自动补零。
 */
public final class ApiKeyCryptoUtil {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyCryptoUtil.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKeySpec keySpec;

    public ApiKeyCryptoUtil(String secret) {
        byte[] keyBytes = new byte[32];
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(secretBytes, 0, keyBytes, 0, Math.min(secretBytes.length, 32));
        this.keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * 加密明文 API Key。
     *
     * @param plainText 明文
     * @return Base64 编码的密文（IV + 密文）
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Failed to encrypt API Key", e);
            throw new RuntimeException("API Key 加密失败", e);
        }
    }

    /**
     * 解密密文 API Key。
     *
     * @param cipherText Base64 编码的密文
     * @return 明文
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return "";
        }
        try {
            byte[] cipherData = Base64.getDecoder().decode(cipherText);
            ByteBuffer byteBuffer = ByteBuffer.wrap(cipherData);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] encrypted = new byte[byteBuffer.remaining()];
            byteBuffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

            byte[] plainBytes = cipher.doFinal(encrypted);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 数据库中的值可能是明文 API Key（如 sk-xxx），直接返回
            log.warn("API Key 不是合法的 Base64 密文，可能是明文存储，直接返回原值", e);
            return cipherText;
        } catch (Exception e) {
            log.error("Failed to decrypt API Key", e);
            throw new RuntimeException("API Key 解密失败", e);
        }
    }

    /**
     * 对 API Key 进行脱敏处理，仅保留前缀和后 4 位。
     * 例如：sk-xxxxxxxxxxxx → sk-****5cf6
     *
     * @param plainText 明文 API Key
     * @return 脱敏后的 API Key
     */
    public static String mask(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        if (plainText.length() <= 8) {
            return plainText.substring(0, 1) + "****";
        }
        // 保留前缀（第一个 - 之前或前 3 个字符）和后 4 位
        int dashIdx = plainText.indexOf('-');
        String prefix;
        if (dashIdx > 0 && dashIdx < plainText.length() - 1) {
            prefix = plainText.substring(0, dashIdx + 1);
        } else {
            prefix = plainText.substring(0, Math.min(3, plainText.length()));
        }
        String suffix = plainText.substring(Math.max(0, plainText.length() - 4));
        return prefix + "****" + suffix;
    }

    // ==================== 本地测试入口 ====================

    public static void main(String[] args) {
        String secret = "sits-ai-default-secret-change-me";
        ApiKeyCryptoUtil util = new ApiKeyCryptoUtil(secret);

        // 测试 1：DeepSeek API Key
        String deepSeekKey = "sk-xxxxxxxx";
        testRoundTrip(util, deepSeekKey, "DeepSeek API Key");

        // 测试 2：火山方舟 API Key
        String volcengineKey = "ark-xxxxxxxx";
        testRoundTrip(util, volcengineKey, "火山方舟 API Key");

        // 测试 3：空字符串
        testRoundTrip(util, "", "空字符串");

        // 测试 4：mask 脱敏
        System.out.println("=== Mask 脱敏测试 ===");
        System.out.println("原文  : " + deepSeekKey);
        System.out.println("脱敏后: " + mask(deepSeekKey));
        System.out.println("原文  : " + volcengineKey);
        System.out.println("脱敏后: " + mask(volcengineKey));
    }

    private static void testRoundTrip(ApiKeyCryptoUtil util, String plain, String label) {
        System.out.println("=== " + label + " ===");
        System.out.println("原文 : " + plain);
        String encrypted = util.encrypt(plain);
        System.out.println("密文 : " + encrypted);
        String decrypted = util.decrypt(encrypted);
        System.out.println("解密 : " + decrypted);
        System.out.println("校验 : " + (plain.equals(decrypted) ? "PASS" : "FAIL"));
        System.out.println();
    }
}
