package com.pluscontable.tributario.empresa;

import com.pluscontable.tributario.dto.DocumentoDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.DocumentoLegal;
import com.pluscontable.tributario.service.ClienteService;
import com.pluscontable.tributario.service.ExpedienteService;
import com.pluscontable.tributario.service.SocioService;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HU-35: solo lectura; reúne datos que ya guardan ClienteService, SocioService y ExpedienteService. */
@Service
public class DatosEmpresaService {
    private final ClienteService clientes;
    private final SocioService socios;
    private final ExpedienteService expediente;

    public DatosEmpresaService(ClienteService clientes, SocioService socios, ExpedienteService expediente) {
        this.clientes = clientes;
        this.socios = socios;
        this.expediente = expediente;
    }

    @Transactional(readOnly = true)
    public DatosEmpresaDto obtener(Long clienteId) {
        Cliente c = clientes.buscar(clienteId);
        var sociosVista = socios.listar(clienteId).stream()
                .map(s -> new DatosEmpresaDto.SocioVista(s.nombreCompleto(), vacioANulo(s.cargo()), s.porcentaje())).toList();
        // listar() ordena por tipo y versión descendente: la primera de cada tipo es la vigente
        List<DocumentoDto> todos = expediente.listar(clienteId);
        var documentos = Arrays.stream(DocumentoLegal.Tipo.values()).map(t -> todos.stream().filter(d -> d.tipo() == t).findFirst()
                .map(d -> new DatosEmpresaDto.DocumentoVigente(t, d.id(), d.version(), d.fechaEmision()))
                .orElse(new DatosEmpresaDto.DocumentoVigente(t, null, null, null))).toList();
        return new DatosEmpresaDto(c.getId(), c.getRuc(), c.getRazonSocial(), c.getRegimenTributario().name(),
                vacioANulo(c.getDireccion()), vacioANulo(c.getTelefono()), vacioANulo(c.getEmail()), sociosVista, documentos);
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
