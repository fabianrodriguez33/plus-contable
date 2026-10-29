package com.pluscontable.tributario.obligacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Prepara obligaciones sin persistirlas ni seleccionar reglas tributarias. */
public final class GeneradorObligaciones {
    private GeneradorObligaciones() {
    }

    public record Clave(Long clienteId, TipoObligacion tipoObligacion, LocalDate periodo) {
        public Clave {
            ObligacionInputValidator.validar(clienteId, tipoObligacion, periodo);
        }
    }

    public record Solicitud(Clave clave, LocalDate fechaVencimiento) {
        public Solicitud {
            if (clave == null || fechaVencimiento == null) {
                throw new IllegalArgumentException("clave y fecha de vencimiento son obligatorias");
            }
        }
    }

    /** La fecha debe provenir de una selección explícita, nunca de un tipo supuesto. */
    public static List<Solicitud> preparar(List<Solicitud> solicitudes, Set<Clave> existentes) {
        if (solicitudes == null || existentes == null
                || solicitudes.stream().anyMatch(s -> s == null)
                || existentes.stream().anyMatch(c -> c == null)) {
            throw new IllegalArgumentException("solicitudes y claves existentes son obligatorias");
        }
        Set<Clave> conocidas = new HashSet<>(existentes);
        List<Solicitud> nuevas = new ArrayList<>();
        for (Solicitud solicitud : solicitudes) {
            if (conocidas.add(solicitud.clave())) nuevas.add(solicitud);
        }
        return List.copyOf(nuevas);
    }
}
