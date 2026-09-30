package com.pluscontable.auth.service;

import com.pluscontable.auth.config.AesUtil;
import org.springframework.stereotype.Service;

/** Servicio de cifrado de secretos en reposo (p. ej. Claves SOL) con AES-256. */
@Service
public class EncryptionService {

    private final AesUtil aesUtil;

    public EncryptionService(AesUtil aesUtil) {
        this.aesUtil = aesUtil;
    }

    public String encrypt(String plainText) {
        return aesUtil.encrypt(plainText);
    }

    public String decrypt(String cipherText) {
        return aesUtil.decrypt(cipherText);
    }
}
