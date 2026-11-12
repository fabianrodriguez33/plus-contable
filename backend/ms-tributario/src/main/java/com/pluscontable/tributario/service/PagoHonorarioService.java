package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.CobroHonorarioDto;
import com.pluscontable.tributario.dto.PagoHonorarioEntradaDto;
import com.pluscontable.tributario.model.CobroHonorario;
import com.pluscontable.tributario.model.EstadoCobro;
import com.pluscontable.tributario.model.MedioPago;
import com.pluscontable.tributario.model.PagoHonorario;
import com.pluscontable.tributario.repository.CobroHonorarioRepository;
import com.pluscontable.tributario.repository.PagoHonorarioRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-29 · Registrar el pago de honorarios recibido. Un cobro (HU-28) admite varios pagos parciales: cada pago suma a lo
 * pagado; con saldo queda en PAGO_PARCIAL y sin saldo pasa a PAGADO. El sistema no verifica el pago en el banco.
 */
@Service
public class PagoHonorarioService {

    static final int DETALLE_MAXIMO = 100;

    private final CobroHonorarioRepository cobros;
    private final PagoHonorarioRepository pagos;
    private final Clock clock;

    @Autowired
    public PagoHonorarioService(CobroHonorarioRepository cobros, PagoHonorarioRepository pagos) {
        this(cobros, pagos, Clock.systemDefaultZone());
    }

    PagoHonorarioService(CobroHonorarioRepository cobros, PagoHonorarioRepository pagos, Clock clock) {
        this.cobros = cobros;
        this.pagos = pagos;
        this.clock = clock;
    }

    /**
     * 404 si el cobro no existe o es de otro cliente; 400 si ya está pagado (criterio 4) o los datos son inválidos (todos
     * los errores juntos). Bloquea el cobro mientras suma, para que dos pagos a la vez no superen el saldo.
     */
    @Transactional
    public CobroHonorarioDto registrar(Long clienteId, Long cobroId, PagoHonorarioEntradaDto dto, String usuario) {
        CobroHonorario cobro = cobros.buscarParaActualizar(cobroId)
                .filter(c -> c.getClienteId().equals(clienteId))
                .orElseThrow(() -> new EntityNotFoundException("Cobro no encontrado"));
        if (cobro.getEstado() == EstadoCobro.PAGADO) {
            throw new ValidationException("Solo se registran pagos sobre un cobro pendiente: este cobro ya está pagado");
        }
        validar(dto, cobro.saldo());

        PagoHonorario p = new PagoHonorario();
        p.setCobroId(cobro.getId());
        p.setFechaPago(dto.fechaPago());
        p.setMonto(dto.monto());
        p.setMedioPago(dto.medioPago());
        p.setMedioPagoDetalle(dto.medioPago() == MedioPago.OTRO ? dto.medioPagoDetalle().trim() : null);
        p.setRegistradoPor(usuario);
        pagos.save(p);

        cobro.setPagado(cobro.getPagado().add(dto.monto()));
        cobro.setEstado(cobro.saldo().signum() == 0 ? EstadoCobro.PAGADO : EstadoCobro.PAGO_PARCIAL);
        return CobroHonorarioDto.from(cobros.save(cobro));
    }

    private void validar(PagoHonorarioEntradaDto dto, BigDecimal saldo) {
        List<String> errores = new ArrayList<>();
        LocalDate fecha = dto == null ? null : dto.fechaPago();
        BigDecimal monto = dto == null ? null : dto.monto();

        if (fecha == null) errores.add("fechaPago");
        else if (fecha.isAfter(LocalDate.now(clock))) errores.add("fechaPago (no puede ser futura)");

        if (monto == null) errores.add("monto");
        else if (monto.signum() <= 0) errores.add("monto (debe ser mayor que cero)");
        else if (monto.stripTrailingZeros().scale() > 2) errores.add("monto (máximo 2 decimales)");
        else if (monto.compareTo(saldo) > 0) errores.add("monto (supera el saldo pendiente de " + saldo.toPlainString() + ")");

        if (dto == null || dto.medioPago() == null) errores.add("medioPago");
        else if (dto.medioPago() == MedioPago.OTRO) {
            String detalle = dto.medioPagoDetalle() == null ? "" : dto.medioPagoDetalle().trim();
            if (detalle.isEmpty()) errores.add("medioPagoDetalle (especifique el medio de pago)");
            else if (detalle.length() > DETALLE_MAXIMO) errores.add("medioPagoDetalle (máximo " + DETALLE_MAXIMO + " caracteres)");
        }

        if (!errores.isEmpty()) {
            throw new ValidationException("Faltan o son inválidos: " + String.join(", ", errores));
        }
    }
}
