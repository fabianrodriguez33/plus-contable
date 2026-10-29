package com.pluscontable.tributario.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifrado simetrico AES-256 para Claves SOL en reposo.
 * Lo nuevo se cifra con AES/GCM y un IV aleatorio de 12 bytes: "v2:" + Base64(IV + cifrado + etiqueta).
 * Lo anterior (sin prefijo) esta en AES/CBC con el IV fijo legado y solo se puede leer.
 */
@Component
public class AesUtil {

    public static final String PREFIJO_V2 = "v2:";

    private static final String GCM = "AES/GCM/NoPadding";
    private static final String CBC = "AES/CBC/PKCS5Padding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec keySpec;
    private final IvParameterSpec legacyIvSpec;

    public AesUtil(@Value("${app.aes.secret-key}") String secretKey, @Value("${app.aes.legacy-iv}") String legacyIv) {
        byte[] key = secretKey.getBytes(StandardCharsets.UTF_8);
        byte[] ivBytes = legacyIv.getBytes(StandardCharsets.UTF_8);
        if (key.length != 32) {
            throw new IllegalStateException("app.aes.secret-key debe tener exactamente 32 bytes (AES-256)");
        }
        if (ivBytes.length != 16) {
            throw new IllegalStateException("app.aes.legacy-iv debe tener exactamente 16 bytes");
        }
        this.keySpec = new SecretKeySpec(key, "AES");
        this.legacyIvSpec = new IvParameterSpec(ivBytes);
    }

    /** Cifra siempre con GCM y un IV nuevo. */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(GCM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] salida = Arrays.copyOf(iv, iv.length + cifrado.length);
            System.arraycopy(cifrado, 0, salida, iv.length, cifrado.length);
            return PREFIJO_V2 + Base64.getEncoder().encodeToString(salida);
        } catch (Exception e) {
            throw new IllegalStateException("Error al cifrar", e);
        }
    }

    /** Descifra "v2:" con GCM y cualquier otro valor con el esquema CBC legado. */
    public String decrypt(String cipherText) {
        try {
            if (!esLegado(cipherText)) {
                byte[] datos = Base64.getDecoder().decode(cipherText.substring(PREFIJO_V2.length()));
                Cipher cipher = Cipher.getInstance(GCM);
                cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, datos, 0, GCM_IV_BYTES));
                return new String(cipher.doFinal(datos, GCM_IV_BYTES, datos.length - GCM_IV_BYTES), StandardCharsets.UTF_8);
            }
            Cipher cipher = Cipher.getInstance(CBC);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, legacyIvSpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Error al descifrar", e);
        }
    }

    /** true si el valor esta en el formato antiguo (CBC, sin prefijo "v2:"). */
    public boolean esLegado(String cipherText) {
        return !cipherText.startsWith(PREFIJO_V2);
    }
}
