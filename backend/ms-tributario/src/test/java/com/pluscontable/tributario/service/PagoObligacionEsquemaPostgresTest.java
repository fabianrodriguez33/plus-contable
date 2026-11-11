package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.model.PagoObligacion;
import com.pluscontable.tributario.repository.ObligacionRepository;
import com.pluscontable.tributario.repository.PagoObligacionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * HU-13: la tabla {@code pagos_obligacion} y el CHECK de estado de {@code obligaciones} que crea schema.sql (requiere
 * PostgreSQL de pruebas). Usa un cliente ficticio que se borra al terminar; obligaciones y pagos se borran en cascada.
 */
@SpringBootTest
class PagoObligacionEsquemaPostgresTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired PagoObligacionRepository pagos;
    @Autowired ObligacionRepository obligaciones;
    @Autowired TransactionTemplate transaccion;

    private String ruc;
    private long cliente;
    private long obligacion;

    @BeforeEach
    void crearClienteYObligacion() {
        ruc = RucFicticio.nuevo();
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario) VALUES (?, ?, 'MYPE')",
                ruc, "Cliente ficticio (pagos)");
        cliente = jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
        obligacion = nuevaObligacion("IGV", LocalDate.of(2026, 10, 1));
    }

    @AfterEach
    void borrarCliente() {
        jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
    }

    private long nuevaObligacion(String tipo, LocalDate periodo) {
        jdbc.update("""
                INSERT INTO tributario_schema.obligaciones (cliente_id, tipo_obligacion, periodo, fecha_vencimiento, estado, fecha_declaracion, numero_formulario, monto_determinado)
                VALUES (?, ?, ?, ?, 'DECLARADO', ?, 'F-1', 1000.00)
                """, cliente, tipo, periodo, periodo.plusDays(50), periodo.plusDays(40));
        return jdbc.queryForObject("SELECT id FROM tributario_schema.obligaciones WHERE cliente_id = ? AND tipo_obligacion = ? AND periodo = ?",
                Long.class, cliente, tipo, periodo);
    }

    private int insertar(long obligacionId, String monto, String constancia, String acumulado, String saldo) {
        return jdbc.update("""
                INSERT INTO tributario_schema.pagos_obligacion (obligacion_id, fecha_pago, monto_pagado, numero_constancia, nombre_archivo, tamano_bytes, ruta_almacenamiento, acumulado_despues, saldo_despues)
                VALUES (?, ?, ?, ?, 'c.pdf', 10, 'pagos/1/x.pdf', ?, ?)
                """, obligacionId, LocalDate.of(2026, 11, 8), new BigDecimal(monto), constancia, new BigDecimal(acumulado), new BigDecimal(saldo));
    }

    private int filas() {
        return jdbc.queryForObject("SELECT count(*) FROM tributario_schema.pagos_obligacion WHERE obligacion_id = ?", Integer.class, obligacion);
    }

    @Test
    void laTablaTieneLasColumnasDelDiseno() {
        List<String> columnas = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'tributario_schema' AND table_name = 'pagos_obligacion'
                """, String.class);

        assertThat(columnas).containsExactlyInAnyOrder("id", "obligacion_id", "fecha_pago", "monto_pagado", "numero_constancia",
                "nombre_archivo", "content_type", "tamano_bytes", "ruta_almacenamiento", "acumulado_despues", "saldo_despues", "registrado_en");
    }

    @Test
    void unaObligacionAdmiteVariosPagosParcialesConSuAcumuladoYSuSaldo() {
        insertar(obligacion, "400.00", "C-001", "400.00", "600.00");
        insertar(obligacion, "350.00", "C-002", "750.00", "250.00");

        assertThat(filas()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT sum(monto_pagado) FROM tributario_schema.pagos_obligacion WHERE obligacion_id = ?", BigDecimal.class, obligacion))
                .isEqualByComparingTo("750.00");
        assertThat(jdbc.queryForObject("SELECT content_type FROM tributario_schema.pagos_obligacion WHERE numero_constancia = 'C-001' AND obligacion_id = ?", String.class, obligacion))
                .isEqualTo("application/pdf");
    }

    @Test
    void lasColumnasDelArchivoAdmitenNuloPeroLasDelPagoNo() {
        List<String> obligatorias = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'tributario_schema' AND table_name = 'pagos_obligacion' AND is_nullable = 'NO'
                """, String.class);

        assertThat(obligatorias).contains("fecha_pago", "monto_pagado", "numero_constancia", "acumulado_despues", "saldo_despues")
                .doesNotContain("nombre_archivo", "content_type", "tamano_bytes", "ruta_almacenamiento");
    }

    @Test
    void unPagoSinArchivoSeGuardaYSeLeePorJpaConSusDatosDelArchivoNulos() {
        PagoObligacion p = new PagoObligacion();
        p.setObligacionId(obligacion);
        p.setFechaPago(LocalDate.of(2026, 11, 8));
        p.setMontoPagado(new BigDecimal("100.00"));
        p.setNumeroConstancia("C-SIN-PDF");
        p.setAcumuladoDespues(new BigDecimal("100.00"));
        p.setSaldoDespues(new BigDecimal("900.00"));
        pagos.saveAndFlush(p);

        PagoObligacion leido = pagos.findByObligacionIdOrderByFechaPagoAscIdAsc(obligacion).get(0);
        assertThat(leido.getNumeroConstancia()).isEqualTo("C-SIN-PDF");
        assertThat(leido.getNombreArchivo()).isNull();
        assertThat(leido.getTamanoBytes()).isNull();
        assertThat(leido.getRutaAlmacenamiento()).isNull();
    }

    @Test
    void unaBaseAnteriorConLasColumnasDelArchivoObligatoriasSeActualizaYConservaSusPagos() {
        insertar(obligacion, "400.00", "C-001", "400.00", "600.00");
        for (String c : List.of("nombre_archivo", "content_type", "tamano_bytes", "ruta_almacenamiento")) {
            jdbc.execute("ALTER TABLE tributario_schema.pagos_obligacion ALTER COLUMN " + c + " SET NOT NULL");
        }
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tributario_schema.pagos_obligacion (obligacion_id, fecha_pago, monto_pagado, numero_constancia, acumulado_despues, saldo_despues) VALUES (?, ?, 1, 'C-SIN', 1, 999)",
                obligacion, LocalDate.of(2026, 11, 8))).isInstanceOf(DataIntegrityViolationException.class);

        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("schema.sql"))
                .execute(jdbc.getDataSource());

        assertThat(jdbc.update("INSERT INTO tributario_schema.pagos_obligacion (obligacion_id, fecha_pago, monto_pagado, numero_constancia, acumulado_despues, saldo_despues) VALUES (?, ?, 1, 'C-SIN', 401, 599)",
                obligacion, LocalDate.of(2026, 11, 9))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT nombre_archivo FROM tributario_schema.pagos_obligacion WHERE numero_constancia = 'C-001' AND obligacion_id = ?", String.class, obligacion))
                .isEqualTo("c.pdf");
    }

    @Test
    void elMontoDebeSerMayorQueCeroYElSaldoNoPuedeSerNegativo() {
        assertThatThrownBy(() -> insertar(obligacion, "0", "C-001", "0", "1000.00")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar(obligacion, "-1.00", "C-001", "0", "1000.00")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar(obligacion, "1100.00", "C-001", "1100.00", "-100.00")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(filas()).isZero();
    }

    @Test
    void elMismoNumeroDeConstanciaNoSeRepiteEnUnaObligacionPeroSiEnOtra() {
        insertar(obligacion, "100.00", "C-001", "100.00", "900.00");
        long otra = nuevaObligacion("RENTA", LocalDate.of(2026, 10, 1));

        assertThatThrownBy(() -> insertar(obligacion, "100.00", "C-001", "200.00", "800.00")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(insertar(otra, "100.00", "C-001", "100.00", "900.00")).isEqualTo(1);
    }

    @Test
    void elEstadoDeLaObligacionAdmitePagoParcialYPagadoYRechazaOtros() {
        assertThat(jdbc.update("UPDATE tributario_schema.obligaciones SET estado = 'PAGO_PARCIAL' WHERE id = ?", obligacion)).isEqualTo(1);
        assertThat(jdbc.update("UPDATE tributario_schema.obligaciones SET estado = 'PAGADO' WHERE id = ?", obligacion)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE tributario_schema.obligaciones SET estado = 'VENCIDA' WHERE id = ?", obligacion))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laRestriccionDeEstadoTieneNombreEstableYYaNoQuedaLaEnLineaSinNombre() {
        List<String> restricciones = jdbc.queryForList("""
                SELECT conname FROM pg_constraint
                WHERE conrelid = 'tributario_schema.obligaciones'::regclass AND contype = 'c'
                """, String.class);

        assertThat(restricciones).contains("ck_obligaciones_estado").doesNotContain("obligaciones_estado_check");
    }

    @Test
    void laEntidadYElRepositorioSumanContarYBuscanPorConstancia() {
        guardar("C-001", "400.00", "400.00", "600.00");
        guardar("C-002", "100.50", "500.50", "499.50");

        assertThat(pagos.sumaPagada(obligacion)).isEqualByComparingTo("500.50");
        assertThat(pagos.countByObligacionId(obligacion)).isEqualTo(2);
        assertThat(pagos.existsByObligacionIdAndNumeroConstancia(obligacion, "C-002")).isTrue();
        assertThat(pagos.existsByObligacionIdAndNumeroConstancia(obligacion, "C-999")).isFalse();
        assertThat(pagos.findByObligacionIdOrderByFechaPagoAscIdAsc(obligacion)).extracting(PagoObligacion::getNumeroConstancia)
                .containsExactly("C-001", "C-002");
    }

    @Test
    void sinPagosElAcumuladoEsCero() {
        assertThat(pagos.sumaPagada(obligacion)).isEqualByComparingTo("0");
        assertThat(pagos.countByObligacionId(obligacion)).isZero();
    }

    @Test
    void buscarParaActualizarDevuelveLaObligacionDentroDeUnaTransaccion() {
        Long id = transaccion.execute(estado -> obligaciones.buscarParaActualizar(obligacion).orElseThrow().getId());

        assertThat(id).isEqualTo(obligacion);
    }

    @Test
    void alBorrarLaObligacionSeBorranSusPagos() {
        insertar(obligacion, "100.00", "C-001", "100.00", "900.00");
        assertThat(filas()).isEqualTo(1);

        jdbc.update("DELETE FROM tributario_schema.obligaciones WHERE id = ?", obligacion);

        assertThat(filas()).isZero();
    }

    private void guardar(String constancia, String monto, String acumulado, String saldo) {
        PagoObligacion p = new PagoObligacion();
        p.setObligacionId(obligacion);
        p.setFechaPago(LocalDate.of(2026, 11, 8));
        p.setMontoPagado(new BigDecimal(monto));
        p.setNumeroConstancia(constancia);
        p.setNombreArchivo("c.pdf");
        p.setContentType("application/pdf");
        p.setTamanoBytes(10L);
        p.setRutaAlmacenamiento("pagos/1/x.pdf");
        p.setAcumuladoDespues(new BigDecimal(acumulado));
        p.setSaldoDespues(new BigDecimal(saldo));
        pagos.saveAndFlush(p);
    }
}
