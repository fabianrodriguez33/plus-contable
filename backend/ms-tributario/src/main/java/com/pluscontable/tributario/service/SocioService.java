package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.ResumenSocietarioDto;
import com.pluscontable.tributario.dto.SocioDto;
import com.pluscontable.tributario.dto.SocioEntradaDto;
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
     * Reemplaza la estructura societaria completa. El porcentaje de cada socio no se recibe: se calcula según su
     * aporte respecto al capital total (ver {@link PorcentajesSocietarios}) y se verifica que la suma sea 100.00 %.
     * Cada aporte debe ser mayor que 0.
     */
    @Transactional
    public ResumenSocietarioDto reemplazar(Long clienteId, List<SocioEntradaDto> socios) {
        clienteService.buscar(clienteId);
        if (socios == null || socios.isEmpty()) {
            throw new ValidationException("Debe registrar al menos un socio");
        }
        List<BigDecimal> aportes = socios.stream().map(dto -> exigirAportePositivo(dto.nombreCompleto(), dto.aporte())).toList();
        List<BigDecimal> porcentajes = calcularYVerificar(aportes);

        socioRepository.deleteByClienteId(clienteId);
        socioRepository.flush();
        List<Socio> nuevos = new java.util.ArrayList<>();
        for (int i = 0; i < socios.size(); i++) {
            SocioEntradaDto dto = socios.get(i);
            Socio s = new Socio();
            s.setClienteId(clienteId);
            s.setNombreCompleto(dto.nombreCompleto().trim());
            s.setTipoDocumento(dto.tipoDocumento() == null || dto.tipoDocumento().isBlank() ? "DNI" : dto.tipoDocumento());
            s.setNumeroDocumento(dto.numeroDocumento().trim());
            s.setAporte(aportes.get(i));
            s.setPorcentaje(porcentajes.get(i));
            s.setCargo(dto.cargo());
            nuevos.add(s);
        }
        return resumenDe(socioRepository.saveAll(nuevos).stream().map(SocioDto::from).toList());
    }

    /**
     * Edita el aporte de un socio y recalcula el porcentaje de todos los socios del cliente, de modo que la suma
     * siga siendo 100.00 %. Si otro socio tiene aporte 0 (registro anterior a la regla automática) no se puede
     * calcular ningún porcentaje: se guarda solo el aporte y la estructura queda marcada como incompleta.
     */
    @Transactional
    public ResumenSocietarioDto actualizarAporte(Long clienteId, Long socioId, BigDecimal aporte) {
        clienteService.buscar(clienteId);
        Socio editado = socioRepository.findByIdAndClienteId(socioId, clienteId)
                .orElseThrow(() -> new EntityNotFoundException("Socio no encontrado: " + socioId));
        editado.setAporte(exigirAportePositivo(editado.getNombreCompleto(), aporte));
        List<Socio> todos = socioRepository.findByClienteIdOrderById(clienteId);

        if (todos.stream().allMatch(s -> s.getAporte().signum() > 0)) {
            List<BigDecimal> porcentajes = calcularYVerificar(todos.stream().map(Socio::getAporte).toList());
            for (int i = 0; i < todos.size(); i++) {
                todos.get(i).setPorcentaje(porcentajes.get(i));
            }
            socioRepository.saveAll(todos);
        } else {
            socioRepository.save(editado);
        }
        return resumenDe(todos.stream().map(SocioDto::from).toList());
    }

    private static BigDecimal exigirAportePositivo(String socio, BigDecimal aporte) {
        BigDecimal valor = aporte == null ? BigDecimal.ZERO : aporte.setScale(2, RoundingMode.HALF_UP);
        if (valor.signum() <= 0) {
            throw new ValidationException("El aporte de " + socio + " debe ser mayor a 0: sin aporte no se puede calcular su porcentaje");
        }
        return valor;
    }

    /** Calcula los porcentajes y comprueba que cada socio tenga al menos 0.01 % y que la suma sea 100.00 %. */
    private static List<BigDecimal> calcularYVerificar(List<BigDecimal> aportes) {
        List<BigDecimal> porcentajes = PorcentajesSocietarios.calcular(aportes);
        if (porcentajes.stream().anyMatch(p -> p.signum() <= 0)) {
            throw new ValidationException("Hay un aporte demasiado pequeño frente al capital social total: su participación sería menor a 0.01%");
        }
        BigDecimal suma = sumaPorcentajes(porcentajes);
        if (suma.compareTo(CIEN) != 0) {
            throw new IllegalStateException("Los porcentajes calculados suman " + suma.toPlainString() + "% y no 100.00%");
        }
        return porcentajes;
    }

    private static ResumenSocietarioDto resumenDe(List<SocioDto> socios) {
        BigDecimal capital = socios.stream().map(SocioDto::aporte).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal porcentaje = sumaPorcentajes(socios.stream().map(SocioDto::porcentaje).toList());
        BigDecimal diferencia = CIEN.subtract(porcentaje);
        int sinAporte = (int) socios.stream().filter(s -> s.aporte().signum() == 0).count();
        boolean completo = !socios.isEmpty() && diferencia.signum() == 0 && sinAporte == 0;
        return new ResumenSocietarioDto(socios, capital, porcentaje, diferencia, completo, sinAporte);
    }

    private static BigDecimal sumaPorcentajes(List<BigDecimal> porcentajes) {
        return porcentajes.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
}
