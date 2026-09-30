package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.SocioDto;
import com.pluscontable.tributario.model.Socio;
import com.pluscontable.tributario.repository.SocioRepository;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
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

    /** Reemplaza la estructura societaria completa. La suma de participaciones debe ser exactamente 100.00%. */
    @Transactional
    public List<SocioDto> reemplazar(Long clienteId, List<SocioDto> socios) {
        clienteService.buscar(clienteId);
        if (socios == null || socios.isEmpty()) {
            throw new ValidationException("Debe registrar al menos un socio (la participación total debe ser 100.00%)");
        }
        BigDecimal suma = socios.stream().map(SocioDto::porcentaje)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.compareTo(CIEN) != 0) {
            throw new ValidationException("La suma de participaciones de los socios debe ser exactamente 100.00% (actual: "
                    + suma.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "%)");
        }
        socioRepository.deleteByClienteId(clienteId);
        socioRepository.flush();
        List<Socio> nuevos = socios.stream().map(dto -> {
            Socio s = new Socio();
            s.setClienteId(clienteId);
            s.setNombreCompleto(dto.nombreCompleto().trim());
            s.setTipoDocumento(dto.tipoDocumento() == null || dto.tipoDocumento().isBlank() ? "DNI" : dto.tipoDocumento());
            s.setNumeroDocumento(dto.numeroDocumento().trim());
            s.setPorcentaje(dto.porcentaje().setScale(2, java.math.RoundingMode.HALF_UP));
            s.setCargo(dto.cargo());
            return s;
        }).toList();
        return socioRepository.saveAll(nuevos).stream().map(SocioDto::from).toList();
    }
}
