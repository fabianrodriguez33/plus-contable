package com.pluscontable.tributario.ruc;

/**
 * Consulta de datos públicos de un RUC a un proveedor externo. El resto del sistema solo conoce esta interfaz:
 * para cambiar de proveedor basta otra implementación y la propiedad {@code app.ruc.proveedor}.
 * Las implementaciones no lanzan excepciones por fallas del proveedor: las devuelven como {@link ResultadoConsulta}.
 */
public interface ConsultaRuc {

    /** @param ruc ya validado en formato por quien llama */
    ResultadoConsulta consultar(String ruc);
}
