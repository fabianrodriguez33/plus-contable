package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.CobroHonorarioDto;
import com.pluscontable.tributario.dto.EstadoCuentaDto;
import com.pluscontable.tributario.dto.PagoHonorarioDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.CobroHonorarioRepository;
import com.pluscontable.tributario.repository.PagoHonorarioRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-30 · Emitir el estado de cuenta de honorarios: para un cliente y un periodo [desde, hasta], los cobros (HU-28) y los pagos
 * (HU-29) del periodo, lo que se debía antes (saldo anterior) y el saldo al cierre = saldo anterior + cobrado - pagado.
 */
@Service
public class EstadoCuentaService {

    private final ClienteRepository clientes;
    private final CobroHonorarioRepository cobros;
    private final PagoHonorarioRepository pagos;

    public EstadoCuentaService(ClienteRepository clientes, CobroHonorarioRepository cobros, PagoHonorarioRepository pagos) {
        this.clientes = clientes;
        this.cobros = cobros;
        this.pagos = pagos;
    }

    /** 400 si falta una fecha o desde es posterior a hasta (todos los errores juntos); 404 si el cliente no existe. */
    @Transactional(readOnly = true)
    public EstadoCuentaDto emitir(Long clienteId, LocalDate desde, LocalDate hasta) {
        List<String> errores = new ArrayList<>();
        if (desde == null) errores.add("desde");
        if (hasta == null) errores.add("hasta");
        if (desde != null && hasta != null && desde.isAfter(hasta)) errores.add("desde (no puede ser posterior a hasta)");
        if (!errores.isEmpty()) throw new ValidationException("Faltan o son inválidos: " + String.join(", ", errores));

        Cliente cliente = clientes.findById(clienteId).orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado"));

        BigDecimal saldoAnterior = cobros.cobradoAntesDe(clienteId, desde).subtract(pagos.pagadoAntesDe(clienteId, desde));
        List<CobroHonorarioDto> delPeriodo = cobros.findByClienteIdAndFechaCobroBetweenOrderByFechaCobroAscIdAsc(clienteId, desde, hasta)
                .stream().map(CobroHonorarioDto::from).toList();
        List<PagoHonorarioDto> pagosDelPeriodo = pagos.delPeriodo(clienteId, desde, hasta);
        BigDecimal cobrado = delPeriodo.stream().map(CobroHonorarioDto::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pagado = pagosDelPeriodo.stream().map(PagoHonorarioDto::monto).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new EstadoCuentaDto(clienteId, cliente.getRazonSocial(), desde, hasta, saldoAnterior, delPeriodo, pagosDelPeriodo,
                cobrado, pagado, saldoAnterior.add(cobrado).subtract(pagado), delPeriodo.isEmpty() && pagosDelPeriodo.isEmpty());
    }
}
