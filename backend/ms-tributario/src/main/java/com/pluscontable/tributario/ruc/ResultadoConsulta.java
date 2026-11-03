package com.pluscontable.tributario.ruc;

/** Resultado de consultar un RUC. Distingue "no existe" de "no se pudo consultar" para que el alta nunca se bloquee. */
public sealed interface ResultadoConsulta {

    /** El proveedor devolvió datos del RUC. */
    record Encontrado(DatosRuc datos) implements ResultadoConsulta {
    }

    /** El proveedor respondió que el RUC no existe o está fuera de su alcance. */
    record NoEncontrado() implements ResultadoConsulta {
    }

    /** Servicio caído, lento, con límite de consultas o con una respuesta que no se puede usar. */
    record NoDisponible(String motivo) implements ResultadoConsulta {
    }
}
