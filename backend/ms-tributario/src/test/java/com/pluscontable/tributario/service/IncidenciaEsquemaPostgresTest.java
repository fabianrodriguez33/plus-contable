package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.dto.IncidenciaDto;
import com.pluscontable.tributario.dto.IncidenciaEntradaDto;
import com.pluscontable.tributario.model.Incidencia;
import com.pluscontable.tributario.model.IncidenciaCliente;
import com.pluscontable.tributario.model.TipoIncidencia;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.IncidenciaClienteRepository;
import com.pluscontable.tributario.repository.IncidenciaRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-23 · Tarea 161: las tablas {@code incidencias} e {@code incidencias_clientes} que crea schema.sql y las consultas
 * del historial (requiere PostgreSQL de pruebas). Usa clientes ficticios que se borran al terminar; lo demás se borra en cascada.
 */
@SpringBootTest
class IncidenciaEsquemaPostgresTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired IncidenciaRepository incidencias;
    @Autowired IncidenciaClienteRepository asociaciones;
    @Autowired ClienteRepository clientesRepo;
    @Autowired IncidenciaService service;

    private final List<String> rucs = new ArrayList<>();
    private final List<Long> incidenciasCreadas = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (Long id : incidenciasCreadas) {
            jdbc.update("DELETE FROM tributario_schema.incidencias WHERE id = ?", id);
        }
        for (String ruc : rucs) {
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
        }
    }

    private long crearCliente(boolean activo) {
        String ruc = RucFicticio.nuevo();
        rucs.add(ruc);
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario, activo) VALUES (?, ?, 'MYPE', ?)",
                ruc, "Cliente ficticio (incidencias)", activo);
        return jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
    }

    private long insertarIncidencia(String tipo, String descripcion, LocalDate fecha) {
        jdbc.update("INSERT INTO tributario_schema.incidencias (tipo, fecha, descripcion, registrada_por) VALUES (?, ?, ?, 'contador1')",
                tipo, fecha, descripcion);
        long id = jdbc.queryForObject("SELECT max(id) FROM tributario_schema.incidencias", Long.class);
        incidenciasCreadas.add(id);
        return id;
    }

    private int contar(String tabla, String donde, Object... args) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema." + tabla + " WHERE " + donde, Integer.class, args);
    }

    @Test
    void laTablaSoloAceptaLosTiposDeLaLista() {
        for (TipoIncidencia t : TipoIncidencia.values()) {
            insertarIncidencia(t.name(), "ok", LocalDate.of(2026, 11, 9));
        }
        assertThatThrownBy(() -> insertarIncidencia("INVENTADO", "x", LocalDate.of(2026, 11, 9)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lasObservacionesSonOpcionalesYAdmitenHastaMilCaracteres() {
        long sin = insertarIncidencia("OTRO", "sin observaciones", LocalDate.of(2026, 11, 9));
        assertThat(jdbc.queryForObject("SELECT observaciones FROM tributario_schema.incidencias WHERE id = ?", String.class, sin)).isNull();

        jdbc.update("INSERT INTO tributario_schema.incidencias (tipo, fecha, descripcion, observaciones, registrada_por) VALUES ('PROBLEMA_SIRE', ?, 'x', ?, 'contador1')",
                LocalDate.of(2026, 11, 9), "o".repeat(1000));
        incidenciasCreadas.add(jdbc.queryForObject("SELECT max(id) FROM tributario_schema.incidencias", Long.class));
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tributario_schema.incidencias (tipo, fecha, descripcion, observaciones, registrada_por) VALUES ('OTRO', ?, 'x', ?, 'contador1')",
                LocalDate.of(2026, 11, 9), "o".repeat(1001))).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Una base creada antes de este cambio tiene la restricción de tipos con la lista vieja y no tiene la columna de
     * observaciones: al arrancar, schema.sql debe actualizarla sin perder las incidencias que ya tenía.
     */
    @Test
    void unaBaseAnteriorSeActualizaAlVolverACorrerElEsquemaYConservaSusIncidencias() throws Exception {
        long vieja = insertarIncidencia("RECHAZO_FRACCIONAMIENTO", "anterior", LocalDate.of(2026, 11, 1));
        try {
            jdbc.execute("ALTER TABLE tributario_schema.incidencias DROP CONSTRAINT IF EXISTS ck_incidencias_tipo");
            jdbc.execute("ALTER TABLE tributario_schema.incidencias DROP COLUMN IF EXISTS observaciones");
            jdbc.execute("ALTER TABLE tributario_schema.incidencias ADD CONSTRAINT incidencias_tipo_check "
                    + "CHECK (tipo IN ('CAIDA_PORTAL_SOL', 'CLIENTE_NO_ENVIA_DOCUMENTOS', 'RECHAZO_FRACCIONAMIENTO', 'OTRO'))");
            assertThatThrownBy(() -> insertarIncidencia("PROBLEMA_SIRE", "x", LocalDate.of(2026, 11, 9)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(contar("incidencias", "id = ?", vieja)).isEqualTo(1);

            volverACorrerElEsquema();

            long nueva = insertarIncidencia("PROBLEMA_SIRE", "ya admite el tipo nuevo", LocalDate.of(2026, 11, 9));
            jdbc.update("UPDATE tributario_schema.incidencias SET observaciones = 'nota' WHERE id = ?", nueva);
            assertThat(jdbc.queryForObject("SELECT observaciones FROM tributario_schema.incidencias WHERE id = ?", String.class, nueva)).isEqualTo("nota");
            assertThat(jdbc.queryForObject("SELECT tipo || '|' || descripcion FROM tributario_schema.incidencias WHERE id = ?", String.class, vieja))
                    .isEqualTo("RECHAZO_FRACCIONAMIENTO|anterior");
            assertThatThrownBy(() -> insertarIncidencia("INVENTADO", "x", LocalDate.of(2026, 11, 9)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            volverACorrerElEsquema(); // deja la base como la dejó el arranque, pase lo que pase
        }
    }

    private void volverACorrerElEsquema() throws Exception {
        try (java.sql.Connection c = jdbc.getDataSource().getConnection()) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c, new org.springframework.core.io.ClassPathResource("schema.sql"));
        }
    }

    @Test
    void laDescripcionNoPuedeEstarVaciaNiPasarDeMilCaracteres() {
        assertThatThrownBy(() -> insertarIncidencia("OTRO", "   ", LocalDate.of(2026, 11, 9)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarIncidencia("OTRO", "x".repeat(1001), LocalDate.of(2026, 11, 9)))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertarIncidencia("OTRO", "x".repeat(1000), LocalDate.of(2026, 11, 9));
    }

    @Test
    void unClienteNoSeAsociaDosVecesALaMismaIncidenciaYLasClavesForaneasSeExigen() {
        long cliente = crearCliente(true);
        long inc = insertarIncidencia("OTRO", "x", LocalDate.of(2026, 11, 9));
        jdbc.update("INSERT INTO tributario_schema.incidencias_clientes (incidencia_id, cliente_id) VALUES (?, ?)", inc, cliente);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO tributario_schema.incidencias_clientes (incidencia_id, cliente_id) VALUES (?, ?)", inc, cliente))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tributario_schema.incidencias_clientes (incidencia_id, cliente_id) VALUES (?, ?)", inc, 999_999_999L))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tributario_schema.incidencias_clientes (incidencia_id, cliente_id) VALUES (?, ?)", 999_999_999L, cliente))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void alBorrarLaIncidenciaOElClienteSeBorranSusAsociaciones() {
        long cliente = crearCliente(true);
        long inc = insertarIncidencia("OTRO", "x", LocalDate.of(2026, 11, 9));
        jdbc.update("INSERT INTO tributario_schema.incidencias_clientes (incidencia_id, cliente_id) VALUES (?, ?)", inc, cliente);

        jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", cliente);
        assertThat(contar("incidencias_clientes", "incidencia_id = ?", inc)).isZero();
        assertThat(contar("incidencias", "id = ?", inc)).isEqualTo(1);

        long otro = crearCliente(true);
        jdbc.update("INSERT INTO tributario_schema.incidencias_clientes (incidencia_id, cliente_id) VALUES (?, ?)", inc, otro);
        jdbc.update("DELETE FROM tributario_schema.incidencias WHERE id = ?", inc);
        assertThat(contar("incidencias_clientes", "cliente_id = ?", otro)).isZero();
    }

    @Test
    void elHistorialDelClienteVieneDeLaMasRecienteALaMasAntiguaConPropiasYGenerales() {
        long cliente = crearCliente(true);
        long otro = crearCliente(true);
        long vieja = insertarIncidencia("OTRO", "vieja", LocalDate.of(2026, 10, 1));
        long reciente = insertarIncidencia("CLIENTE_NO_ENVIA_DOCUMENTOS", "reciente", LocalDate.of(2026, 11, 8));
        long general = insertarIncidencia("CAIDA_PORTAL_SOL", "general", LocalDate.of(2026, 11, 5));
        long ajena = insertarIncidencia("OTRO", "de otro cliente", LocalDate.of(2026, 11, 9));
        for (long[] par : new long[][]{{vieja, cliente}, {reciente, cliente}, {general, cliente}, {general, otro}, {ajena, otro}}) {
            asociaciones.save(new IncidenciaCliente(par[0], par[1]));
        }

        List<Incidencia> r = incidencias.findByCliente(cliente);

        assertThat(r).extracting(Incidencia::getId).containsExactly(reciente, general, vieja);
        assertThat(asociaciones.contarPorIncidencia(List.of(general, reciente)))
                .extracting(f -> (Long) f[0], f -> ((Number) f[1]).intValue())
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(general, 2), org.assertj.core.groups.Tuple.tuple(reciente, 1));
    }

    @Test
    void laCarteraActivaNoIncluyeLosClientesInactivos() {
        long activo = crearCliente(true);
        long inactivo = crearCliente(false);

        assertThat(clientesRepo.findByActivoTrue()).extracting(c -> c.getId()).contains(activo).doesNotContain(inactivo);
    }

    @Test
    void registrarUnaGeneralAsociaALosClientesActivosAlMomentoYElHistorialLaMuestra() {
        long activo = crearCliente(true);
        long inactivo = crearCliente(false);
        int activosAntes = clientesRepo.findByActivoTrue().size();

        IncidenciaDto r = service.registrar(new IncidenciaEntradaDto(TipoIncidencia.CAIDA_PORTAL_SOL, null,
                LocalDate.now(), "No abrió el portal SOL"), "contador1");
        incidenciasCreadas.add(r.id());

        assertThat(r.clientesAsociados()).isEqualTo(activosAntes);
        assertThat(contar("incidencias_clientes", "incidencia_id = ? AND cliente_id = ?", r.id(), activo)).isEqualTo(1);
        assertThat(contar("incidencias_clientes", "incidencia_id = ? AND cliente_id = ?", r.id(), inactivo)).isZero();
        assertThat(service.delCliente(activo)).extracting(IncidenciaDto::id).containsExactly(r.id());
        assertThat(service.delCliente(inactivo)).isEmpty();
    }

    @Test
    void registrarConObservacionesLasGuardaYElHistorialLasDevuelve() {
        long cliente = crearCliente(true);

        IncidenciaDto r = service.registrar(new IncidenciaEntradaDto(TipoIncidencia.FALLA_SISTEMA_INTERNO, cliente,
                LocalDate.now(), "El sistema se cerró", "  Se reinició el servidor  "), "contador1");
        incidenciasCreadas.add(r.id());

        assertThat(jdbc.queryForObject("SELECT observaciones FROM tributario_schema.incidencias WHERE id = ?", String.class, r.id()))
                .isEqualTo("Se reinició el servidor");
        assertThat(service.delCliente(cliente)).singleElement().satisfies(i -> {
            assertThat(i.observaciones()).isEqualTo("Se reinició el servidor");
            assertThat(i.tipo()).isEqualTo(TipoIncidencia.FALLA_SISTEMA_INTERNO);
        });
    }

    @Test
    void registrarUnaDeUnClienteLaDejaGuardadaConSuUsuarioYSuHistorial() {
        long cliente = crearCliente(true);

        IncidenciaDto r = service.registrar(new IncidenciaEntradaDto(TipoIncidencia.RECHAZO_FRACCIONAMIENTO, cliente,
                LocalDate.now(), "  SUNAT rechazó el fraccionamiento  "), "contador1");
        incidenciasCreadas.add(r.id());

        assertThat(r.clientesAsociados()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT registrada_por || '|' || descripcion FROM tributario_schema.incidencias WHERE id = ?",
                String.class, r.id())).isEqualTo("contador1|SUNAT rechazó el fraccionamiento");
        assertThat(service.delCliente(cliente)).singleElement().satisfies(i -> {
            assertThat(i.id()).isEqualTo(r.id());
            assertThat(i.clientesAsociados()).isEqualTo(1);
        });
    }
}
