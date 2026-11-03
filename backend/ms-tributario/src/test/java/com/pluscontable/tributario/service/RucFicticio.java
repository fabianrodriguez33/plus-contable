package com.pluscontable.tributario.service;

import java.util.concurrent.atomic.AtomicLong;

/** Apoyo de pruebas: RUC ficticios de persona jurídica (prefijo 20) con dígito verificador módulo 11 válido. */
final class RucFicticio {

    private static final int[] FACTORES = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final AtomicLong SECUENCIA = new AtomicLong(System.nanoTime() % 100_000_000L);

    private RucFicticio() {
    }

    static String nuevo() {
        String cuerpo = "20" + String.format("%08d", SECUENCIA.incrementAndGet() % 100_000_000L);
        int suma = 0;
        for (int i = 0; i < FACTORES.length; i++) {
            suma += (cuerpo.charAt(i) - '0') * FACTORES[i];
        }
        int dv = 11 - (suma % 11);
        return cuerpo + (dv == 10 ? 0 : dv == 11 ? 1 : dv);
    }
}
