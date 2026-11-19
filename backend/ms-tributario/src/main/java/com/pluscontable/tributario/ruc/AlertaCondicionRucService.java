package com.pluscontable.tributario.ruc;

import com.pluscontable.tributario.aviso.AvisoInternoDto;
import com.pluscontable.tributario.aviso.AvisoInternoService;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.Cliente.CondicionHabido;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-22 · T80: aviso interno al estudio cuando un cliente pasa de HABIDO a NO HABIDO.
 * Alcance acordado con Julio el 17/11: solo NO HABIDO y sin cambiar el esquema. BAJA y SUSPENDIDO no se avisan
 * porque el cliente aún no guarda el estado SUNAT (pendiente de Beto, T84).
 */
@Service
public class AlertaCondicionRucService {

    public static final String ORIGEN = "HU22_RUC";
    /** «Hoy» es el de Lima, aunque el servidor corra en UTC. */
    static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final AvisoInternoService avisos;
    private final Clock clock;

    @Autowired
    public AlertaCondicionRucService(AvisoInternoService avisos) {
        this(avisos, Clock.system(ZONA));
    }

    AlertaCondicionRucService(AvisoInternoService avisos, Clock clock) {
        this.avisos = avisos;
        this.clock = clock;
    }

    /** Regla pura: solo la transición HABIDO → NO_HABIDO avisa. Sin valor anterior no hay base de comparación. */
    public static boolean pasaANoHabido(CondicionHabido anterior, CondicionHabido nueva) {
        return anterior == CondicionHabido.HABIDO && nueva == CondicionHabido.NO_HABIDO;
    }

    /**
     * Debe llamarse dentro de la transacción que guardó la nueva condición: si el aviso no se puede crear, el cambio
     * del cliente también se revierte, para que la alerta no se pierda (sin historial, la siguiente comparación ya
     * no vería la transición). Un mismo cliente genera como máximo un aviso por día de Lima: si ya existe el de hoy,
     * se devuelve ese aunque la razón social o el RUC hayan cambiado después.
     */
    @Transactional
    public Optional<AvisoInternoDto> registrarCambio(Cliente cliente, CondicionHabido anterior) {
        Objects.requireNonNull(cliente, "El cliente es obligatorio");
        if (!pasaANoHabido(anterior, cliente.getCondicionHabido())) {
            return Optional.empty();
        }
        String clave = claveEvento(cliente.getId(), LocalDate.now(clock));
        Optional<AvisoInternoDto> previo = avisos.buscarPorEvento(ORIGEN, clave);
        if (previo.isPresent()) {
            return previo;
        }
        return Optional.of(avisos.crear(ORIGEN, clave, mensaje(cliente), List.of(cliente.getId())));
    }

    static String claveEvento(Long clienteId, LocalDate fecha) {
        return "NO_HABIDO:cliente:" + clienteId + ":" + fecha;
    }

    static String mensaje(Cliente cliente) {
        return "El cliente " + cliente.getRazonSocial() + " (RUC " + cliente.getRuc() + ") pasó a condición NO HABIDO.";
    }
}
