package com.pluscontable.tributario.ruc;

import com.pluscontable.tributario.model.Cliente;
import java.time.LocalDate;
import java.util.List;

/**
 * Respuesta de {@code GET /api/tributario/ruc/{ruc}/consulta}. Siempre es 200 salvo RUC mal formado (400):
 * que el proveedor no responda no es un error del alta. Los datos son una sugerencia, no se guardan por sí solos.
 */
public record ConsultaRucDto(Resultado resultado, String mensaje, Sugerencia datos, List<String> avisos) {

    public enum Resultado { ENCONTRADO, NO_ENCONTRADO, NO_DISPONIBLE, FUERA_DE_COBERTURA }

    /**
     * @param condicionHabido solo si la condición del proveedor es exactamente HABIDO o NO HABIDO; si no, nulo
     * @param fechaDatos      fecha a la que corresponden los datos según el proveedor ({@code as_of})
     */
    public record Sugerencia(String ruc, String razonSocial, String direccion, Cliente.CondicionHabido condicionHabido,
                             String estado, String condicion, String ubigeo, String fuente, LocalDate fechaDatos) {
    }
}
