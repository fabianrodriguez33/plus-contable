package com.pluscontable.tributario.empresa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.DocumentoDto;
import com.pluscontable.tributario.dto.SocioDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.DocumentoLegal.Tipo;
import com.pluscontable.tributario.service.ClienteService;
import com.pluscontable.tributario.service.ExpedienteService;
import com.pluscontable.tributario.service.SocioService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** HU-35, reglas de lo que ve el cliente: régimen, socios y documentos vigentes, y lo que falta como dato vacío. */
class DatosEmpresaServiceTest {
    private final ClienteService clientes = mock(ClienteService.class);
    private final SocioService socios = mock(SocioService.class);
    private final ExpedienteService expediente = mock(ExpedienteService.class);
    private final DatosEmpresaService service = new DatosEmpresaService(clientes, socios, expediente);

    private Cliente cliente(String direccion) {
        Cliente c = new Cliente();
        c.setId(4L);
        c.setRuc("20123456789");
        c.setRazonSocial("Acme SAC");
        c.setRegimenTributario(Cliente.Regimen.RER);
        c.setDireccion(direccion);
        c.setEmail("   ");
        return c;
    }

    private DocumentoDto doc(long id, Tipo tipo, int version, String emision) {
        return new DocumentoDto(id, tipo, version, LocalDate.parse(emision), "a.pdf", 10, LocalDateTime.now());
    }

    @Test
    void criterio1_muestraRegimenSociosYLaVersionVigenteDeCadaDocumento() {
        when(clientes.buscar(4L)).thenReturn(cliente("Av. Lima 123"));
        when(socios.listar(4L)).thenReturn(List.of(
                new SocioDto(1L, "Ana Pérez", "DNI", "11111111", new BigDecimal("6000"), new BigDecimal("60.00"), "Gerente"),
                new SocioDto(2L, "Luis Díaz", "DNI", "22222222", new BigDecimal("4000"), new BigDecimal("40.00"), null)));
        // el servicio de expediente entrega cada tipo con la versión mayor primero
        when(expediente.listar(4L)).thenReturn(List.of(
                doc(9, Tipo.FICHA_RUC, 2, "2026-10-01"), doc(5, Tipo.FICHA_RUC, 1, "2026-01-01"), doc(7, Tipo.MINUTA, 1, "2026-02-02")));

        var datos = service.obtener(4L);

        assertThat(datos.regimenTributario()).isEqualTo("RER");
        assertThat(datos.socios()).extracting(DatosEmpresaDto.SocioVista::nombreCompleto, DatosEmpresaDto.SocioVista::porcentaje)
                .containsExactly(tuple("Ana Pérez", new BigDecimal("60.00")), tuple("Luis Díaz", new BigDecimal("40.00")));
        assertThat(datos.documentos()).extracting(DatosEmpresaDto.DocumentoVigente::tipo, DatosEmpresaDto.DocumentoVigente::id)
                .containsExactly(tuple(Tipo.MINUTA, 7L), tuple(Tipo.VIGENCIA_PODERES, null), tuple(Tipo.FICHA_RUC, 9L));
    }

    @Test
    void criterio3_loQueFaltaViajaVacioParaMostrarloComoPendienteDeRegistrar() {
        when(clientes.buscar(4L)).thenReturn(cliente(null));
        when(socios.listar(4L)).thenReturn(List.of());
        when(expediente.listar(4L)).thenReturn(List.of());

        var datos = service.obtener(4L);

        assertThat(datos.direccion()).isNull();
        assertThat(datos.telefono()).isNull();
        assertThat(datos.email()).isNull(); // en blanco cuenta como faltante
        assertThat(datos.socios()).isEmpty();
        assertThat(datos.documentos()).hasSize(Tipo.values().length).allSatisfy(d -> assertThat(d.id()).isNull());
    }

    @Test
    void cargoDireccionYTelefonoVaciosOEnBlancoViajanComoNuloYLosConDatoSeRecortan() {
        Cliente c = cliente("   ");
        c.setTelefono("");
        when(clientes.buscar(4L)).thenReturn(c);
        when(socios.listar(4L)).thenReturn(List.of(
                new SocioDto(1L, "Ana Pérez", "DNI", "11111111", BigDecimal.ONE, new BigDecimal("50.00"), "  "),
                new SocioDto(2L, "Luis Díaz", "DNI", "22222222", BigDecimal.ONE, new BigDecimal("50.00"), " Gerente ")));
        when(expediente.listar(4L)).thenReturn(List.of());

        var datos = service.obtener(4L);

        assertThat(datos.direccion()).isNull();
        assertThat(datos.telefono()).isNull();
        assertThat(datos.socios()).extracting(DatosEmpresaDto.SocioVista::cargo).containsExactly(null, "Gerente");
    }

    @Test
    void noExponeDocumentosDeIdentidadDeLosSocios() {
        assertThat(DatosEmpresaDto.SocioVista.class.getRecordComponents()).extracting("name")
                .containsExactly("nombreCompleto", "cargo", "porcentaje");
    }
}
