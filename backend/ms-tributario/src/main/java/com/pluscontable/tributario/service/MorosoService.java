package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.MorosoDto;
import com.pluscontable.tributario.repository.CobroHonorarioRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-31 · Identificar los clientes morosos: los que tienen al menos un cobro de honorarios (HU-28) vencido, es decir con fecha
 * anterior a hoy y saldo sin pagar (PENDIENTE o PAGO_PARCIAL, HU-29). Un cobro que vence hoy todavía no está vencido; un cliente
 * al día no aparece. Se incluyen los clientes inactivos: su deuda sigue vigente.
 */
@Service
public class MorosoService {

    private final CobroHonorarioRepository cobros;
    private final Clock clock;

    @Autowired
    public MorosoService(CobroHonorarioRepository cobros) {
        this(cobros, Clock.systemDefaultZone());
    }

    MorosoService(CobroHonorarioRepository cobros, Clock clock) {
        this.cobros = cobros;
        this.clock = clock;
    }

    /** Los más atrasados primero; a igual atraso, el de mayor monto. */
    @Transactional(readOnly = true)
    public List<MorosoDto> listar() {
        LocalDate hoy = LocalDate.now(clock);
        return cobros.deudasVencidas(CobroHonorarioService.CON_SALDO, hoy).stream()
                .map(d -> new MorosoDto(d.clienteId(), d.ruc(), d.razonSocial(), d.montoVencido(), d.cobrosVencidos(),
                        d.vencidoDesde(), ChronoUnit.DAYS.between(d.vencidoDesde(), hoy)))
                .sorted(Comparator.comparingLong(MorosoDto::diasAtraso).reversed()
                        .thenComparing(MorosoDto::montoVencido, Comparator.reverseOrder()))
                .toList();
    }
}
