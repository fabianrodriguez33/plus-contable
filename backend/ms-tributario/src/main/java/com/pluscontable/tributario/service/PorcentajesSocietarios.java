package com.pluscontable.tributario.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Porcentaje de participación de cada socio según su aporte respecto al capital social total.
 *
 * <p><b>Regla de redondeo y residuo (mayor resto):</b> los porcentajes tienen 2 decimales. A cada socio se le
 * asigna primero su porcentaje exacto truncado a la centésima; las centésimas que faltan para llegar a 100.00 se
 * reparten de a una entre los socios con mayor parte decimal descartada. Si dos socios tienen el mismo resto, la
 * centésima va primero al que se dio de alta antes (menor posición en la lista). Así la suma es siempre 100.00
 * exactos y a nadie se le asigna más de una centésima de diferencia respecto a su porcentaje exacto.
 *
 * <p>Ejemplo: tres aportes iguales dan 33.34, 33.33 y 33.33.
 */
public final class PorcentajesSocietarios {

    private static final BigInteger CIEN_PORCIENTO_EN_CENTESIMAS = BigInteger.valueOf(10_000);

    private PorcentajesSocietarios() {
    }

    /**
     * @param aportes aportes en soles, en el orden de alta; deben ser mayores que 0
     * @return los porcentajes con escala 2, en el mismo orden, que suman exactamente 100.00
     */
    public static List<BigDecimal> calcular(List<BigDecimal> aportes) {
        if (aportes == null || aportes.isEmpty()) {
            throw new IllegalArgumentException("Se necesita al menos un aporte");
        }
        List<BigInteger> centimos = aportes.stream().map(PorcentajesSocietarios::enCentimos).toList();
        if (centimos.stream().anyMatch(c -> c.signum() <= 0)) {
            throw new IllegalArgumentException("Todos los aportes deben ser mayores a 0");
        }
        BigInteger capital = centimos.stream().reduce(BigInteger.ZERO, BigInteger::add);

        int n = centimos.size();
        BigInteger[] base = new BigInteger[n];
        BigInteger[] resto = new BigInteger[n];
        BigInteger asignado = BigInteger.ZERO;
        for (int i = 0; i < n; i++) {
            BigInteger[] qr = centimos.get(i).multiply(CIEN_PORCIENTO_EN_CENTESIMAS).divideAndRemainder(capital);
            base[i] = qr[0];
            resto[i] = qr[1];
            asignado = asignado.add(qr[0]);
        }

        int faltantes = CIEN_PORCIENTO_EN_CENTESIMAS.subtract(asignado).intValueExact();
        List<Integer> orden = new ArrayList<>(IntStream.range(0, n).boxed().toList());
        orden.sort(Comparator.<Integer, BigInteger>comparing(i -> resto[i]).reversed().thenComparing(Comparator.naturalOrder()));
        for (int k = 0; k < faltantes; k++) {
            int i = orden.get(k);
            base[i] = base[i].add(BigInteger.ONE);
        }

        return IntStream.range(0, n).mapToObj(i -> new BigDecimal(base[i], 2)).toList();
    }

    private static BigInteger enCentimos(BigDecimal aporte) {
        return aporte.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toBigIntegerExact();
    }
}
