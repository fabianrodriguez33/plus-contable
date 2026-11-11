package com.pluscontable.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** HU-37 (tarea 15): la contraseña temporal es aleatoria, legible y sale de un generador seguro. */
class GeneradorPasswordTest {

    private final GeneradorPassword generador = new GeneradorPassword();

    @Test
    void tieneDoceCaracteresDeUnAlfabetoSinLetrasQueSeConfunden() {
        for (int i = 0; i < 2000; i++) {
            String p = generador.generar();
            assertThat(p).hasSize(12).matches("[ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789]{12}");
        }
    }

    @Test
    void siempreLlevaMayusculaMinusculaYNumero() {
        for (int i = 0; i < 2000; i++) {
            String p = generador.generar();
            assertThat(p).matches(".*[A-Z].*").matches(".*[a-z].*").matches(".*[0-9].*");
        }
    }

    @Test
    void cadaContraseniaEsDistintaDeLasAnteriores() {
        Set<String> vistas = new HashSet<>();
        for (int i = 0; i < 5000; i++) vistas.add(generador.generar());
        assertThat(vistas).hasSize(5000);
    }

    @Test
    void lasPosicionesDeLaMayusculaLaMinusculaYElNumeroNoSonSiempreLasMismas() {
        Set<Integer> posicionesDeLaPrimeraMayuscula = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String p = generador.generar();
            for (int k = 0; k < p.length(); k++) {
                if (Character.isUpperCase(p.charAt(k))) { posicionesDeLaPrimeraMayuscula.add(k); break; }
            }
        }
        assertThat(posicionesDeLaPrimeraMayuscula.size()).isGreaterThan(3);
    }

    @Test
    void porDefectoUsaUnGeneradorSeguroYNoUnoPredecible() {
        Object azar = ReflectionTestUtils.getField(generador, "azar");

        assertThat(azar).isInstanceOf(SecureRandom.class);
    }

    @Test
    void conElMismoAzarDaLaMismaContraseniaYConOtroAzarOtra() {
        assertThat(new GeneradorPassword(new Random(7)).generar()).isEqualTo(new GeneradorPassword(new Random(7)).generar());
        assertThat(new GeneradorPassword(new Random(7)).generar()).isNotEqualTo(new GeneradorPassword(new Random(8)).generar());
    }
}
