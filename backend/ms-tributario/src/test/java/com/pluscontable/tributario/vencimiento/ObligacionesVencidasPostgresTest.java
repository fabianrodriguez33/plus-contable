package com.pluscontable.tributario.vencimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
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

/** HU-16 · T46 con PostgreSQL desechable: qué sale como vencida (con pagado y saldo) y qué como sin declarar. */
@SpringBootTest
class ObligacionesVencidasPostgresTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 18);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired JdbcTemplate jdbc;
    private final List<Long> clientes = new ArrayList<>();
    private int mes = 0;

    private ObligacionesVencidasService servicio(LocalDate hoy) {
        return new ObligacionesVencidasService(jdbc, Clock.fixed(hoy.atStartOfDay(LIMA).plusHours(12).toInstant(), LIMA));
    }

    @AfterEach
    void limpiar() {
        for (Long id : clientes) jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", id);
    }

    private long cliente(boolean activo) {
        String ruc = "20" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
        Long id = jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes(ruc, razon_social, regimen_tributario, activo)
                VALUES (?, 'Cliente HU16 SAC', 'MYPE', ?) RETURNING id""", Long.class, ruc, activo);
        clientes.add(id);
        return id;
    }

    private long obligacion(long cliente, String estado, LocalDate vence, String monto) {
        LocalDate periodo = LocalDate.of(2020, 1, 1).plusMonths(mes++);
        return jdbc.queryForObject("""
                INSERT INTO tributario_schema.obligaciones(cliente_id, tipo_obligacion, periodo, fecha_vencimiento, estado, monto_determinado)
                VALUES (?, 'IGV', ?, ?, ?, ?::numeric) RETURNING id""", Long.class, cliente, periodo, vence, estado, monto);
    }

    private void pago(long obligacion, String monto, String constancia) {
        jdbc.update("""
                INSERT INTO tributario_schema.pagos_obligacion(obligacion_id, fecha_pago, monto_pagado, numero_constancia, acumulado_despues, saldo_despues)
                VALUES (?, '2026-11-01', ?::numeric, ?, ?::numeric, 0)""", obligacion, monto, constancia, monto);
    }

    private List<Long> ids(List<ObligacionVencidaDto> lista) {
        return lista.stream().map(ObligacionVencidaDto::obligacionId).toList();
    }

    @Test
    void vencidasConSaldoYSinDeclararVanEnListasSeparadas() {
        long c = cliente(true);
        long declarada = obligacion(c, "DECLARADO", HOY.minusDays(3), "1000.00");
        long parcial = obligacion(c, "PAGO_PARCIAL", HOY.minusDays(10), "1000.00");
        pago(parcial, "400.00", "C-1");
        long sinDeclarar = obligacion(c, "PENDIENTE", HOY.minusDays(7), null);
        long pagada = obligacion(c, "PAGADO", HOY.minusDays(5), "1000.00");
        pago(pagada, "1000.00", "C-2");
        obligacion(c, "DECLARADO", HOY, "500.00");                 // vence hoy: aún no
        obligacion(c, "DECLARADO", HOY.plusDays(4), "500.00");     // futura
        obligacion(c, "PENDIENTE", HOY.plusDays(4), null);         // sin declarar pero con tiempo

        var r = servicio(HOY).listar(c);

        assertThat(ids(r.vencidas())).containsExactly(parcial, declarada);              // más atrasada primero
        assertThat(ids(r.sinDeclarar())).containsExactly(sinDeclarar);
        var conParcial = r.vencidas().get(0);
        assertThat(conParcial.diasVencida()).isEqualTo(10);
        assertThat(conParcial.montoPagado()).isEqualByComparingTo("400.00");
        assertThat(conParcial.saldo()).isEqualByComparingTo("600.00");
        var sinPago = r.vencidas().get(1);
        assertThat(sinPago.montoPagado()).isEqualByComparingTo("0");
        assertThat(sinPago.saldo()).isEqualByComparingTo("1000.00");
        assertThat(r.sinDeclarar().get(0).saldo()).isNull();                            // sin monto determinado
        assertThat(r.vencidas().get(0).razonSocial()).isEqualTo("Cliente HU16 SAC");
    }

    @Test
    void cambiaSoloAlPasarLaFechaLimiteYSiSePagaTodoDejaDeSerVencida() {
        long c = cliente(true);
        long o = obligacion(c, "DECLARADO", LocalDate.of(2026, 11, 20), "100.00");
        assertThat(servicio(LocalDate.of(2026, 11, 20)).listar(c).vencidas()).isEmpty();
        assertThat(ids(servicio(LocalDate.of(2026, 11, 21)).listar(c).vencidas())).containsExactly(o);
        pago(o, "100.00", "C-9");
        jdbc.update("UPDATE tributario_schema.obligaciones SET estado = 'PAGADO' WHERE id = ?", o);
        assertThat(servicio(LocalDate.of(2026, 11, 21)).listar(c).vencidas()).isEmpty();
    }

    @Test
    void conPagoParcialSigueVencidaYMuestraElSaldoActualizado() {
        long c = cliente(true);
        long o = obligacion(c, "PAGO_PARCIAL", HOY.minusDays(2), "1000.00");
        pago(o, "300.00", "C-1");
        pago(o, "200.00", "C-2");
        var v = servicio(HOY).listar(c).vencidas().get(0);
        assertThat(v.montoPagado()).isEqualByComparingTo("500.00");
        assertThat(v.saldo()).isEqualByComparingTo("500.00");
    }

    @Test
    void clienteInactivoNoApareceYElFiltroPorClienteFunciona() {
        long activo = cliente(true);
        long inactivo = cliente(false);
        long delActivo = obligacion(activo, "DECLARADO", HOY.minusDays(1), "10.00");
        obligacion(inactivo, "DECLARADO", HOY.minusDays(1), "10.00");

        assertThat(servicio(HOY).listar(inactivo).vencidas()).isEmpty();
        assertThat(ids(servicio(HOY).listar(activo).vencidas())).containsExactly(delActivo);
        assertThat(ids(servicio(HOY).listar(null).vencidas())).contains(delActivo);
    }

    @Test
    void hoyEsElDeLimaAunqueElServidorEsteEnUtc() {
        long c = cliente(true);
        long o = obligacion(c, "DECLARADO", LocalDate.of(2026, 11, 17), "10.00");
        // 18/11 03:00 UTC = 17/11 22:00 en Lima: el 17 todavía no está vencida
        var enLima = new ObligacionesVencidasService(jdbc, Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), LIMA));
        assertThat(enLima.listar(c).vencidas()).isEmpty();
        assertThat(ids(servicio(LocalDate.of(2026, 11, 18)).listar(c).vencidas())).containsExactly(o);
    }
}
