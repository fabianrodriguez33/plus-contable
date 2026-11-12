package com.pluscontable.tributario.ruc;

import com.pluscontable.tributario.model.Cliente;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Compara la condición HABIDO/NO_HABIDO guardada con una consulta válida, sin producir avisos. */
public final class DetectorCambioCondicionRuc {

    public enum Resultado { CAMBIO, SIN_CAMBIO, CONSULTA_NO_VALIDA }

    /** Valores reconocidos para comparar una respuesta; no modifica el modelo persistido de Cliente. */
    public enum EstadoSunat { ACTIVO, SUSPENDIDO, BAJA, DESCONOCIDO }

    public enum TipoEvaluacion {
        CONSULTA_NO_VALIDA, SIN_BASE_COMPARACION, SIN_CAMBIOS, CAMBIO_SIN_AVISO, CAMBIO_RELEVANTE
    }

    public enum MotivoAviso { BAJA, SUSPENDIDO, NO_HABIDO }

    /** Los motivos son inmutables y pueden incluir varias transiciones de una misma consulta. */
    public record Evaluacion(TipoEvaluacion tipo, boolean cambioCondicion, boolean cambioEstado,
                             Set<MotivoAviso> motivos) {
        public Evaluacion {
            motivos = Set.copyOf(motivos);
        }
    }

    private DetectorCambioCondicionRuc() {
    }

    public static Resultado evaluar(Cliente.CondicionHabido anterior, ResultadoConsulta consulta) {
        Objects.requireNonNull(anterior, "La condición anterior es obligatoria");
        if (!(consulta instanceof ResultadoConsulta.Encontrado encontrado) || encontrado.datos() == null) {
            return Resultado.CONSULTA_NO_VALIDA;
        }

        Cliente.CondicionHabido nueva = ConsultaRucService.condicionHabido(encontrado.datos().condicion());
        if (nueva == null) {
            return Resultado.CONSULTA_NO_VALIDA;
        }
        return anterior == nueva ? Resultado.SIN_CAMBIO : Resultado.CAMBIO;
    }

    /**
     * Evalúa por separado condición y estado SUNAT. Un anterior nulo o DESCONOCIDO no prueba una transición
     * de esa dimensión; la otra dimensión sí puede evaluarse. Solo se aceptan valores exactos del proveedor
     * (ignorando espacios exteriores y mayúsculas): variantes como «BAJA DEFINITIVA» requieren confirmación
     * antes de mapearse. Una respuesta inválida no produce motivos de aviso.
     */
    public static Evaluacion evaluar(Cliente.CondicionHabido condicionAnterior, EstadoSunat estadoAnterior,
                                     ResultadoConsulta consulta) {
        if (!(consulta instanceof ResultadoConsulta.Encontrado encontrado) || encontrado.datos() == null) {
            return nuevaEvaluacion(TipoEvaluacion.CONSULTA_NO_VALIDA, false, false, EnumSet.noneOf(MotivoAviso.class));
        }
        Cliente.CondicionHabido condicionNueva = ConsultaRucService.condicionHabido(encontrado.datos().condicion());
        EstadoSunat estadoNuevo = estadoSunat(encontrado.datos().estado());
        if (condicionNueva == null || estadoNuevo == null) {
            return nuevaEvaluacion(TipoEvaluacion.CONSULTA_NO_VALIDA, false, false, EnumSet.noneOf(MotivoAviso.class));
        }

        boolean hayCondicionAnterior = condicionAnterior != null;
        boolean hayEstadoAnterior = estadoAnterior != null && estadoAnterior != EstadoSunat.DESCONOCIDO;
        boolean cambioCondicion = hayCondicionAnterior && condicionAnterior != condicionNueva;
        boolean cambioEstado = hayEstadoAnterior && estadoAnterior != estadoNuevo;
        EnumSet<MotivoAviso> motivos = EnumSet.noneOf(MotivoAviso.class);
        if (cambioCondicion && condicionNueva == Cliente.CondicionHabido.NO_HABIDO) {
            motivos.add(MotivoAviso.NO_HABIDO);
        }
        if (cambioEstado && estadoNuevo == EstadoSunat.BAJA) motivos.add(MotivoAviso.BAJA);
        if (cambioEstado && estadoNuevo == EstadoSunat.SUSPENDIDO) motivos.add(MotivoAviso.SUSPENDIDO);

        TipoEvaluacion tipo = !motivos.isEmpty() ? TipoEvaluacion.CAMBIO_RELEVANTE
                : cambioCondicion || cambioEstado ? TipoEvaluacion.CAMBIO_SIN_AVISO
                : !hayCondicionAnterior || !hayEstadoAnterior ? TipoEvaluacion.SIN_BASE_COMPARACION
                : TipoEvaluacion.SIN_CAMBIOS;
        return nuevaEvaluacion(tipo, cambioCondicion, cambioEstado, motivos);
    }

    private static Evaluacion nuevaEvaluacion(TipoEvaluacion tipo, boolean cambioCondicion, boolean cambioEstado,
                                               EnumSet<MotivoAviso> motivos) {
        return new Evaluacion(tipo, cambioCondicion, cambioEstado, motivos);
    }

    private static EstadoSunat estadoSunat(String valor) {
        if (valor == null) return null;
        try {
            return EstadoSunat.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
