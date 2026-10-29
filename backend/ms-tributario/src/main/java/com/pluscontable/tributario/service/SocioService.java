package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.ResumenSocietarioDto;
import com.pluscontable.tributario.dto.SocioDto;
import com.pluscontable.tributario.model.Socio;
import com.pluscontable.tributario.repository.SocioRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocioService {

    private static final BigDecimal CIEN = new BigDecimal("100.00");

    private final SocioRepository socioRepository;
    private final ClienteService clienteService;

    public SocioService(SocioRepository socioRepository, ClienteService clienteService) {
        this.socioRepository = socioRepository;
        this.clienteService = clienteService;
    }

    @Transactional(readOnly = true)
    public List<SocioDto> listar(Long clienteId) {
        clienteService.buscar(clienteId);
        return socioRepository.findByClienteIdOrderById(clienteId).stream().map(SocioDto::from).toList();
    }

    /** Socios con el capital social total (suma de aportes) y la situación del 100 % de participación. */
    @Transactional(readOnly = true)
    public ResumenSocietarioDto resumen(Long clienteId) {
        return resumenDe(listar(clienteId));
    }

    /**
     * Reemplaza la estructura societaria completa. La suma de participaciones debe ser exactamente 100.00 %;
     * si no, no se guarda y el mensaje indica cuántos puntos faltan o sobran.
     */
    @Transactional
    public ResumenSocietarioDto reemplazar(Long clienteId, List<SocioDto> socios) {
        clienteService.buscar(clienteId);
        if (socios == null || socios.isEmpty()) {
            throw new ValidationException("Debe registrar al menos un socio (la participación total debe ser 100.00%)");
        }
        BigDecimal suma = sumaPorcentajes(socios);
        if (suma.compareTo(CIEN) != 0) {
            throw new ValidationException(mensajeDiferencia(suma));
        }
        socioRepository.deleteByClienteId(clienteId);
        socioRepository.flush();
        List<Socio> nuevos = socios.stream().map(dto -> {
            Socio s = new Socio();
            s.setClienteId(clienteId);
            s.setNombreCompleto(dto.nombreCompleto().trim());
            s.setTipoDocumento(dto.tipoDocumento() == null || dto.tipoDocumento().isBlank() ? "DNI" : dto.tipoDocumento());
            s.setNumeroDocumento(dto.numeroDocumento().trim());
            s.setAporte(dto.aporte().setScale(2, RoundingMode.HALF_UP));
            s.setPorcentaje(dto.porcentaje().setScale(2, RoundingMode.HALF_UP));
            s.setCargo(dto.cargo());
            return s;
        }).toList();
        return resumenDe(socioRepository.saveAll(nuevos).stream().map(SocioDto::from).toList());
    }

    /** Edita solo el aporte de un socio; los porcentajes no cambian, y el capital social se recalcula. */
    @Transactional
    public ResumenSocietarioDto actualizarAporte(Long clienteId, Long socioId, BigDecimal aporte) {
        clienteService.buscar(clienteId);
        Socio s = socioRepository.findByIdAndClienteId(socioId, clienteId)
                .orElseThrow(() -> new EntityNotFoundException("Socio no encontrado: " + socioId));
        s.setAporte(aporte.setScale(2, RoundingMode.HALF_UP));
        socioRepository.save(s);
        return resumen(clienteId);
    }

    private static ResumenSocietarioDto resumenDe(List<SocioDto> socios) {
        BigDecimal capital = socios.stream().map(SocioDto::aporte).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal porcentaje = sumaPorcentajes(socios);
        BigDecimal diferencia = CIEN.subtract(porcentaje);
        return new ResumenSocietarioDto(socios, capital, porcentaje, diferencia, diferencia.signum() == 0);
    }

    private static BigDecimal sumaPorcentajes(List<SocioDto> socios) {
        return socios.stream().map(SocioDto::porcentaje).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static String mensajeDiferencia(BigDecimal suma) {
        BigDecimal diff = CIEN.subtract(suma);
        String detalle = diff.signum() > 0
                ? "faltan " + diff.toPlainString() + "% para llegar a 100.00%"
                : "sobran " + diff.negate().toPlainString() + "% sobre 100.00%";
        return "La suma de participaciones debe ser exactamente 100.00%: actual " + suma.toPlainString() + "%, " + detalle;
    }
}
