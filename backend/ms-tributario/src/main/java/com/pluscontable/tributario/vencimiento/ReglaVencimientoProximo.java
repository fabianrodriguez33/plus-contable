package com.pluscontable.tributario.vencimiento;

import com.pluscontable.tributario.model.Obligacion;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Selección pura de obligaciones pendientes dentro de una ventana explícita; no crea avisos. */
public final class ReglaVencimientoProximo {
    private ReglaVencimientoProximo() {
    }

    public record Candidato(Long obligacionId, Obligacion.Estado estado, LocalDate fechaVencimiento) {
        public Candidato {
            if (obligacionId == null || obligacionId <= 0) throw new IllegalArgumentException("obligacionId inválido");
            Objects.requireNonNull(estado, "estado");
        }
    }

    /** Incluye hoy y el último día de la ventana; una fecha pasada nunca es próxima. */
    public static boolean corresponde(Candidato candidato, LocalDate hoy, int diasAnticipacion) {
        Objects.requireNonNull(candidato, "candidato");
        Objects.requireNonNull(hoy, "hoy");
        if (diasAnticipacion < 0) throw new IllegalArgumentException("diasAnticipacion no puede ser negativo");
        LocalDate fecha = candidato.fechaVencimiento();
        return candidato.estado() == Obligacion.Estado.PENDIENTE && fecha != null
                && !fecha.isBefore(hoy) && !fecha.isAfter(hoy.plusDays(diasAnticipacion));
    }

    /** Repite un mismo ID solo una vez; versiones contradictorias del mismo ID se rechazan, no se adivina cuál prevalece. */
    public static List<Candidato> seleccionar(List<Candidato> candidatos, LocalDate hoy, int diasAnticipacion) {
        Objects.requireNonNull(candidatos, "candidatos");
        Objects.requireNonNull(hoy, "hoy");
        if (diasAnticipacion < 0) throw new IllegalArgumentException("diasAnticipacion no puede ser negativo");
        LinkedHashMap<Long, Candidato> unicos = new LinkedHashMap<>();
        for (Candidato candidato : candidatos) {
            Objects.requireNonNull(candidato, "candidato");
            Candidato anterior = unicos.putIfAbsent(candidato.obligacionId(), candidato);
            if (anterior != null && !anterior.equals(candidato)) {
                throw new IllegalArgumentException("Datos contradictorios para obligacionId " + candidato.obligacionId());
            }
        }
        return unicos.values().stream().filter(c -> corresponde(c, hoy, diasAnticipacion)).toList();
    }
}
