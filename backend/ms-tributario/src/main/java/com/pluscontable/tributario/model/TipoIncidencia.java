package com.pluscontable.tributario.model;

/** HU-23: tipos de incidencia del servicio. Supuesto: la lista sale de los ejemplos de la historia, pendiente de confirmar con el cliente. */
public enum TipoIncidencia {
    /** General: afecta a toda la cartera activa, no lleva cliente. */
    CAIDA_PORTAL_SOL,
    CLIENTE_NO_ENVIA_DOCUMENTOS,
    RECHAZO_FRACCIONAMIENTO,
    OTRO;

    public boolean esGeneral() {
        return this == CAIDA_PORTAL_SOL;
    }
}
