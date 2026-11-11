package com.pluscontable.tributario.ruc;

import com.pluscontable.tributario.model.Cliente;
import java.util.Objects;

/** Compara la condición HABIDO/NO_HABIDO guardada con una consulta válida, sin producir avisos. */
public final class DetectorCambioCondicionRuc {

    public enum Resultado { CAMBIO, SIN_CAMBIO, CONSULTA_NO_VALIDA }

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
}
