package com.pluscontable.tributario.model;

/**
 * HU-23: tipos de incidencia del servicio. Los cuatro primeros salen de los ejemplos de la historia; los demás completan la
 * lista de Beto (10/11): problemas con SIRE, con SUNAT u otros servicios externos, fallas del sistema interno, problemas de
 * conexión a internet y errores en procesos contables o en la presentación de declaraciones. Solo CAIDA_PORTAL_SOL es
 * general (se asocia a toda la cartera activa); los demás van con un cliente.
 */
public enum TipoIncidencia {
    /**
     * Caída o indisponibilidad del portal SOL. Es el único tipo general (decisión de Beto, 10/11): afecta a toda la cartera
     * activa y no lleva cliente. El valor interno no cambia aunque la etiqueta de la pantalla sí.
     */
    CAIDA_PORTAL_SOL,
    CLIENTE_NO_ENVIA_DOCUMENTOS,
    RECHAZO_FRACCIONAMIENTO,
    /** Problemas con SIRE. */
    PROBLEMA_SIRE,
    /** Problemas con SUNAT u otros servicios externos. */
    PROBLEMA_SERVICIOS_EXTERNOS,
    /** Fallas del sistema interno. */
    FALLA_SISTEMA_INTERNO,
    /** Problemas de conexión a internet. */
    PROBLEMA_CONEXION_INTERNET,
    /** Errores en procesos contables o en la presentación de declaraciones. */
    ERROR_PROCESO_CONTABLE,
    OTRO;

    public boolean esGeneral() {
        return this == CAIDA_PORTAL_SOL;
    }
}
