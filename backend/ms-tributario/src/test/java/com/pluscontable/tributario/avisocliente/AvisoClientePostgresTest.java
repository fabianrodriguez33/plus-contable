package com.pluscontable.tributario.avisocliente;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** HU-33 con PostgreSQL desechable: cada criterio de aceptación de la tarjeta, con la tabla real. */
@SpringBootTest
class AvisoClientePostgresTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 17);

    @Autowired AvisoClienteService service;
    @Autowired JdbcTemplate jdbc;
    private final List<Long> clientes = new ArrayList<>();
    private int mes; // el periodo es único por cliente y tipo

    @AfterEach
    void limpiar() {
        for (Long id : clientes) jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", id);
    }

    private long cliente() {
        String ruc = "20" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
        Long id = jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes(ruc, razon_social, regimen_tributario, activo)
                VALUES (?, 'Cliente de prueba', 'MYPE', true) RETURNING id
                """, Long.class, ruc);
        clientes.add(id);
        return id;
    }

    private long obligacion(long clienteId, String estado, LocalDate vence, String monto) {
        return jdbc.queryForObject("""
                INSERT INTO tributario_schema.obligaciones(cliente_id, tipo_obligacion, periodo, fecha_vencimiento, estado,
                    fecha_declaracion, numero_formulario, monto_determinado)
                VALUES (?, 'IGV', ?, ?, ?, '2026-11-10', '621', ?) RETURNING id
                """, Long.class, clienteId, LocalDate.of(2024, 1, 1).plusMonths(mes++), vence, estado, monto == null ? null : new BigDecimal(monto));
    }

    private void pago(long obligacionId, String monto, String saldo) {
        jdbc.update("""
                INSERT INTO tributario_schema.pagos_obligacion(obligacion_id, fecha_pago, monto_pagado, numero_constancia,
                    acumulado_despues, saldo_despues) VALUES (?, '2026-11-15', ?, 'C-1', ?, ?)
                """, obligacionId, new BigDecimal(monto), new BigDecimal(monto), new BigDecimal(saldo));
    }

    @Test
    void avisaConTributoMontoYFechaAntesDelVencimiento() {
        long c = cliente();
        long o = obligacion(c, "DECLARADO", HOY.plusDays(3), "1500.50");
        service.generar(HOY);
        assertThat(service.listar(c)).singleElement().satisfies(a -> {
            assertThat(a.obligacionId()).isEqualTo(o);
            assertThat(a.tributo()).isEqualTo("IGV");
            assertThat(a.monto()).isEqualByComparingTo("1500.50");
            assertThat(a.fechaVencimiento()).isEqualTo(HOY.plusDays(3));
        });
    }

    @Test
    void sinPagoPorHacerNoAvisaYFueraDeLaVentanaTampoco() {
        long c = cliente();
        obligacion(c, "PAGADO", HOY.plusDays(3), "900");
        obligacion(c, "DECLARADO", HOY.plusDays(5), "900");   // faltan 5 días: aún no toca
        obligacion(c, "PENDIENTE", HOY.plusDays(3), null);    // sin declarar no hay monto que avisar
        obligacion(c, "DECLARADO", HOY.minusDays(1), "900");  // ya venció: no es un aviso previo
        service.generar(HOY);
        assertThat(service.listar(c)).isEmpty();
    }

    @Test
    void conPagoParcialAvisaSoloElSaldo() {
        long c = cliente();
        long o = obligacion(c, "PAGO_PARCIAL", HOY.plusDays(1), "1000");
        pago(o, "400", "600");
        service.generar(HOY);
        assertThat(service.listar(c)).singleElement().satisfies(a -> assertThat(a.monto()).isEqualByComparingTo("600"));
    }

    @Test
    void repetirElMismoDiaNoDuplicaYOtroDiaDeLaVentanaSiAvisa() {
        long c = cliente();
        obligacion(c, "DECLARADO", HOY.plusDays(3), "100");
        service.generar(HOY);
        service.generar(HOY);
        assertThat(service.listar(c)).hasSize(1);
        service.generar(HOY.plusDays(2)); // faltan 1 día
        assertThat(service.listar(c)).hasSize(2);
    }

    @Test
    void cadaClienteVeSoloSusAvisos() {
        long a = cliente();
        long b = cliente();
        obligacion(a, "DECLARADO", HOY.plusDays(3), "100");
        service.generar(HOY);
        assertThat(service.listar(a)).hasSize(1);
        assertThat(service.listar(b)).isEmpty();
    }
}
