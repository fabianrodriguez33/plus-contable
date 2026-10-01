package com.pluscontable.auth.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifrado simetrico AES-256 en modo GCM (autenticado). Cada cifrado usa un IV aleatorio de 12 bytes
 * que se antepone al texto cifrado: Base64(IV || ciphertext || tag).
 * Team JRJ - Plus Contable.
 */
@Component
public class AesUtil {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public AesUtil(@Value("${app.aes.secret-key}") String secretKey) {
        byte[] key = secretKey.getBytes(StandardCharsets.UTF_8);
        if (key.length != 32) {
            throw new IllegalStateException("app.aes.secret-key debe tener exactamente 32 bytes (AES-256)");
        }
        this.keySpec = new SecretKeySpec(key, "AES");
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + enc.length).put(iv).put(enc).array());
        } catch (Exception e) {
            throw new IllegalStateException("Error al cifrar", e);
        }
    }

    public String decrypt(String cipherText) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(Base64.getDecoder().decode(cipherText));
            byte[] iv = new byte[IV_BYTES];
            buf.get(iv);
            byte[] enc = new byte[buf.remaining()];
            buf.get(enc);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Error al descifrar", e);
        }
    }
}
