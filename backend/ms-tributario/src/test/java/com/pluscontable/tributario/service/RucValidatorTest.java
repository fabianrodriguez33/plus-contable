package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** HU-01: formato, prefijo y dígito verificador del RUC. */
class RucValidatorTest {

    private static final String FORMATO = "El RUC debe tener exactamente 11 dígitos numéricos";
    private static final String PREFIJO = "El RUC debe iniciar con 10, 15, 17 o 20";
    private static final String DIGITO = "El dígito verificador del RUC no es válido";

    @ParameterizedTest(name = "{0} es válido")
    @ValueSource(strings = {
            "20100070970",  // personas jurídicas reales del padrón reducido de SUNAT
            "20186226292",
            "10123456781",  // persona natural (prefijo 10)
            "15000000016",  // prefijos 15 y 17, dígito calculado a mano
            "17000000027",
            "20123456786",
            "20999999990",  // la suma da resto 1 -> 11 - 1 = 10 -> dígito 0
            "20000000001",  // resto 10 -> 11 - 10 = 1
            "20000000061"   // resto 0 -> 11 - 0 = 11 -> dígito 1
    })
    void aceptaRucConEstructuraCorrecta(String ruc) {
        assertThat(RucValidator.motivoInvalido(ruc)).isNull();
        assertThat(RucValidator.esValido(ruc)).isTrue();
    }

    @Test
    void rechazaOnceUnos() {
        assertThat(RucValidator.esValido("11111111111")).isFalse();
        assertThat(RucValidator.motivoInvalido("11111111111")).isEqualTo(PREFIJO);
    }

    @ParameterizedTest(name = "{0} tiene el dígito verificador mal")
    @ValueSource(strings = {"20100070971", "20100070979", "20100060970", "10123456780", "20000000000"})
    void rechazaDigitoVerificadorIncorrecto(String ruc) {
        assertThat(RucValidator.motivoInvalido(ruc)).isEqualTo(DIGITO);
    }

    @ParameterizedTest(name = "{0} tiene prefijo inválido")
    @ValueSource(strings = {
            "30000000031",  // dígito correcto, pero el prefijo 30 no existe
            "00000000043",  // el padrón trae 27 filas con prefijo 00: no son RUC reales
            "11111111111",
            "16000000010",
            "21123456780"})
    void rechazaPrefijoDesconocidoAunqueElDigitoCuadre(String ruc) {
        assertThat(RucValidator.motivoInvalido(ruc)).isEqualTo(PREFIJO);
    }

    @ParameterizedTest(name = "[{0}] no tiene formato de RUC")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "2010007097", "201000709701", "2010007097A", "20100070970 ", " 20100070970", "20-1000709-7", "AA100070970"})
    void rechazaFormatoIncorrecto(String ruc) {
        assertThat(RucValidator.motivoInvalido(ruc)).isEqualTo(FORMATO);
    }

    @Test
    void cambiarUnDigitoDeUnRucValidoLoInvalida() {
        String valido = "20100070970";
        for (int i = 2; i < 10; i++) {
            char c = valido.charAt(i);
            char otro = c == '9' ? '8' : (char) (c + 1);
            String alterado = valido.substring(0, i) + otro + valido.substring(i + 1);
            assertThat(RucValidator.esValido(alterado)).as("alterado en la posición %d: %s", i, alterado).isFalse();
        }
    }
}
