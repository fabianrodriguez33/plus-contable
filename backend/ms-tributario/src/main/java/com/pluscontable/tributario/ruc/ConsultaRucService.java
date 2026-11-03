package com.pluscontable.tributario.ruc;

import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.service.RucValidator;
import jakarta.validation.ValidationException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Consulta de RUC para el alta de cliente (HU-01): valida antes de llamar y nunca bloquea el alta. */
@Service
public class ConsultaRucService {

    /** El proveedor actual solo consulta RUC de personas jurídicas, que empiezan con 20. */
    static final String PREFIJO_CUBIERTO = "20";
    static final int LARGO_DIRECCION = 250;

    private final ConsultaRuc consulta;

    public ConsultaRucService(ConsultaRuc consulta) {
        this.consulta = consulta;
    }

    public ConsultaRucDto consultar(String ruc) {
        String motivo = RucValidator.motivoInvalido(ruc);
        if (motivo != null) {
            throw new ValidationException(motivo);
        }
        if (!ruc.startsWith(PREFIJO_CUBIERTO)) {
            return new ConsultaRucDto(ConsultaRucDto.Resultado.FUERA_DE_COBERTURA,
                    "La consulta automática solo cubre RUC que empiezan con 20. Ingrese los datos manualmente.",
                    null, List.of());
        }
        return switch (consulta.consultar(ruc)) {
            case ResultadoConsulta.Encontrado e -> encontrado(e.datos());
            case ResultadoConsulta.NoEncontrado n -> new ConsultaRucDto(ConsultaRucDto.Resultado.NO_ENCONTRADO,
                    "El RUC no aparece en la consulta. Verifique el número o ingrese los datos manualmente.",
                    null, List.of());
            case ResultadoConsulta.NoDisponible d -> new ConsultaRucDto(ConsultaRucDto.Resultado.NO_DISPONIBLE,
                    "No se pudo consultar el RUC en este momento. Ingrese los datos manualmente.",
                    null, List.of());
        };
    }

    private ConsultaRucDto encontrado(DatosRuc d) {
        List<String> avisos = new ArrayList<>(avisos(d));
        String direccion = d.direccion();
        if (direccion != null && direccion.length() > LARGO_DIRECCION) {
            avisos.add("La dirección de SUNAT tiene " + direccion.length() + " caracteres y se recortó a "
                    + LARGO_DIRECCION + ". Revísela.");
            direccion = direccion.substring(0, LARGO_DIRECCION);
        }
        var sugerencia = new ConsultaRucDto.Sugerencia(d.ruc(), d.razonSocial(), direccion, condicionHabido(d.condicion()),
                d.estado(), d.condicion(), d.ubigeo(), d.fuente(), d.fechaDatos());
        return new ConsultaRucDto(ConsultaRucDto.Resultado.ENCONTRADO, "Datos sugeridos. Revíselos antes de guardar.",
                sugerencia, List.copyOf(avisos));
    }

    /**
     * Política de avisos (la define Beto): un estado distinto de ACTIVO o una condición distinta de HABIDO avisa
     * pero no bloquea el alta. Es el único lugar donde se decide; para bloquear, o para cambiar qué avisa, se cambia aquí.
     */
    static List<String> avisos(DatosRuc d) {
        List<String> avisos = new ArrayList<>();
        if (d.estado() == null || !"ACTIVO".equalsIgnoreCase(d.estado().trim())) {
            avisos.add("El estado del contribuyente en SUNAT es " + (d.estado() == null ? "desconocido" : d.estado())
                    + " (no ACTIVO).");
        }
        if (d.condicion() == null || !"HABIDO".equalsIgnoreCase(d.condicion().trim())) {
            avisos.add("La condición del contribuyente en SUNAT es " + (d.condicion() == null ? "desconocida" : d.condicion())
                    + " (no HABIDO).");
        }
        return avisos;
    }

    /** Solo los dos valores que el sistema conoce; cualquier otro queda para ingreso manual. */
    static Cliente.CondicionHabido condicionHabido(String condicion) {
        if (condicion == null) return null;
        return switch (condicion.trim().toUpperCase()) {
            case "HABIDO" -> Cliente.CondicionHabido.HABIDO;
            case "NO HABIDO", "NO_HABIDO" -> Cliente.CondicionHabido.NO_HABIDO;
            default -> null;
        };
    }
}
