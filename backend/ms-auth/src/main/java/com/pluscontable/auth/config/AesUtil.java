package com.pluscontable.auth.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Cifrado simetrico AES-256 (AES/CBC/PKCS5Padding). Clave de 32 bytes e IV de 16 bytes. */
@Component
public class AesUtil {

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    private final SecretKeySpec keySpec;
    private final IvParameterSpec ivSpec;

    public AesUtil(@Value("${app.aes.secret-key}") String secretKey, @Value("${app.aes.iv}") String iv) {
        byte[] key = secretKey.getBytes(StandardCharsets.UTF_8);
        byte[] ivBytes = iv.getBytes(StandardCharsets.UTF_8);
        if (key.length != 32) {
            throw new IllegalStateException("app.aes.secret-key debe tener exactamente 32 bytes (AES-256)");
        }
        if (ivBytes.length != 16) {
            throw new IllegalStateException("app.aes.iv debe tener exactamente 16 bytes");
        }
        this.keySpec = new SecretKeySpec(key, "AES");
        this.ivSpec = new IvParameterSpec(ivBytes);
    }

    public String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Error al cifrar", e);
        }
    }

    public String decrypt(String cipherText) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Error al descifrar", e);
        }
    }
}
