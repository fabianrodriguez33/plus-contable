package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.dto.DocumentoFaltanteDto;
import com.pluscontable.tributario.dto.DocumentoFaltanteEntradaDto;
import com.pluscontable.tributario.dto.HistorialItemDto;
import com.pluscontable.tributario.model.EstadoDocumentoFaltante;
import jakarta.validation.ValidationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-24 · Tareas 171 y 173: la tabla {@code documentos_faltantes} y los documentos faltantes de punta a punta contra PostgreSQL
 * de pruebas, incluida la incidencia de HU-23 (los permisos por rol se prueban en DocumentoFaltanteControllerTest).
 * Clientes ficticios que se borran al terminar, junto con las incidencias que se crean.
 */
@SpringBootTest
class DocumentoFaltantePostgresTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired DocumentoFaltanteService service;
    @Autowired HistorialService historial;

    private final List<String> rucs = new ArrayList<>();
    private final LocalDate hoy = LocalDate.now(DocumentoFaltanteService.ZONA);

    @AfterEach
    void limpiar() {
        for (String ruc : rucs) {
            // la incidencia no se borra con el cliente (solo su asociación): se borra aparte para no dejarla suelta
            jdbc.update("DELETE FROM tributario_schema.incidencias WHERE id IN (SELECT incidencia_id FROM tributario_schema.documentos_faltantes d "
                    + "JOIN tributario_schema.clientes c ON c.id = d.cliente_id WHERE c.ruc = ?)", ruc);
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
        }
    }

    private long cliente() {
        String ruc = RucFicticio.nuevo();
        rucs.add(ruc);
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario, activo) VALUES (?, 'Cliente ficticio (documentos)', 'MYPE', true)", ruc);
        return jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
    }

    /** Un documento ya vencido: se inserta directo porque por el servicio la fecha de solicitud no puede ser futura. */
    private long vencido(long cliente, String documento) {
        jdbc.update("INSERT INTO tributario_schema.documentos_faltantes (cliente_id, documento, fecha_solicitud, fecha_limite, registrado_por) VALUES (?, ?, ?, ?, 'x')",
                cliente, documento, hoy.minusDays(10), hoy.minusDays(1));
        return jdbc.queryForObject("SELECT id FROM tributario_schema.documentos_faltantes WHERE cliente_id = ? AND documento = ?", Long.class, cliente, documento);
    }

    @Test
    void criterios1a3y5_seRegistraPendienteConSuSolicitudSeVeEnLaListaYAlEntregarlaPasaAEntregado() {
        long id = cliente();
        DocumentoFaltanteDto d = service.registrar(id, new DocumentoFaltanteEntradaDto("Libro de compras", null, hoy.plusDays(7)), "contador1");
        vencido(id, "Planilla de octubre");

        List<DocumentoFaltanteDto> lista = service.listar(id);
        assertThat(lista).extracting(DocumentoFaltanteDto::documento).containsExactly("Planilla de octubre", "Libro de compras");
        assertThat(lista).extracting(DocumentoFaltanteDto::estado)
                .containsExactly(EstadoDocumentoFaltante.SIN_RESPUESTA, EstadoDocumentoFaltante.PENDIENTE);
        assertThat(lista.get(1).fechaSolicitud()).isEqualTo(hoy);

        DocumentoFaltanteDto entregado = service.registrarEntrega(id, d.id());
        assertThat(entregado.estado()).isEqualTo(EstadoDocumentoFaltante.ENTREGADO);
        assertThat(jdbc.queryForObject("SELECT estado || '|' || fecha_entrega FROM tributario_schema.documentos_faltantes WHERE id = ?",
                String.class, d.id())).isEqualTo("ENTREGADO|" + hoy);
    }

    @Test
    void criterio4_unSinRespuestaQuedaComoIncidenciaDelClienteYApareceEnSuHistorialUnaSolaVez() {
        long id = cliente();
        long doc = vencido(id, "Libro de ventas");

        DocumentoFaltanteDto r = service.registrarComoIncidencia(id, doc, "contador1");

        assertThat(r.incidenciaId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT tipo FROM tributario_schema.incidencias WHERE id = ?", String.class, r.incidenciaId()))
                .isEqualTo("CLIENTE_NO_ENVIA_DOCUMENTOS");
        assertThat(historial.delCliente(id)).anySatisfy(h -> {
            assertThat(h.origen()).isEqualTo(HistorialItemDto.Origen.INCIDENCIA);
            assertThat(h.descripcion()).contains("Libro de ventas");
        });
        assertThatThrownBy(() -> service.registrarComoIncidencia(id, doc, "contador1")).isInstanceOf(ValidationException.class);
    }

    @Test
    void dosPedidosALaVezParaRegistrarLaIncidenciaCreanSoloUna() throws Exception {
        long id = cliente();
        long doc = vencido(id, "Libro diario");
        CyclicBarrier largada = new CyclicBarrier(2);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> registrar = () -> {
                largada.await(5, TimeUnit.SECONDS);
                try {
                    service.registrarComoIncidencia(id, doc, "contador1");
                    return true;
                } catch (ValidationException e) {
                    return false; // el segundo ya ve la incidencia enlazada
                }
            };
            long exitosos = 0;
            for (Future<Boolean> r : hilos.invokeAll(List.of(registrar, registrar))) if (r.get()) exitosos++;
            assertThat(exitosos).isEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tributario_schema.incidencias WHERE descripcion LIKE '%Libro diario%'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void laTablaExigeFechaLimiteNoAnteriorAEstadosDeLaListaYEntregaSoloSiEstaEntregado() {
        long id = cliente();
        String insertar = "INSERT INTO tributario_schema.documentos_faltantes (cliente_id, documento, fecha_solicitud, fecha_limite, estado, fecha_entrega, registrado_por) VALUES (?, ?, ?, ?, ?, ?, 'x')";

        assertThatThrownBy(() -> jdbc.update(insertar, id, "A", hoy, hoy.minusDays(1), "PENDIENTE", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, id, "B", hoy, hoy, "SIN_RESPUESTA", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, id, "C", hoy, hoy, "ENTREGADO", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, id, "D", hoy, hoy, "PENDIENTE", hoy)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, id, "  ", hoy, hoy, "PENDIENTE", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tributario_schema.documentos_faltantes WHERE cliente_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void borrarLaIncidenciaDejaElDocumentoSinEnlaceYBorrarElClienteBorraSusDocumentos() {
        long id = cliente();
        long doc = vencido(id, "Constancia de pago");
        Long incidencia = service.registrarComoIncidencia(id, doc, "contador1").incidenciaId();

        jdbc.update("DELETE FROM tributario_schema.incidencias WHERE id = ?", incidencia);
        assertThat(jdbc.queryForObject("SELECT incidencia_id FROM tributario_schema.documentos_faltantes WHERE id = ?", Long.class, doc)).isNull();

        jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tributario_schema.documentos_faltantes WHERE id = ?", Integer.class, doc)).isZero();
    }
}
