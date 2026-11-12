package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.ObligacionDto;
import com.pluscontable.tributario.dto.PagoDto;
import com.pluscontable.tributario.dto.PagoEntradaDto;
import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.model.PagoObligacion;
import com.pluscontable.tributario.repository.ObligacionRepository;
import com.pluscontable.tributario.repository.PagoObligacionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * HU-13 · Registrar el pago de una obligación declarada. El comprobante es el número de constancia; el PDF es opcional
 * (alcance reducido de Beto, 10/11: la pantalla no lo envía; si llega, se valida y se guarda como antes). Una obligación
 * admite varios pagos parciales: cada pago suma al acumulado y reduce el saldo; con el acumulado igual al monto determinado
 * pasa a PAGADO. El sistema no verifica el pago en SUNAT ni en el banco (acordado con Beto).
 */
@Service
public class PagoObligacionService {

    private static final byte[] FIRMA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final ObligacionRepository obligaciones;
    private final PagoObligacionRepository pagos;
    private final AlmacenComprobantes almacen;
    private final long tamanoMaximoBytes;
    private final long tamanoMaximoMb;
    private final Clock clock;

    @Autowired
    public PagoObligacionService(ObligacionRepository obligaciones, PagoObligacionRepository pagos,
                                 AlmacenComprobantes almacen, @Value("${app.pagos.archivo-max-mb:10}") long tamanoMaximoMb) {
        this(obligaciones, pagos, almacen, tamanoMaximoMb, Clock.systemDefaultZone());
    }

    PagoObligacionService(ObligacionRepository obligaciones, PagoObligacionRepository pagos, AlmacenComprobantes almacen,
                          long tamanoMaximoMb, Clock clock) {
        this.obligaciones = obligaciones;
        this.pagos = pagos;
        this.almacen = almacen;
        this.tamanoMaximoMb = tamanoMaximoMb;
        this.tamanoMaximoBytes = tamanoMaximoMb * 1024 * 1024;
        this.clock = clock;
    }

    /**
     * 404 si la obligación no existe; 400 si no está declarada, ya está pagada o los datos son inválidos (todos los
     * errores juntos). Bloquea la obligación mientras suma, para que dos pagos a la vez no superen el saldo.
     */
    @Transactional
    public ObligacionDto registrar(Long obligacionId, PagoEntradaDto dto, MultipartFile archivo) {
        Obligacion o = obligaciones.buscarParaActualizar(obligacionId)
                .orElseThrow(() -> new EntityNotFoundException("Obligación no encontrada"));
        if (o.getEstado() == Obligacion.Estado.PENDIENTE) {
            throw new ValidationException("Solo se registra pago sobre una obligación ya declarada");
        }
        if (o.getEstado() == Obligacion.Estado.PAGADO) {
            throw new ValidationException("La obligación ya está pagada");
        }
        BigDecimal determinado = o.getMontoDeterminado() == null ? BigDecimal.ZERO : o.getMontoDeterminado();
        BigDecimal acumuladoAntes = pagos.sumaPagada(o.getId());
        BigDecimal saldoAntes = determinado.subtract(acumuladoAntes);
        validar(o.getId(), dto, archivo, saldoAntes);

        BigDecimal monto = dto.montoPagado();
        BigDecimal acumulado = acumuladoAntes.add(monto);
        BigDecimal saldo = determinado.subtract(acumulado);
        int numeroPagos = (int) pagos.countByObligacionId(o.getId()) + 1;

        AlmacenComprobantes.Guardado guardado = hayArchivo(archivo) ? almacen.guardar(o.getClienteId(), archivo) : null;
        try {
            PagoObligacion p = new PagoObligacion();
            p.setObligacionId(o.getId());
            p.setFechaPago(dto.fechaPago());
            p.setMontoPagado(monto);
            p.setNumeroConstancia(dto.numeroConstancia().trim());
            if (guardado != null) {
                p.setNombreArchivo(guardado.nombreArchivo());
                p.setContentType("application/pdf");
                p.setTamanoBytes(guardado.tamanoBytes());
                p.setRutaAlmacenamiento(guardado.rutaRelativa());
            }
            p.setAcumuladoDespues(acumulado);
            p.setSaldoDespues(saldo);
            pagos.save(p);
            o.setEstado(saldo.signum() == 0 ? Obligacion.Estado.PAGADO : Obligacion.Estado.PAGO_PARCIAL);
            return ObligacionDto.from(obligaciones.save(o), acumulado, numeroPagos);
        } catch (RuntimeException e) {
            if (guardado != null) almacen.borrar(guardado.destino()); // sin registro el archivo quedaría huérfano
            throw e;
        }
    }

    private void validar(Long obligacionId, PagoEntradaDto dto, MultipartFile archivo, BigDecimal saldoAntes) {
        List<String> errores = new ArrayList<>();
        LocalDate fecha = dto == null ? null : dto.fechaPago();
        BigDecimal monto = dto == null ? null : dto.montoPagado();
        String constancia = dto == null ? null : dto.numeroConstancia();

        if (fecha == null) errores.add("fechaPago");
        else if (fecha.isAfter(LocalDate.now(clock))) errores.add("fechaPago (no puede ser futura)");

        if (monto == null) errores.add("montoPagado");
        else if (monto.signum() <= 0) errores.add("montoPagado (debe ser mayor que cero)");
        else if (monto.compareTo(saldoAntes) > 0) {
            errores.add("montoPagado (supera el saldo pendiente de " + saldoAntes.toPlainString() + ")");
        }

        if (constancia == null || constancia.isBlank()) errores.add("numeroConstancia");
        else if (constancia.trim().length() > 30) errores.add("numeroConstancia (máximo 30 caracteres)");
        else if (pagos.existsByObligacionIdAndNumeroConstancia(obligacionId, constancia.trim())) {
            errores.add("numeroConstancia (ya registrado en esta obligación)");
        }

        if (hayArchivo(archivo)) {
            if (archivo.getSize() > tamanoMaximoBytes) errores.add("archivo (supera el máximo de " + tamanoMaximoMb + " MB)");
            else if (!esPdf(archivo)) errores.add("archivo (no es un PDF)");
        }

        if (!errores.isEmpty()) {
            throw new ValidationException("Faltan o son inválidos: " + String.join(", ", errores));
        }
    }

    private static boolean hayArchivo(MultipartFile archivo) {
        return archivo != null && !archivo.isEmpty();
    }

    /** HU-13: los pagos de la obligación en orden cronológico; 404 si la obligación no existe. */
    @Transactional(readOnly = true)
    public List<PagoDto> listar(Long obligacionId) {
        if (!obligaciones.existsById(obligacionId)) throw new EntityNotFoundException("Obligación no encontrada");
        return pagos.findByObligacionIdOrderByFechaPagoAscIdAsc(obligacionId).stream().map(PagoDto::from).toList();
    }

    /** Se exige el tipo declarado y la firma %PDF- del contenido: la extensión sola se puede falsear (igual que HU-04). */
    private static boolean esPdf(MultipartFile archivo) {
        if (!"application/pdf".equalsIgnoreCase(archivo.getContentType())) return false;
        try (InputStream in = archivo.getInputStream()) {
            return Arrays.equals(in.readNBytes(FIRMA_PDF.length), FIRMA_PDF);
        } catch (IOException e) {
            return false;
        }
    }
}
