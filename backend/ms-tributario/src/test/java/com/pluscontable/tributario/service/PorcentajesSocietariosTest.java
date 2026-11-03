package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** HU-02: porcentaje de cada socio según su aporte, con la regla del mayor resto y desempate por orden de alta. */
class PorcentajesSocietariosTest {

    private static List<BigDecimal> aportes(String... valores) {
        return java.util.Arrays.stream(valores).map(BigDecimal::new).toList();
    }

    private static BigDecimal suma(List<BigDecimal> porcentajes) {
        return porcentajes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void unSoloSocioTieneElCienPorCiento() {
        assertThat(PorcentajesSocietarios.calcular(aportes("5000"))).containsExactly(new BigDecimal("100.00"));
    }

    @Test
    void aportesProporcionalesDanPorcentajesExactos() {
        assertThat(PorcentajesSocietarios.calcular(aportes("60000", "40000")))
                .containsExactly(new BigDecimal("60.00"), new BigDecimal("40.00"));
        assertThat(PorcentajesSocietarios.calcular(aportes("1", "1", "2")))
                .containsExactly(new BigDecimal("25.00"), new BigDecimal("25.00"), new BigDecimal("50.00"));
    }

    @Test
    void tresAportesIgualesRepartenElResiduoAlPrimeroPorOrdenDeAlta() {
        List<BigDecimal> r = PorcentajesSocietarios.calcular(aportes("1000", "1000", "1000"));

        assertThat(r).containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
        assertThat(suma(r)).isEqualByComparingTo("100.00");
    }

    @Test
    void elResiduoVaALosMayoresRestosAunqueNoSeanLosPrimeros() {
        // 1/6 = 16.666..., 1/6 = 16.666..., 4/6 = 66.666...: los tres tienen resto 0.666 -> empate, gana el orden de alta
        assertThat(PorcentajesSocietarios.calcular(aportes("1", "1", "4")))
                .containsExactly(new BigDecimal("16.67"), new BigDecimal("16.67"), new BigDecimal("66.66"));
        // restos distintos: 1/7 = 14.2857 (resto .57), 2/7 = 28.5714 (resto .14), 4/7 = 57.1428 (resto .28)
        assertThat(PorcentajesSocietarios.calcular(aportes("1", "2", "4")))
                .containsExactly(new BigDecimal("14.29"), new BigDecimal("28.57"), new BigDecimal("57.14"));
    }

    @Test
    void elDesempateEsPorOrdenDeAltaYNoPorElTamanoDelAporte() {
        List<BigDecimal> r = PorcentajesSocietarios.calcular(aportes("100", "100", "100", "100", "100", "100", "100"));

        // 100/7 = 14.2857...: base 14.28 x 7 = 99.96, faltan 4 centésimas, para los cuatro primeros
        assertThat(r).containsExactly(
                new BigDecimal("14.29"), new BigDecimal("14.29"), new BigDecimal("14.29"), new BigDecimal("14.29"),
                new BigDecimal("14.28"), new BigDecimal("14.28"), new BigDecimal("14.28"));
        assertThat(suma(r)).isEqualByComparingTo("100.00");
    }

    @Test
    void losAportesConCentimosSeCalculanSinPerderPrecision() {
        List<BigDecimal> r = PorcentajesSocietarios.calcular(aportes("60000.00", "40000.50"));

        assertThat(r).containsExactly(new BigDecimal("60.00"), new BigDecimal("40.00"));
    }

    @Test
    void siempreSumaExactamente100ConEscala2() {
        Random azar = new Random(20261103L);
        for (int caso = 0; caso < 2_000; caso++) {
            int n = 1 + azar.nextInt(12);
            List<BigDecimal> entrada = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                entrada.add(BigDecimal.valueOf(1 + azar.nextInt(10_000_000), 2));
            }

            List<BigDecimal> r = PorcentajesSocietarios.calcular(entrada);

            assertThat(r).hasSize(n);
            assertThat(suma(r)).as("caso %d: %s", caso, entrada).isEqualByComparingTo("100.00");
            assertThat(r).allSatisfy(p -> assertThat(p.scale()).isEqualTo(2));
        }
    }

    @Test
    void nadieSeApartaMasDeUnaCentesimaDeSuPorcentajeExacto() {
        Random azar = new Random(7L);
        for (int caso = 0; caso < 500; caso++) {
            int n = 2 + azar.nextInt(9);
            List<BigDecimal> entrada = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                entrada.add(BigDecimal.valueOf(1 + azar.nextInt(5_000_000), 2));
            }
            BigDecimal capital = entrada.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

            List<BigDecimal> r = PorcentajesSocietarios.calcular(entrada);

            for (int i = 0; i < n; i++) {
                BigDecimal exacto = entrada.get(i).multiply(BigDecimal.valueOf(100))
                        .divide(capital, 10, java.math.RoundingMode.HALF_UP);
                assertThat(r.get(i).subtract(exacto).abs()).isLessThan(new BigDecimal("0.01"));
            }
        }
    }

    @Test
    void sinAportesOConAporteCeroONegativoNoSeCalcula() {
        assertThatThrownBy(() -> PorcentajesSocietarios.calcular(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PorcentajesSocietarios.calcular(aportes("100", "0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PorcentajesSocietarios.calcular(aportes("100", "-5")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
