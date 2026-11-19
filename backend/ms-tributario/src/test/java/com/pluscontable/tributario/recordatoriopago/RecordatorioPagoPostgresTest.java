package com.pluscontable.tributario.recordatoriopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** HU-15 con PostgreSQL desechable: programar, entregar el día elegido y no enviar lo que ya no corresponde. */
@SpringBootTest
class RecordatorioPagoPostgresTest {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 18);
    private static final LocalDate VENCE = LocalDate.of(2026, 11, 30);

    @Autowired JdbcTemplate jdbc;
    private final List<Long> clientes = new ArrayList<>();
    private int mes;

    private RecordatorioPagoService servicio(LocalDate hoy) {
        return new RecordatorioPagoService(jdbc, Clock.fixed(hoy.atTime(12, 0).atZone(LIMA).toInstant(), LIMA));
    }

    @AfterEach
    void limpiar() {
        for (Long id : clientes) jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", id);
    }

    private long cliente(boolean activo) {
        String ruc = "20" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
        Long id = jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes(ruc, razon_social, regimen_tributario, activo)
                VALUES (?, 'Cliente HU15 SAC', 'MYPE', ?) RETURNING id""", Long.class, ruc, activo);
        clientes.add(id);
        return id;
    }

    private long obligacion(long cliente, String estado, String monto) {
        LocalDate periodo = LocalDate.of(2020, 1, 1).plusMonths(mes++);
        return jdbc.queryForObject("""
                INSERT INTO tributario_schema.obligaciones(cliente_id, tipo_obligacion, periodo, fecha_vencimiento, estado, monto_determinado)
                VALUES (?, 'IGV', ?, ?, ?, ?::numeric) RETURNING id""", Long.class, cliente, periodo, VENCE, estado, monto);
    }

    private void pago(long obligacion, String monto, String constancia) {
        jdbc.update("""
                INSERT INTO tributario_schema.pagos_obligacion(obligacion_id, fecha_pago, monto_pagado, numero_constancia, acumulado_despues, saldo_despues)
                VALUES (?, '2026-11-10', ?::numeric, ?, ?::numeric, 0)""", obligacion, monto, constancia, monto);
    }

    private long declarada() {
        return obligacion(cliente(true), "DECLARADO", "1000.00");
    }

    private List<String> avisos(long obligacion) {
        return jdbc.queryForList("SELECT tributo || '|' || monto || '|' || fecha_vencimiento || '|' || fecha_aviso FROM tributario_schema.avisos_cliente WHERE obligacion_id = ?",
                String.class, obligacion);
    }

    private String estado(long id) {
        return jdbc.queryForObject("SELECT estado FROM tributario_schema.recordatorios_pago WHERE id = ?", String.class, id);
    }

    @Test
    void programaSobreUnaDeclaradaSinPagosYGuardaQuienLoProgramo() {
        long o = declarada();
        var r = servicio(HOY).programar(o, HOY.plusDays(3), "contador1");
        assertThat(r.estado()).isEqualTo("PROGRAMADO");
        assertThat(r.fechaRecordatorio()).isEqualTo(HOY.plusDays(3));
        assertThat(r.programadoPor()).isEqualTo("contador1");
        assertThat(r.resueltoEn()).isNull();
        assertThat(servicio(HOY).listar(o)).containsExactly(r);
        servicio(HOY).programar(o, VENCE, "contador1"); // el último día (el de vencimiento) también vale
        assertThat(servicio(HOY).listar(o)).hasSize(2);
    }

    @Test
    void noSeProgramaSobrePendienteParcialPagadaNiConPagos() {
        long cli = cliente(true);
        long pendiente = obligacion(cli, "PENDIENTE", "1000.00");
        long parcial = obligacion(cli, "PAGO_PARCIAL", "1000.00");
        pago(parcial, "100.00", "C-1");
        long pagada = obligacion(cli, "PAGADO", "1000.00");
        pago(pagada, "1000.00", "C-2");
        long declaradaConPago = obligacion(cli, "DECLARADO", "1000.00");
        pago(declaradaConPago, "50.00", "C-3");

        assertThatThrownBy(() -> servicio(HOY).programar(pendiente, VENCE, "c")).hasMessageContaining("no está declarada");
        assertThatThrownBy(() -> servicio(HOY).programar(parcial, VENCE, "c")).hasMessageContaining("ya tiene pagos");
        assertThatThrownBy(() -> servicio(HOY).programar(pagada, VENCE, "c")).hasMessageContaining("ya está pagada");
        assertThatThrownBy(() -> servicio(HOY).programar(declaradaConPago, VENCE, "c")).hasMessageContaining("ya tiene pagos");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.recordatorios_pago WHERE obligacion_id IN (?,?,?,?)",
                Integer.class, pendiente, parcial, pagada, declaradaConPago)).isZero();
    }

    @Test
    void validaFechasMontoClienteYDuplicadoReuniendoLosErrores() {
        long o = declarada();
        var s = servicio(HOY);
        assertThatThrownBy(() -> s.programar(o, HOY.minusDays(1), "c")).isInstanceOf(ValidationException.class).hasMessageContaining("fecha pasada");
        assertThatThrownBy(() -> s.programar(o, VENCE.plusDays(1), "c")).hasMessageContaining("posterior a la fecha de vencimiento");
        assertThatThrownBy(() -> s.programar(o, null, "c")).hasMessageContaining("fechaRecordatorio es obligatoria");

        s.programar(o, HOY.plusDays(2), "c");
        assertThatThrownBy(() -> s.programar(o, HOY.plusDays(2), "c")).hasMessageContaining("Ya hay un recordatorio programado");

        long sinMonto = obligacion(cliente(true), "DECLARADO", null);
        long inactivo = obligacion(cliente(false), "DECLARADO", "1000.00");
        assertThatThrownBy(() -> s.programar(sinMonto, VENCE, "c")).hasMessageContaining("no tiene monto determinado");
        assertThatThrownBy(() -> s.programar(inactivo, VENCE, "c")).hasMessageContaining("inactivo");
        assertThatThrownBy(() -> s.programar(sinMonto, HOY.minusDays(1), "c")).hasMessageContaining("monto determinado").hasMessageContaining("fecha pasada");
        assertThatThrownBy(() -> s.programar(-1L, VENCE, "c")).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void elDiaElegidoSeEntregaAlClienteConSaldoTributoYFechas_yRepetirNoDuplica() {
        long o = declarada();
        var r = servicio(HOY).programar(o, HOY.plusDays(2), "c");
        var dia = HOY.plusDays(2);

        assertThat(servicio(dia).entregar(dia)).isEqualTo(new RecordatorioPagoService.Resultado(1, 0));
        assertThat(avisos(o)).containsExactly("IGV|1000.00|" + VENCE + "|" + dia);
        assertThat(estado(r.id())).isEqualTo("ENVIADO");
        assertThat(jdbc.queryForObject("SELECT resuelto_en IS NOT NULL FROM tributario_schema.recordatorios_pago WHERE id = ?", Boolean.class, r.id())).isTrue();

        assertThat(servicio(dia).entregar(dia)).isEqualTo(new RecordatorioPagoService.Resultado(0, 0));
        assertThat(avisos(o)).hasSize(1);
    }

    @Test
    void antesDeSuFechaNoSeEntregaYSigueProgramado() {
        long o = declarada();
        var r = servicio(HOY).programar(o, HOY.plusDays(5), "c");
        assertThat(servicio(HOY).entregar(HOY)).isEqualTo(new RecordatorioPagoService.Resultado(0, 0));
        assertThat(avisos(o)).isEmpty();
        assertThat(estado(r.id())).isEqualTo("PROGRAMADO");
    }

    @Test
    void siSePagoDespuesDeProgramarNoSeEnvia_pagoCompletoOParcial() {
        long cli = cliente(true);
        long pagada = obligacion(cli, "DECLARADO", "1000.00");
        long parcial = obligacion(cli, "DECLARADO", "1000.00");
        var dia = HOY.plusDays(1);
        var r1 = servicio(HOY).programar(pagada, dia, "c");
        var r2 = servicio(HOY).programar(parcial, dia, "c");
        pago(pagada, "1000.00", "C-9");
        jdbc.update("UPDATE tributario_schema.obligaciones SET estado = 'PAGADO' WHERE id = ?", pagada);
        pago(parcial, "100.00", "C-8");
        jdbc.update("UPDATE tributario_schema.obligaciones SET estado = 'PAGO_PARCIAL' WHERE id = ?", parcial);

        assertThat(servicio(dia).entregar(dia)).isEqualTo(new RecordatorioPagoService.Resultado(0, 2));
        assertThat(avisos(pagada)).isEmpty();
        assertThat(avisos(parcial)).isEmpty();
        assertThat(estado(r1.id())).isEqualTo("OMITIDO");
        assertThat(estado(r2.id())).isEqualTo("OMITIDO");
    }

    @Test
    void siElJobNoCorrioEnSuDiaQuedaOmitidoYNoSeEnviaTarde() {
        long o = declarada();
        var r = servicio(HOY).programar(o, HOY.plusDays(1), "c");
        var tarde = HOY.plusDays(3);
        assertThat(servicio(tarde).entregar(tarde)).isEqualTo(new RecordatorioPagoService.Resultado(0, 1));
        assertThat(avisos(o)).isEmpty();
        assertThat(estado(r.id())).isEqualTo("OMITIDO");
    }

    @Test
    void unRecordatorioCanceladoNoSeEnviaYUnoYaEnviadoNoSePuedeCancelar() {
        long o = declarada();
        var dia = HOY.plusDays(1);
        var cancelado = servicio(HOY).programar(o, dia, "c");
        assertThat(servicio(HOY).cancelar(o, cancelado.id()).estado()).isEqualTo("CANCELADO");
        assertThat(servicio(dia).entregar(dia)).isEqualTo(new RecordatorioPagoService.Resultado(0, 0));
        assertThat(avisos(o)).isEmpty();
        assertThatThrownBy(() -> servicio(HOY).cancelar(o, cancelado.id())).hasMessageContaining("Solo se cancela un recordatorio programado");

        var otro = servicio(HOY).programar(o, dia, "c"); // se puede reprogramar la misma fecha tras cancelar
        servicio(dia).entregar(dia);
        assertThat(estado(otro.id())).isEqualTo("ENVIADO");
        assertThatThrownBy(() -> servicio(dia).cancelar(o, otro.id())).hasMessageContaining("Solo se cancela un recordatorio programado");
        assertThatThrownBy(() -> servicio(dia).cancelar(o, -5L)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> servicio(dia).cancelar(declarada(), otro.id())).isInstanceOf(EntityNotFoundException.class); // de otra obligación
    }

    @Test
    void siElAvisoAutomaticoDeHu33YaSalioHoyNoSeDuplicaYElRecordatorioQuedaEnviado() {
        long o = declarada();
        var dia = HOY.plusDays(1);
        var r = servicio(HOY).programar(o, dia, "c");
        long cli = jdbc.queryForObject("SELECT cliente_id FROM tributario_schema.obligaciones WHERE id = ?", Long.class, o);
        jdbc.update("""
                INSERT INTO tributario_schema.avisos_cliente(cliente_id, obligacion_id, tributo, monto, fecha_vencimiento, fecha_aviso)
                VALUES (?, ?, 'IGV', 1000.00, ?, ?)""", cli, o, VENCE, dia);

        servicio(dia).entregar(dia);
        assertThat(avisos(o)).hasSize(1);
        assertThat(estado(r.id())).isEqualTo("ENVIADO");
    }

    @Test
    void siElClienteQuedoInactivoNoSeEnvia() {
        long cli = cliente(true);
        long o = obligacion(cli, "DECLARADO", "1000.00");
        var dia = HOY.plusDays(1);
        var r = servicio(HOY).programar(o, dia, "c");
        jdbc.update("UPDATE tributario_schema.clientes SET activo = false WHERE id = ?", cli);
        assertThat(servicio(dia).entregar(dia)).isEqualTo(new RecordatorioPagoService.Resultado(0, 1));
        assertThat(avisos(o)).isEmpty();
        assertThat(estado(r.id())).isEqualTo("OMITIDO");
    }

    @Test
    void hoyEsElDeLimaAunqueElServidorEsteEnUtc() {
        long o = declarada();
        // 18/11/2026 22:30 en Lima ya es 19/11 en UTC: programar «hoy» (18/11) debe aceptarse.
        var tarde = new RecordatorioPagoService(jdbc, Clock.fixed(LocalDate.of(2026, 11, 18).atTime(22, 30).atZone(LIMA).toInstant(), LIMA));
        assertThat(tarde.programar(o, LocalDate.of(2026, 11, 18), "c").fechaRecordatorio()).isEqualTo(LocalDate.of(2026, 11, 18));
    }
}
