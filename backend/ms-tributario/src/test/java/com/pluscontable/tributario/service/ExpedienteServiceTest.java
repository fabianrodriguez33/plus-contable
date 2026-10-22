package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.DocumentoDto;
import com.pluscontable.tributario.model.DocumentoLegal;
import com.pluscontable.tributario.model.DocumentoLegal.Tipo;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.DocumentoRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/** HU-04 · Tarea 42 (unitarias). La 43 (integración) va en un @SpringBootTest aparte. */
class ExpedienteServiceTest {

    private static final long CLIENTE = 1L;
    private static final LocalDate EMISION = LocalDate.of(2026, 9, 15);
    private static final byte[] PDF = "%PDF-1.7\n contenido de prueba".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path uploads;

    private final DocumentoRepository documentos = mock(DocumentoRepository.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private ExpedienteService service;

    @BeforeEach
    void setUp() {
        service = new ExpedienteService(documentos, clientes, uploads.toString());
        when(clientes.existsById(CLIENTE)).thenReturn(true);
        when(documentos.findTopByClienteIdAndTipoOrderByVersionDesc(any(), any())).thenReturn(Optional.empty());
        when(documentos.save(any(DocumentoLegal.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("archivo", "ficha-ruc.pdf", "application/pdf", PDF);
    }

    private long archivosGuardados() throws Exception {
        try (Stream<Path> s = Files.walk(uploads)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void pdfValidoSeArchivaConSuFechaDeEmision() throws Exception {
        DocumentoDto d = service.archivar(CLIENTE, Tipo.FICHA_RUC, EMISION, pdf());

        assertThat(d.tipo()).isEqualTo(Tipo.FICHA_RUC);
        assertThat(d.fechaEmision()).isEqualTo(EMISION);
        assertThat(d.version()).isEqualTo(1);
        assertThat(d.nombreArchivo()).isEqualTo("ficha-ruc.pdf");
        assertThat(archivosGuardados()).isEqualTo(1);
    }

    @Test
    void archivoNoPdfSeRechazaIndicandoElMotivo() throws Exception {
        var png = new MockMultipartFile("archivo", "foto.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'});

        assertThatThrownBy(() -> service.archivar(CLIENTE, Tipo.MINUTA, EMISION, png))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no es un PDF");
        verify(documentos, never()).save(any());
        assertThat(archivosGuardados()).isZero();
    }

    @Test
    void archivoConTipoPdfPeroContenidoFalsoSeRechaza() {
        var falso = new MockMultipartFile("archivo", "virus.pdf", "application/pdf", "MZ no soy pdf".getBytes());

        assertThatThrownBy(() -> service.archivar(CLIENTE, Tipo.MINUTA, EMISION, falso))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no es un PDF");
    }

    @Test
    void archivoMayorA10MbSeRechaza() {
        byte[] grande = new byte[(int) ExpedienteService.TAMANO_MAXIMO + 1];
        System.arraycopy(PDF, 0, grande, 0, PDF.length);
        var pesado = new MockMultipartFile("archivo", "grande.pdf", "application/pdf", grande);

        assertThatThrownBy(() -> service.archivar(CLIENTE, Tipo.MINUTA, EMISION, pesado))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("10 MB");
    }

    @Test
    void sinFechaDeEmisionSeRechaza() {
        assertThatThrownBy(() -> service.archivar(CLIENTE, Tipo.VIGENCIA_PODERES, null, pdf()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("fechaEmision es obligatoria");
        verify(documentos, never()).save(any());
    }

    @Test
    void fechaDeEmisionFuturaSeRechaza() {
        assertThatThrownBy(() -> service.archivar(CLIENTE, Tipo.VIGENCIA_PODERES, LocalDate.now().plusDays(1), pdf()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("futura");
    }

    @Test
    void nuevaVersionConservaLaAnteriorEnElHistorial() throws Exception {
        DocumentoLegal anterior = new DocumentoLegal();
        anterior.setVersion(1);
        when(documentos.findTopByClienteIdAndTipoOrderByVersionDesc(eq(CLIENTE), eq(Tipo.MINUTA)))
                .thenReturn(Optional.of(anterior));

        DocumentoDto d = service.archivar(CLIENTE, Tipo.MINUTA, EMISION, pdf());

        assertThat(d.version()).isEqualTo(2);
        verify(documentos, never()).delete(any());
    }

    @Test
    void seDescargaTalCualSeSubio() throws Exception {
        var guardado = new DocumentoLegal[1];
        when(documentos.save(any(DocumentoLegal.class))).thenAnswer(inv -> {
            guardado[0] = inv.getArgument(0);
            guardado[0].setId(10L);
            guardado[0].setClienteId(CLIENTE);
            return guardado[0];
        });
        service.archivar(CLIENTE, Tipo.FICHA_RUC, EMISION, pdf());
        when(documentos.findById(10L)).thenReturn(Optional.of(guardado[0]));

        var recurso = service.cargar(service.buscar(CLIENTE, 10L));

        assertThat(recurso.getContentAsByteArray()).isEqualTo(PDF);
    }

    @Test
    void documentoDeOtroClienteNoSeEncuentra() {
        DocumentoLegal ajeno = new DocumentoLegal();
        ajeno.setClienteId(2L);
        when(documentos.findById(5L)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.buscar(CLIENTE, 5L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void siFallaLaBaseDeDatosNoQuedaArchivoHuerfano() throws Exception {
        when(documentos.save(any(DocumentoLegal.class))).thenThrow(new IllegalStateException("BD caída"));

        assertThatThrownBy(() -> service.archivar(CLIENTE, Tipo.MINUTA, EMISION, pdf()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(archivosGuardados()).isZero();
    }

    @Test
    void nombreDeArchivoSinRutaDelEquipoDelUsuario() {
        var conRuta = new MockMultipartFile("archivo", "C:\\Users\\x\\minuta:v2.pdf", "application/pdf", PDF);

        assertThat(service.archivar(CLIENTE, Tipo.MINUTA, EMISION, conRuta).nombreArchivo()).isEqualTo("minuta:v2.pdf");
    }
}
