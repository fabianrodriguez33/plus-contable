package com.pluscontable.tributario.obligacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GrupoRucCronogramaTest {
    @ParameterizedTest
    @CsvSource({"0,0", "1,1", "2,2-3", "3,2-3", "4,4-5", "5,4-5",
            "6,6-7", "7,6-7", "8,8-9", "9,8-9"})
    void extraeUltimoDigitoYGrupoDelCsv(int digito, String grupo) {
        String ruc = "2099999999" + digito;
        assertEquals(digito, GrupoRucCronograma.ultimoDigito(ruc));
        assertEquals(grupo, GrupoRucCronograma.paraRuc(ruc));
    }

    @Test
    void rechazaRucInvalido() {
        assertThrows(IllegalArgumentException.class, () -> GrupoRucCronograma.ultimoDigito(null));
        assertThrows(IllegalArgumentException.class, () -> GrupoRucCronograma.ultimoDigito("123"));
        assertThrows(IllegalArgumentException.class, () -> GrupoRucCronograma.ultimoDigito("2099999999X"));
        assertThrows(IllegalArgumentException.class, () -> GrupoRucCronograma.ultimoDigito("2099999999９"));
    }
}
