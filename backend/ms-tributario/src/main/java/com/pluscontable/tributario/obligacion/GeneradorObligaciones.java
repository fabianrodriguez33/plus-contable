package com.pluscontable.tributario.obligacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Prepara obligaciones sin persistirlas. Las reglas del mes son las confirmadas por Beto el 02/11/2026. */
public final class GeneradorObligaciones {
    /**
     * Tipos que se generan al programar un mes: usan la fecha del cronograma SUNAT por último dígito del RUC.
     * SUPUESTO sin confirmar con Beto: los cinco se generan igual en MYPE, REMIPE y GENERAL.
     */
    public static final List<TipoObligacion> TIPOS_DEL_MES = List.of(TipoObligacion.IGV, TipoObligacion.RENTA,
            TipoObligacion.PLAME, TipoObligacion.ESSALUD, TipoObligacion.RETENCION_QUINTA);

    /** AFP tiene su propio cronograma, que aún no tenemos: no se genera en este sprint. */
    public static final List<TipoObligacion> TIPOS_NO_GENERADOS = List.of(TipoObligacion.AFP);

    private GeneradorObligaciones() {
    }

    public static List<Solicitud> solicitudesDelMes(Long clienteId, LocalDate periodo, LocalDate fechaVencimiento) {
        return TIPOS_DEL_MES.stream()
                .map(tipo -> new Solicitud(new Clave(clienteId, tipo, periodo), fechaVencimiento))
                .toList();
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
