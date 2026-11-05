package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.TrabajadorDto;
import com.pluscontable.tributario.model.Trabajador;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.TrabajadorRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HU-03 · Registrar la nómina de trabajadores. */
@Service
public class TrabajadorService {

    private final TrabajadorRepository trabajadorRepository;
    private final ClienteRepository clienteRepository;

    public TrabajadorService(TrabajadorRepository trabajadorRepository, ClienteRepository clienteRepository) {
        this.trabajadorRepository = trabajadorRepository;
        this.clienteRepository = clienteRepository;
    }

    @Transactional(readOnly = true)
    public List<TrabajadorDto> listar(Long clienteId) {
        exigirCliente(clienteId);
        return trabajadorRepository.findByClienteIdOrderById(clienteId).stream().map(TrabajadorDto::from).toList();
    }

    @Transactional
    public TrabajadorDto registrar(Long clienteId, TrabajadorDto dto) {
        exigirCliente(clienteId);
        validar(dto);

        Trabajador t = new Trabajador();
        t.setClienteId(clienteId);
        t.setNombreCompleto(dto.nombreCompleto().trim());
        t.setTipoDocumento(dto.tipoDocumento());
        t.setNumeroDocumento(dto.numeroDocumento().trim());
        t.setExtranjero(dto.extranjero());
        t.setSistemaPensionario(dto.sistemaPensionario());
        t.setNombreAfp(dto.sistemaPensionario() == Trabajador.SistemaPensionario.AFP ? dto.nombreAfp().trim() : null);
        t.setFechaIngreso(dto.fechaIngreso());
        t.setRemuneracion(dto.remuneracion());
        // los datos de extranjería solo se guardan para el extranjero
        if (dto.extranjero()) {
            t.setNacionalidad(dto.nacionalidad().trim());
            t.setTipoContrato(dto.tipoContrato().trim());
            t.setFechaInicioContrato(dto.fechaInicioContrato());
            t.setFechaFinContrato(dto.fechaFinContrato());
        }
        return TrabajadorDto.from(trabajadorRepository.save(t));
    }

    /** Reúne todos los campos faltantes o inválidos y los informa en un solo error. */
    private static void validar(TrabajadorDto dto) {
        List<String> errores = new ArrayList<>();
        if (vacio(dto.nombreCompleto())) errores.add("nombreCompleto");
        if (dto.tipoDocumento() == null) errores.add("tipoDocumento");
        if (vacio(dto.numeroDocumento())) errores.add("numeroDocumento");
        if (dto.sistemaPensionario() == null) errores.add("sistemaPensionario (ONP o AFP)");
        if (dto.sistemaPensionario() == Trabajador.SistemaPensionario.AFP && vacio(dto.nombreAfp())) errores.add("nombreAfp");
        if (dto.fechaIngreso() == null) errores.add("fechaIngreso");
        if (dto.remuneracion() != null && dto.remuneracion().compareTo(BigDecimal.ZERO) < 0) {
            errores.add("remuneracion (no puede ser negativa)");
        }

        if (dto.extranjero()) {
            if (dto.tipoDocumento() == Trabajador.TipoDocumento.DNI) errores.add("tipoDocumento (CE, PASAPORTE o PTP para extranjeros)");
            if (vacio(dto.nacionalidad())) errores.add("nacionalidad");
            if (vacio(dto.tipoContrato())) errores.add("tipoContrato");
            if (dto.fechaInicioContrato() == null) errores.add("fechaInicioContrato");
            if (dto.fechaInicioContrato() != null && dto.fechaFinContrato() != null
                    && dto.fechaFinContrato().isBefore(dto.fechaInicioContrato())) {
                errores.add("fechaFinContrato (anterior al inicio)");
            }
        }

        if (!errores.isEmpty()) {
            throw new ValidationException("Faltan o son inválidos: " + String.join(", ", errores));
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private void exigirCliente(Long clienteId) {
        if (!clienteRepository.existsById(clienteId)) {
            throw new EntityNotFoundException("Cliente no encontrado");
        }
    }
}
