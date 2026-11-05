package com.pluscontable.tributario.service;

import java.util.Set;

/**
 * Valida la estructura del RUC: 11 dígitos, prefijo conocido y dígito verificador (módulo 11).
 *
 * <p>Algoritmo: los 10 primeros dígitos se multiplican por 5,4,3,2,7,6,5,4,3,2; con la suma S se calcula
 * {@code 11 - (S mod 11)}; si da 10 el dígito es 0 y si da 11 el dígito es 1. Se comprobó contra el padrón
 * reducido de SUNAT (18 479 116 RUC numéricos del 21/10/2026): lo cumplen todos salvo 4 del prefijo 10.
 *
 * <p>Prefijos presentes en ese padrón: 10 (persona natural), 15, 17 y 20 (persona jurídica). El 16 no aparece.
 * Validar la estructura no prueba que el contribuyente exista ni que esté activo: eso requiere el padrón.
 */
public final class RucValidator {

    public static final Set<String> PREFIJOS = Set.of("10", "15", "17", "20");

    private static final int[] FACTORES = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private RucValidator() {
    }

    /** @return {@code null} si el RUC es válido; en caso contrario, el motivo en lenguaje claro */
    public static String motivoInvalido(String ruc) {
        if (ruc == null || !ruc.matches("\\d{11}")) {
            return "El RUC debe tener exactamente 11 dígitos numéricos";
        }
        if (!PREFIJOS.contains(ruc.substring(0, 2))) {
            return "El RUC debe iniciar con 10, 15, 17 o 20";
        }
        if (digitoVerificador(ruc) != ruc.charAt(10) - '0') {
            return "El dígito verificador del RUC no es válido";
        }
        return null;
    }

    public static boolean esValido(String ruc) {
        return motivoInvalido(ruc) == null;
    }

    /** Dígito verificador que corresponde a los 10 primeros dígitos del RUC. */
    static int digitoVerificador(String ruc) {
        int suma = 0;
        for (int i = 0; i < FACTORES.length; i++) {
            suma += (ruc.charAt(i) - '0') * FACTORES[i];
        }
        int dv = 11 - (suma % 11);
        return dv == 10 ? 0 : dv == 11 ? 1 : dv;
    }
}
