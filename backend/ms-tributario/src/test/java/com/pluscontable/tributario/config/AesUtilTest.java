package com.pluscontable.tributario.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Claves e IV ficticios: no son los de ningun entorno. */
class AesUtilTest {

    private static final String CLAVE = "clave-ficticia-de-prueba-32-byte";
    private static final String IV_LEGADO = "iv-ficticio-16by";
    private static final String TEXTO = "texto-ficticio-123";

    private final AesUtil util = new AesUtil(CLAVE, IV_LEGADO);

    /** Reproduce el cifrado antiguo (CBC con IV fijo) para generar un valor legado de prueba. */
    private static String cifrarComoAntes(String clave, String iv, String texto) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8)));
        return Base64.getEncoder().encodeToString(cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void cifrarYDescifrarDevuelveElMismoTexto() {
        assertEquals(TEXTO, util.decrypt(util.encrypt(TEXTO)));
    }

    @Test
    void loNuevoLlevaPrefijoV2YNoEsLegado() {
        String cifrado = util.encrypt(TEXTO);
        assertTrue(cifrado.startsWith("v2:"));
        assertFalse(util.esLegado(cifrado));
        assertFalse(cifrado.contains(TEXTO));
    }

    @Test
    void elMismoTextoCifradoDosVecesDaResultadosDistintos() {
        String a = util.encrypt(TEXTO);
        String b = util.encrypt(TEXTO);
        assertNotEquals(a, b);
        assertEquals(TEXTO, util.decrypt(a));
        assertEquals(TEXTO, util.decrypt(b));
    }

    @Test
    void alterarUnByteDelCifradoHaceFallarElDescifrado() {
        byte[] datos = Base64.getDecoder().decode(util.encrypt(TEXTO).substring(3));
        datos[datos.length - 1] ^= 0x01;
        String alterado = "v2:" + Base64.getEncoder().encodeToString(datos);
        assertThrows(IllegalStateException.class, () -> util.decrypt(alterado));
    }

    @Test
    void sigueLeyendoLoGuardadoAntesEnCbc() throws Exception {
        String legado = cifrarComoAntes(CLAVE, IV_LEGADO, TEXTO);
        assertTrue(util.esLegado(legado));
        assertEquals(TEXTO, util.decrypt(legado));
    }

    @Test
    void conOtraClaveElDescifradoFallaSinFiltrarElTexto() {
        AesUtil otra = new AesUtil("otra-clave-ficticia-de-prueba-32", IV_LEGADO);
        String cifrado = util.encrypt(TEXTO);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> otra.decrypt(cifrado));
        assertFalse(String.valueOf(e.getMessage()).contains(TEXTO));
    }

    @Test
    void conOtroIvLegadoUnValorCbcNoSeDescifraIgual() throws Exception {
        String legado = cifrarComoAntes(CLAVE, IV_LEGADO, TEXTO);
        AesUtil otro = new AesUtil(CLAVE, "otro-iv-ficticio");
        // Con CBC un IV incorrecto altera el primer bloque: o falla o no devuelve el texto original.
        try {
            assertNotEquals(TEXTO, otro.decrypt(legado));
        } catch (IllegalStateException esperado) {
            // tambien es un resultado valido
        }
    }

    @Test
    void rechazaClaveOIvConLongitudIncorrecta() {
        assertThrows(IllegalStateException.class, () -> new AesUtil("corta", IV_LEGADO));
        assertThrows(IllegalStateException.class, () -> new AesUtil(CLAVE, "corto"));
    }
}
