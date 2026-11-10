package com.pluscontable.tributario.sire;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;

/** Cálculo puro de días de aviso SIRE; no decide la fecha de revisión ni los clientes obligados. */
public final class RecordatorioSireFechas {
    /** Decisión de Beto del 06/11/2026 recogida en el PR #31. */
    public static final List<Integer> DIAS_AVISO_INICIALES = List.of(3, 1, 0);

    private RecordatorioSireFechas() {
    }

    public static boolean corresponde(LocalDate fechaRevision, LocalDate fechaActual,
                                      List<Integer> diasAnticipacion) {
        if (fechaRevision == null || fechaActual == null) {
            throw new IllegalArgumentException("fechaRevision y fechaActual son obligatorias");
        }
        if (diasAnticipacion == null || diasAnticipacion.isEmpty()
                || diasAnticipacion.stream().anyMatch(dia -> dia == null || dia < 0)
                || new HashSet<>(diasAnticipacion).size() != diasAnticipacion.size()) {
            throw new IllegalArgumentException("diasAnticipacion debe contener días únicos y no negativos");
        }

        long diasRestantes = ChronoUnit.DAYS.between(fechaActual, fechaRevision);
        return diasRestantes >= 0 && diasRestantes <= Integer.MAX_VALUE
                && diasAnticipacion.contains((int) diasRestantes);
    }
}
