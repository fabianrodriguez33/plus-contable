package com.pluscontable.tributario.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * HT-02 · Pruebas unitarias de los 4 criterios de la tarjeta sobre {@link AesUtil}: lo que se guarda va cifrado con
 * AES-256 (T105), no se lee sin la llave (T106), con otra llave falla sin mostrar el contenido (T107) y los límites y
 * rechazos (T109). El criterio 4 (la llave aparte) está en {@code LlaveCifradoAparteTest}. Claves ficticias: no son las
 * de ningún entorno.
 */
class AesUtilCriteriosTest {

    private static final String LLAVE = "clave-ficticia-de-prueba-32-byte";
    private static final String IV_LEGADO = "iv-ficticio-16by";
    private static final String CLAVE_SOL = "ClaveSol-Ficticia-9X";

    private final AesUtil util = new AesUtil(LLAVE, IV_LEGADO);

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] datosDe(String guardado) {
        return Base64.getDecoder().decode(guardado.substring(AesUtil.PREFIJO_V2.length()));
    }

    private static boolean contiene(byte[] pajar, byte[] aguja) {
        for (int i = 0; i + aguja.length <= pajar.length; i++) {
            boolean igual = true;
            for (int j = 0; j < aguja.length && igual; j++) igual = pajar[i + j] == aguja[j];
            if (igual) return true;
        }
        return false;
    }

    /** Llaves de 32 bytes que difieren de LLAVE en un solo carácter (la posición i). */
    private static String llaveConUnCambio(int i) {
        char[] c = LLAVE.toCharArray();
        c[i] = c[i] == 'X' ? 'Y' : 'X';
        return new String(c);
    }

    private static String volcado(Throwable t) {
        StringWriter w = new StringWriter();
        t.printStackTrace(new PrintWriter(w));
        return w.toString();
    }

    // ---- criterio 1 (T105): una Clave SOL enviada para guardar se almacena cifrada con AES-256

    @Test
    void criterio1_laClaveSeAlmacenaConAes256GcmYSePuedeLeerConUnaImplementacionIndependiente() throws Exception {
        byte[] datos = datosDe(util.encrypt(CLAVE_SOL));

        assertThat(bytes(LLAVE)).hasSize(32); // 256 bits
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.DECRYPT_MODE, new SecretKeySpec(bytes(LLAVE), "AES"), new GCMParameterSpec(128, datos, 0, 12));
        assertThat(new String(gcm.doFinal(datos, 12, datos.length - 12), StandardCharsets.UTF_8)).isEqualTo(CLAVE_SOL);
        assertThat(datos).hasSize(12 + bytes(CLAVE_SOL).length + 16); // IV + cifrado + etiqueta de 128 bits
    }

    @Test
    void criterio1_loGuardadoLlevaElPrefijoV2YNoContieneLaClaveNiEnTextoNiEnBytes() {
        String guardado = util.encrypt(CLAVE_SOL);

        assertThat(guardado).startsWith("v2:").doesNotContain(CLAVE_SOL);
        assertThat(contiene(datosDe(guardado), bytes(CLAVE_SOL))).isFalse();
        assertThat(contiene(datosDe(guardado), bytes(CLAVE_SOL.substring(0, 8)))).isFalse();
    }

    @Test
    void criterio1_cadaCifradoUsaUnIvAleatorioNuevo() {
        Set<String> ivs = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            byte[] datos = datosDe(util.encrypt(CLAVE_SOL));
            ivs.add(Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(datos, 12)));
        }

        assertThat(ivs).hasSize(50);
    }

    // ---- criterio 2 (T106): lo almacenado no se puede leer sin la llave de cifrado

    @Test
    void criterio2_conLaLlaveSeLeeYSinLaLlaveNingunaLlaveParecidaLoLee() {
        String guardado = util.encrypt(CLAVE_SOL);

        assertThat(util.decrypt(guardado)).isEqualTo(CLAVE_SOL);
        for (int i = 0; i < 32; i++) {
            AesUtil casi = new AesUtil(llaveConUnCambio(i), IV_LEGADO);
            assertThatThrownBy(() -> casi.decrypt(guardado)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void criterio2_loUnicoQueSeVeSinLaLlaveEsElIvPublicoYBytesSinRelacionConElTexto() {
        byte[] datos = datosDe(util.encrypt(CLAVE_SOL));
        // lo único que se ve sin la llave es el IV (público) y bytes sin relación con el texto
        byte[] cifrado = java.util.Arrays.copyOfRange(datos, 12, 12 + bytes(CLAVE_SOL).length);

        assertThat(cifrado).isNotEqualTo(bytes(CLAVE_SOL));
        assertThat(new String(cifrado, StandardCharsets.ISO_8859_1)).doesNotContain(CLAVE_SOL.substring(0, 6));
    }

    // ---- criterio 3 (T107): con una llave incorrecta, el descifrado falla sin mostrar nada del contenido

    @Test
    void criterio3_conOtraLlaveFallaYNiElMensajeNiLaCausaNiLaTrazaMuestranElContenido() {
        String guardado = util.encrypt(CLAVE_SOL);

        for (int i = 0; i < 32; i++) {
            AesUtil otra = new AesUtil(llaveConUnCambio(i), IV_LEGADO);
            Throwable e = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> otra.decrypt(guardado));
            assertThat(e.getMessage()).isEqualTo("Error al descifrar");
            assertThat(volcado(e)).doesNotContain(CLAVE_SOL).doesNotContain(CLAVE_SOL.substring(0, 6));
        }
    }

    @Test
    void criterio3_unValorAlteradoOIncompletoFallaSinMostrarNada() {
        byte[] original = datosDe(util.encrypt(CLAVE_SOL));
        String[] malos = {
                conByteCambiado(original, 0),                      // en el IV
                conByteCambiado(original, 14),                     // en el cifrado
                conByteCambiado(original, original.length - 1),    // en la etiqueta de autenticación
                AesUtil.PREFIJO_V2 + Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(original, 5)), // menos que el IV
                AesUtil.PREFIJO_V2 + Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(original, 12 + 3)), // sin etiqueta
                AesUtil.PREFIJO_V2,                                // vacío
                AesUtil.PREFIJO_V2 + "%%%no-es-base64%%%",
        };

        for (String malo : malos) {
            Throwable e = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> util.decrypt(malo));
            assertThat(volcado(e)).doesNotContain(CLAVE_SOL);
        }
    }

    private static String conByteCambiado(byte[] datos, int posicion) {
        byte[] copia = datos.clone();
        copia[posicion] ^= 0x01;
        return AesUtil.PREFIJO_V2 + Base64.getEncoder().encodeToString(copia);
    }

    @Test
    void criterio3_unValorAntiguoCbcConOtraLlaveNuncaDevuelveElTexto() throws Exception {
        // CBC no autentica: con una llave incorrecta casi siempre falla por el relleno y, rara vez, da basura; nunca el texto.
        Cipher cbc = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cbc.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(bytes(LLAVE), "AES"), new IvParameterSpec(bytes(IV_LEGADO)));
        String antiguo = Base64.getEncoder().encodeToString(cbc.doFinal(bytes(CLAVE_SOL)));
        assertThat(util.decrypt(antiguo)).isEqualTo(CLAVE_SOL);

        for (int i = 0; i < 32; i++) {
            AesUtil otra = new AesUtil(llaveConUnCambio(i), IV_LEGADO);
            try {
                assertThat(otra.decrypt(antiguo)).isNotEqualTo(CLAVE_SOL);
            } catch (IllegalStateException esperado) {
                assertThat(volcado(esperado)).doesNotContain(CLAVE_SOL);
            }
        }
    }

    // ---- T109: límites y rechazos

    @Test
    void limites_elTextoVacioLosAcentosYLosEmojisVuelvenIguales() {
        for (String texto : new String[] {"", "contraseña-ñandú-ÁÉÍÓÚ", "clave-😀-ficticia", "a"}) {
            String guardado = util.encrypt(texto);
            assertThat(guardado).startsWith("v2:");
            assertThat(util.decrypt(guardado)).isEqualTo(texto);
        }
    }

    @Test
    void limites_laLlaveYElIvDebenTenerSuLongitudExacta() {
        assertThatThrownBy(() -> new AesUtil(LLAVE.substring(0, 31), IV_LEGADO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesUtil(LLAVE + "x", IV_LEGADO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesUtil(LLAVE, IV_LEGADO.substring(0, 15))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesUtil(LLAVE, IV_LEGADO + "x")).isInstanceOf(IllegalStateException.class);
        assertThat(new AesUtil(LLAVE, IV_LEGADO)).isNotNull();
    }

    @Test
    void rechazos_noSeCifraUnValorNuloYNoSeDescifraUnValorNulo() {
        assertThatThrownBy(() -> util.encrypt(null)).isInstanceOf(IllegalStateException.class).hasMessage("Error al cifrar");
        assertThatThrownBy(() -> util.decrypt(null)).isInstanceOf(IllegalStateException.class).hasMessage("Error al descifrar");
    }

    @Test
    void limites_elMaximoQueCabeEnLaColumnaSeCalculaDelTamanoRealDelCifrado() {
        assertThat(AesUtil.maxBytesCifrables(300)).isEqualTo(194);
        for (int columna = 60; columna <= 400; columna++) {
            int max = AesUtil.maxBytesCifrables(columna);
            assertThat(util.encrypt("a".repeat(max)).length()).as("columna %d, %d bytes", columna, max).isLessThanOrEqualTo(columna);
            assertThat(util.encrypt("a".repeat(max + 1)).length()).as("columna %d, %d bytes", columna, max + 1).isGreaterThan(columna);
        }
    }
}
