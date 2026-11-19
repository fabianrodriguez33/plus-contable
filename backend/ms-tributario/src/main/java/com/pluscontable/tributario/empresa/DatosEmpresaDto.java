package com.pluscontable.tributario.empresa;

import com.pluscontable.tributario.model.DocumentoLegal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * HU-35: lo que el cliente ve de su empresa. Un dato que falta viaja como null (o lista vacía) y la pantalla lo muestra como
 * «pendiente de registrar». No incluye la clave SOL ni los documentos de identidad de los socios.
 */
public record DatosEmpresaDto(Long clienteId, String ruc, String razonSocial, String regimenTributario,
                              String direccion, String telefono, String email,
                              List<SocioVista> socios, List<DocumentoVigente> documentos) {

    public record SocioVista(String nombreCompleto, String cargo, BigDecimal porcentaje) {
    }

    /** Un documento por tipo: el de versión mayor. Sin subir, id y fechaEmision son null. */
    public record DocumentoVigente(DocumentoLegal.Tipo tipo, Long id, Integer version, LocalDate fechaEmision) {
    }
}
