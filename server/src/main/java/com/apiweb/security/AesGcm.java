package com.apiweb.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密工具（用于 SECRET 类型全局变量）。
 *
 * <h3>算法</h3>
 * AES-256-GCM：业界推荐的认证加密（AEAD）算法,同时提供机密性和完整性。
 * <ul>
 *   <li>密钥长度：256 位（32 字节）</li>
 *   <li>IV 长度：96 位（12 字节,GCM 推荐）</li>
 *   <li>Tag 长度：128 位（16 字节）</li>
 * </ul>
 *
 * <h3>密钥派生</h3>
 * 主密钥从 {@code apiweb.aes.master-key} 配置项（Base64）或环境变量 {@code APIWEB_AES_MASTER_KEY} 读取。
 * 长度不足 32 字节时,使用 SHA-256 派生到 32 字节,保证 Java 安全策略下也能跑通。
 *
 * <h3>密文格式</h3>
 * {@code Base64(IV || CipherText||Tag)},IV 占前 12 字节,后续为密文+认证 Tag。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 *   String enc = AesGcm.encrypt("sk-xxx");
 *   String dec = AesGcm.decrypt(enc);   // 还原为 "sk-xxx"
 * }</pre>
 */
public final class AesGcm {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BIT_LENGTH = 128;

    /** 主密钥（启动时初始化） */
    private static volatile SecretKey KEY;
    /** 安全随机数生成器 */
    private static final SecureRandom RNG = new SecureRandom();

    private AesGcm() {}

    /**
     * 显式设置主密钥（推荐从配置中心读取 32 字节原始密钥）。
     *
     * @param raw 32 字节原始密钥
     */
    public static synchronized void setMasterKey(byte[] raw) {
        if (raw == null) {
            KEY = null;
            return;
        }
        if (raw.length < 16) {
            throw new IllegalArgumentException("AES 主密钥至少 16 字节,推荐 32 字节");
        }
        // 长度不是 32 字节时,使用 SHA-256 派生到 32 字节
        if (raw.length != 32) {
            try {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                raw = sha.digest(raw);
            } catch (Exception e) {
                throw new IllegalStateException("SHA-256 不可用", e);
            }
        }
        KEY = new SecretKeySpec(raw, "AES");
    }

    /**
     * 从任意字符串派生主密钥（用户配置密钥时不必强制 32 字节）。
     */
    public static void setMasterKeyFromString(String s) {
        if (s == null || s.isBlank()) {
            KEY = null;
            return;
        }
        try {
            byte[] raw = s.getBytes(StandardCharsets.UTF_8);
            setMasterKey(raw);
        } catch (Exception e) {
            throw new IllegalStateException("主密钥派生失败", e);
        }
    }

    /**
     * 获取当前是否已配置主密钥（false 时 encrypt/decrypt 抛错）。
     */
    public static boolean isReady() {
        return KEY != null;
    }

    /**
     * 加密明文,返回 Base64(IV||cipherText+tag)。
     */
    public static String encrypt(String plain) {
        if (!isReady()) {
            throw new IllegalStateException("AES 主密钥未初始化,无法加密");
        }
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            RNG.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(TAG_BIT_LENGTH, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解密 Base64(IV||cipherText+tag),返回明文。
     */
    public static String decrypt(String cipherText) {
        if (!isReady()) {
            throw new IllegalStateException("AES 主密钥未初始化,无法解密");
        }
        if (cipherText == null || cipherText.isBlank()) return cipherText;
        try {
            byte[] data = Base64.getDecoder().decode(cipherText);
            if (data.length < IV_LENGTH + TAG_BIT_LENGTH / 8) {
                throw new IllegalArgumentException("密文长度不合法");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ct = new byte[data.length - IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, IV_LENGTH);
            System.arraycopy(data, IV_LENGTH, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(TAG_BIT_LENGTH, iv));
            byte[] plain = cipher.doFinal(ct);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断是否为密文格式（Base64 且长度 &gt;= IV+Tag）。
     * 用于读取时判断是否需要解密。
     */
    public static boolean isCipherText(String s) {
        if (s == null || s.isBlank()) return false;
        try {
            byte[] data = Base64.getDecoder().decode(s);
            return data.length >= IV_LENGTH + TAG_BIT_LENGTH / 8;
        } catch (Exception e) {
            return false;
        }
    }
}