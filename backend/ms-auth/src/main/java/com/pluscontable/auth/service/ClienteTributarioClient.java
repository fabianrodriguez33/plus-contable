package com.pluscontable.auth.service;

import java.util.Optional;

/** HU-37: consulta a ms-tributario si el cliente existe (la empresa vive en el otro servicio). */
public interface ClienteTributarioClient {

    /** Datos mínimos de la empresa. */
    record ClienteResumen(Long id, String razonSocial) {
    }

    /**
     * @param autorizacion el encabezado Authorization del contador ("Bearer ..."), que se reenvía tal cual
     * @return vacío si ms-tributario responde 404; lanza 502 si no se puede comprobar
     */
    Optional<ClienteResumen> buscar(Long clienteId, String autorizacion);
}
