package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.HistorialItemDto;
import com.pluscontable.tributario.repository.SolicitudEspecialRepository;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-27 · Consultar el historial de incidencias de un cliente: sus incidencias (las propias y las generales, HU-23) y sus
 * solicitudes especiales (pendientes y atendidas, HU-26), de la más reciente a la más antigua. A igual fecha, la registrada
 * después va primero.
 */
@Service
public class HistorialService {

    static final Comparator<HistorialItemDto> MAS_RECIENTE_PRIMERO = Comparator.comparing(HistorialItemDto::fecha)
            .thenComparing(HistorialItemDto::registradaEn).reversed();

    private final IncidenciaService incidencias;
    private final SolicitudEspecialRepository solicitudes;

    public HistorialService(IncidenciaService incidencias, SolicitudEspecialRepository solicitudes) {
        this.incidencias = incidencias;
        this.solicitudes = solicitudes;
    }

    /** 404 si el cliente no existe (lo comprueba IncidenciaService.delCliente). */
    @Transactional(readOnly = true)
    public List<HistorialItemDto> delCliente(Long clienteId) {
        return Stream.concat(
                        incidencias.delCliente(clienteId).stream().map(HistorialItemDto::de),
                        solicitudes.findByClienteId(clienteId).stream().map(HistorialItemDto::de))
                .sorted(MAS_RECIENTE_PRIMERO)
                .toList();
    }
}
