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

/** HU-16 · T46 con PostgreSQL desechable: qué obligaciones aparecen como VENCIDA SIN PAGO. */
@SpringBootTest
class ObligacionesVencidasSinPagoPostgresTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 18);
    /** 18/11/2026 03:00 UTC = 17/11 22:00 en Lima: «hoy» en Lima es el 17. */
    private static final Clock RELOJ_LIMA_NOCHE = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("UTC"));

    @Autowired JdbcTemplate jdbc;
    private final List<Long> clientes = new ArrayList<>();
    private int mes = 0;

    private ObligacionesVencidasSinPagoService servicio(LocalDate hoy) {
        return new ObligacionesVencidasSinPagoService(jdbc,
                Clock.fixed(hoy.atStartOfDay(ZoneId.of("America/Lima")).plusHours(12).toInstant(), ZoneId.of("America/Lima")));
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

    private long obligacion(long cliente, String estado, LocalDate vence) {
        LocalDate periodo = LocalDate.of(2020, 1, 1).plusMonths(mes++);
        return jdbc.queryForObject("""
                INSERT INTO tributario_schema.obligaciones(cliente_id, tipo_obligacion, periodo, fecha_vencimiento, estado, monto_determinado)
                VALUES (?, 'IGV', ?, ?, ?, 100.00) RETURNING id""", Long.class, cliente, periodo, vence, estado);
    }

    private void pago(long obligacion, String monto, String constancia, String saldo) {
        jdbc.update("""
                INSERT INTO tributario_schema.pagos_obligacion(obligacion_id, fecha_pago, monto_pagado, numero_constancia,
                                                              acumulado_despues, saldo_despues)
                VALUES (?, '2026-11-01', ?::numeric, ?, ?::numeric, ?::numeric)""", obligacion, monto, constancia, monto, saldo);
    }

    private List<Long> ids(long cliente, LocalDate hoy) {
        return servicio(hoy).listar(cliente).stream().map(ObligacionVencidaSinPagoDto::obligacionId).toList();
    }

    @Test
    void marcaSoloLaVencidaSinConstanciaYNoLasDemas() {
        long c = cliente(true);
        long vencidaDeclarada = obligacion(c, "DECLARADO", HOY.minusDays(3));
        long vencidaPendiente = obligacion(c, "PENDIENTE", HOY.minusDays(10));
        long conConstancia = obligacion(c, "DECLARADO", HOY.minusDays(2));
        pago(conConstancia, "40.00", "C-1", "60.00");
        long pagada = obligacion(c, "PAGADO", HOY.minusDays(5));
        pago(pagada, "100.00", "C-2", "0.00");
        obligacion(c, "DECLARADO", HOY);                  // vence hoy: aún no
        obligacion(c, "DECLARADO", HOY.plusDays(4));      // futura

        var lista = servicio(HOY).listar(c);

        assertThat(lista).extracting(ObligacionVencidaSinPagoDto::obligacionId)
                .containsExactly(vencidaPendiente, vencidaDeclarada);   // más atrasada primero
        assertThat(lista.get(0).diasVencida()).isEqualTo(10);
        assertThat(lista.get(1).diasVencida()).isEqualTo(3);
        assertThat(lista.get(0).razonSocial()).isEqualTo("Cliente HU16 SAC");
    }

    @Test
    void cambiaSoloAlPasarLaFechaLimiteYDejaDeMarcarseAlRegistrarLaConstancia() {
        long c = cliente(true);
        long o = obligacion(c, "DECLARADO", LocalDate.of(2026, 11, 20));
        assertThat(ids(c, LocalDate.of(2026, 11, 20))).isEmpty();
        assertThat(ids(c, LocalDate.of(2026, 11, 21))).containsExactly(o);
        pago(o, "100.00", "C-9", "0.00");
        assertThat(ids(c, LocalDate.of(2026, 11, 21))).isEmpty();
    }

    @Test
    void clienteInactivoNoApareceYElFiltroPorClienteFunciona() {
        long activo = cliente(true);
        long inactivo = cliente(false);
        long delActivo = obligacion(activo, "DECLARADO", HOY.minusDays(1));
        obligacion(inactivo, "DECLARADO", HOY.minusDays(1));

        assertThat(ids(inactivo, HOY)).isEmpty();
        assertThat(ids(activo, HOY)).containsExactly(delActivo);
        assertThat(servicio(HOY).listar(null)).extracting(ObligacionVencidaSinPagoDto::obligacionId).contains(delActivo);
    }

    @Test
    void hoyEsElDeLimaAunqueElServidorEsteEnUtc() {
        long c = cliente(true);
        // Vence el 17/11. A las 22:00 de Lima del 17/11 (ya 18/11 en UTC) todavía no está vencida.
        long o = obligacion(c, "DECLARADO", LocalDate.of(2026, 11, 17));
        var enLima = new ObligacionesVencidasSinPagoService(jdbc,
                Clock.fixed(RELOJ_LIMA_NOCHE.instant(), ZoneId.of("America/Lima")));
        assertThat(enLima.listar(c)).isEmpty();
        assertThat(ids(c, LocalDate.of(2026, 11, 18))).containsExactly(o);
    }
}
