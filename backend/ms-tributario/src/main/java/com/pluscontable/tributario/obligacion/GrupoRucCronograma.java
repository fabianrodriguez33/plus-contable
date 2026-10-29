package com.pluscontable.tributario.obligacion;

/** Grupos de último dígito exactamente como aparecen en el CSV de Julio. */
public final class GrupoRucCronograma {
    private GrupoRucCronograma() {
    }

    public static int ultimoDigito(String ruc) {
        if (ruc == null || ruc.length() != 11) {
            throw new IllegalArgumentException("RUC debe tener 11 dígitos");
        }
        for (int i = 0; i < ruc.length(); i++) {
            if (ruc.charAt(i) < '0' || ruc.charAt(i) > '9') {
                throw new IllegalArgumentException("RUC debe tener 11 dígitos");
            }
        }
        return ruc.charAt(10) - '0';
    }

    public static String paraRuc(String ruc) {
        return switch (ultimoDigito(ruc)) {
            case 0 -> "0";
            case 1 -> "1";
            case 2, 3 -> "2-3";
            case 4, 5 -> "4-5";
            case 6, 7 -> "6-7";
            case 8, 9 -> "8-9";
            default -> throw new IllegalStateException("Último dígito fuera de rango");
        };
    }
}
