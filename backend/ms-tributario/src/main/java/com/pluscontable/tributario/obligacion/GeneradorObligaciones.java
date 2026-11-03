package com.pluscontable.tributario.obligacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Prepara obligaciones sin persistirlas. Las reglas del mes son las confirmadas por Beto el 02/11/2026. */
public final class GeneradorObligaciones {
    /** Tipo que no se genera en un mes y por qué; se informa al contador. */
    public record Omision(TipoObligacion tipo, String motivo) {
    }

    /**
     * Datos del cliente que deciden qué se genera. {@code motivoSinQuinta} es null cuando la retención de quinta
     * categoría corresponde y, si no, el motivo por el que se omite.
     */
    public record ReglasMes(boolean hayPlanilla, boolean hayAsegurados, String motivoSinQuinta) {
    }

    public record TiposDelMes(List<TipoObligacion> generar, List<Omision> omitidas) {
    }

    public static final String MOTIVO_AFP = "AFP tiene su propio cronograma, que aún no está disponible";
    public static final String MOTIVO_SIN_PLANILLA = "El cliente no tiene trabajadores registrados en el periodo";
    public static final String MOTIVO_SIN_ASEGURADOS = "Ningún trabajador tiene remuneración mayor que 0";

    private GeneradorObligaciones() {
    }

    /**
     * Decide qué se genera. Confirmado por Beto (02/11/2026): IGV y RENTA siempre, con el mismo cronograma en
     * MYPE, REMIPE y GENERAL (solo cambia el cálculo del impuesto, no la fecha); PLAME si hay trabajadores;
     * AFP queda fuera.
     * SUPUESTO sin confirmar: ESSALUD se genera si hay trabajadores con remuneración mayor que 0; eso NO equivale
     * a "asegurado" (la columna essalud existe pero ninguna pantalla la llena).
     * PLAME: los "otros sujetos a declarar" no existen como dato; hoy solo cuentan los trabajadores.
     */
    public static TiposDelMes tiposDelMes(ReglasMes reglas) {
        if (reglas == null) throw new IllegalArgumentException("las reglas del mes son obligatorias");
        List<TipoObligacion> generar = new ArrayList<>(List.of(TipoObligacion.IGV, TipoObligacion.RENTA));
        List<Omision> omitidas = new ArrayList<>();
        if (reglas.hayPlanilla()) {
            generar.add(TipoObligacion.PLAME);
        } else {
            omitidas.add(new Omision(TipoObligacion.PLAME, MOTIVO_SIN_PLANILLA));
        }
        if (reglas.hayPlanilla() && reglas.hayAsegurados()) {
            generar.add(TipoObligacion.ESSALUD);
        } else {
            omitidas.add(new Omision(TipoObligacion.ESSALUD,
                    reglas.hayPlanilla() ? MOTIVO_SIN_ASEGURADOS : MOTIVO_SIN_PLANILLA));
        }
        if (reglas.motivoSinQuinta() == null) {
            generar.add(TipoObligacion.RETENCION_QUINTA);
        } else {
            omitidas.add(new Omision(TipoObligacion.RETENCION_QUINTA, reglas.motivoSinQuinta()));
        }
        omitidas.add(new Omision(TipoObligacion.AFP, MOTIVO_AFP));
        return new TiposDelMes(List.copyOf(generar), List.copyOf(omitidas));
    }

    public static List<Solicitud> solicitudesDelMes(Long clienteId, LocalDate periodo, LocalDate fechaVencimiento,
                                                    List<TipoObligacion> tipos) {
        return tipos.stream()
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
