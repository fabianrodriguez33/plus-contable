package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.dto.CobroHonorarioDto;
import com.pluscontable.tributario.dto.EstadoCuentaDto;
import com.pluscontable.tributario.dto.PagoHonorarioDto;
import com.pluscontable.tributario.model.MedioPago;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-30 · Tarea 149: el estado de cuenta de punta a punta contra PostgreSQL de pruebas, con cobros y pagos guardados en la base
 * (los permisos por rol se prueban en EstadoCuentaControllerTest). Usa clientes ficticios que se borran al terminar.
 */
@SpringBootTest
class EstadoCuentaPostgresTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired EstadoCuentaService service;

    private final List<String> rucs = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (String ruc : rucs) jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc); // lo demás en cascada
    }

    private long cliente() {
        String ruc = RucFicticio.nuevo();
        rucs.add(ruc);
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario, activo) VALUES (?, 'Cliente ficticio (estado de cuenta)', 'MYPE', true)", ruc);
        long id = jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
        jdbc.update("INSERT INTO tributario_schema.programaciones_cobro (cliente_id, periodicidad) VALUES (?, 'MENSUAL')", id);
        return id;
    }

    private long cobro(long cliente, String fecha, String monto) {
        long prog = jdbc.queryForObject("SELECT id FROM tributario_schema.programaciones_cobro WHERE cliente_id = ?", Long.class, cliente);
        jdbc.update("INSERT INTO tributario_schema.cobros_honorarios (cliente_id, programacion_id, fecha_cobro, monto) VALUES (?, ?, ?, ?)",
                cliente, prog, LocalDate.parse(fecha), new BigDecimal(monto));
        return jdbc.queryForObject("SELECT id FROM tributario_schema.cobros_honorarios WHERE cliente_id = ? AND fecha_cobro = ?",
                Long.class, cliente, LocalDate.parse(fecha));
    }

    private void pago(long cobro, String fecha, String monto, String medio, String detalle) {
        jdbc.update("INSERT INTO tributario_schema.pagos_honorarios (cobro_id, fecha_pago, monto, medio_pago, medio_pago_detalle, registrado_por) "
                + "VALUES (?, ?, ?, ?, ?, 'contador1')", cobro, LocalDate.parse(fecha), new BigDecimal(monto), medio, detalle);
    }

    @Test
    void criterios1y2_elPeriodoElegidoMuestraSusCobrosYPagosYElSaldoArrastraLoAnterior() {
        long id = cliente();
        long octubre = cobro(id, "2026-10-15", "500.00");
        long noviembre = cobro(id, "2026-11-15", "500.00");
        cobro(id, "2026-12-15", "500.00"); // fuera del periodo
        pago(octubre, "2026-10-20", "200.00", "EFECTIVO", null);       // antes del periodo
        pago(octubre, "2026-11-05", "300.00", "TRANSFERENCIA", null);  // salda octubre dentro del periodo
        pago(noviembre, "2026-11-30", "100.00", "OTRO", "Tarjeta");      // último día del periodo: entra
        pago(noviembre, "2026-12-01", "50.00", "EFECTIVO", null);       // después del periodo: no entra

        EstadoCuentaDto r = service.emitir(id, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertThat(r.saldoAnterior()).isEqualByComparingTo("300.00");   // 500 cobrado - 200 pagado en octubre
        assertThat(r.cobros()).extracting(CobroHonorarioDto::id).containsExactly(noviembre);
        assertThat(r.pagos()).extracting(PagoHonorarioDto::fechaPago)
                .containsExactly(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 30));
        assertThat(r.pagos().get(0).fechaCobro()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(r.pagos().get(1).medioPago()).isEqualTo(MedioPago.OTRO);
        assertThat(r.pagos().get(1).medioPagoDetalle()).isEqualTo("Tarjeta");
        assertThat(r.totalCobrado()).isEqualByComparingTo("500.00");
        assertThat(r.totalPagado()).isEqualByComparingTo("400.00");
        assertThat(r.saldo()).isEqualByComparingTo("400.00");          // 300 + 500 - 400
        assertThat(r.sinMovimientos()).isFalse();
    }

    @Test
    void unCobroYUnPagoDelPrimerDiaEntranEnElPeriodoYNoEnElSaldoAnterior() {
        long id = cliente();
        long primerDia = cobro(id, "2026-11-01", "500.00");
        pago(primerDia, "2026-11-01", "200.00", "EFECTIVO", null);

        EstadoCuentaDto r = service.emitir(id, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertThat(r.saldoAnterior()).isEqualByComparingTo("0");
        assertThat(r.cobros()).extracting(CobroHonorarioDto::id).containsExactly(primerDia);
        assertThat(r.pagos()).hasSize(1);
        assertThat(r.saldo()).isEqualByComparingTo("300.00"); // contado una sola vez
    }

    @Test
    void unPagoAdelantadoDeUnCobroPosteriorEntraEnLosPagosYPuedeDejarSaldoAFavor() {
        long id = cliente();
        long diciembre = cobro(id, "2026-12-15", "500.00");          // cobro de un periodo posterior
        pago(diciembre, "2026-11-20", "500.00", "EFECTIVO", null);    // pagado por adelantado dentro del periodo

        EstadoCuentaDto r = service.emitir(id, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertThat(r.cobros()).isEmpty();                              // el cobro es de diciembre
        assertThat(r.pagos()).singleElement().satisfies(p -> assertThat(p.fechaCobro()).isEqualTo(LocalDate.of(2026, 12, 15)));
        assertThat(r.totalPagado()).isEqualByComparingTo("500.00");
        assertThat(r.saldo()).isEqualByComparingTo("-500.00");         // a favor del cliente
        assertThat(r.sinMovimientos()).isFalse();

        // en diciembre ese pago ya está en el saldo anterior y el saldo vuelve a cero
        EstadoCuentaDto dic = service.emitir(id, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
        assertThat(dic.saldoAnterior()).isEqualByComparingTo("-500.00");
        assertThat(dic.saldo()).isEqualByComparingTo("0");
    }

    @Test
    void criterio3_unPeriodoSinMovimientosLoIndicaYUnClienteNoVeLosDeOtro() {
        long id = cliente();
        long otro = cliente();
        long cobroDelOtro = cobro(otro, "2026-11-15", "900.00");
        pago(cobroDelOtro, "2026-11-16", "900.00", "EFECTIVO", null);
        cobro(id, "2026-09-15", "100.00");

        EstadoCuentaDto r = service.emitir(id, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertThat(r.sinMovimientos()).isTrue();
        assertThat(r.cobros()).isEmpty();
        assertThat(r.pagos()).isEmpty();
        assertThat(r.saldoAnterior()).isEqualByComparingTo("100.00");
        assertThat(r.saldo()).isEqualByComparingTo("100.00");
    }
}
