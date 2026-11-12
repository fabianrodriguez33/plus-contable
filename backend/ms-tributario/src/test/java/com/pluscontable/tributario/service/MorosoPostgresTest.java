package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.dto.MorosoDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-31 · Tarea 155: la lista de morosos de punta a punta contra PostgreSQL de pruebas, con cobros reales en la base (los
 * permisos por rol se prueban en MorosoControllerTest). Usa clientes ficticios que se borran al terminar; las fechas son
 * relativas a hoy porque el servicio usa la fecha del sistema.
 */
@SpringBootTest
class MorosoPostgresTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired MorosoService service;

    private final List<String> rucs = new ArrayList<>();
    private final LocalDate hoy = LocalDate.now(MorosoService.ZONA); // el mismo «hoy» que el servicio

    @AfterEach
    void limpiar() {
        for (String ruc : rucs) jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
    }

    private long cliente(boolean activo) {
        String ruc = RucFicticio.nuevo();
        rucs.add(ruc);
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario, activo) VALUES (?, ?, 'MYPE', ?)",
                ruc, "Cliente ficticio " + ruc, activo);
        long id = jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
        jdbc.update("INSERT INTO tributario_schema.programaciones_cobro (cliente_id, periodicidad) VALUES (?, 'MENSUAL')", id);
        return id;
    }

    private void cobro(long cliente, LocalDate fecha, String monto, String estado, String pagado) {
        long prog = jdbc.queryForObject("SELECT id FROM tributario_schema.programaciones_cobro WHERE cliente_id = ?", Long.class, cliente);
        jdbc.update("INSERT INTO tributario_schema.cobros_honorarios (cliente_id, programacion_id, fecha_cobro, monto, estado, pagado) "
                + "VALUES (?, ?, ?, ?, ?, ?)", cliente, prog, fecha, new BigDecimal(monto), estado, new BigDecimal(pagado));
    }

    private Optional<MorosoDto> enLaLista(long cliente) {
        return service.listar().stream().filter(m -> m.clienteId() == cliente).findFirst();
    }

    @Test
    void criterio1_conCobrosVencidosApareceConElSaldoVencidoYLosDiasDelMasAntiguo() {
        long id = cliente(true);
        cobro(id, hoy.minusDays(40), "500.00", "PENDIENTE", "0");
        cobro(id, hoy.minusDays(10), "500.00", "PAGO_PARCIAL", "200.00");
        cobro(id, hoy.minusDays(70), "500.00", "PAGADO", "500.00");   // pagado: no cuenta
        cobro(id, hoy, "500.00", "PENDIENTE", "0");                   // vence hoy: todavía no
        cobro(id, hoy.plusDays(20), "500.00", "PENDIENTE", "0");      // futuro: no

        MorosoDto m = enLaLista(id).orElseThrow();

        assertThat(m.montoVencido()).isEqualByComparingTo("800.00"); // 500 + (500 - 200)
        assertThat(m.cobrosVencidos()).isEqualTo(2);
        assertThat(m.vencidoDesde()).isEqualTo(hoy.minusDays(40));
        assertThat(m.diasAtraso()).isEqualTo(40);
        assertThat(m.razonSocial()).startsWith("Cliente ficticio");
    }

    @Test
    void criterio2_unClienteAlDiaNoApareceAunqueTengaCobrosPagadosOPorVencer() {
        long alDia = cliente(true);
        cobro(alDia, hoy.minusDays(30), "300.00", "PAGADO", "300.00");
        cobro(alDia, hoy, "300.00", "PENDIENTE", "0");
        cobro(alDia, hoy.plusDays(5), "300.00", "PAGO_PARCIAL", "100.00");
        long sinCobros = cliente(true);

        assertThat(enLaLista(alDia)).isEmpty();
        assertThat(enLaLista(sinCobros)).isEmpty();
    }

    @Test
    void unClienteInactivoConDeudaVencidaSigueEnLaListaYElMasAtrasadoVaPrimero() {
        long inactivo = cliente(false);
        cobro(inactivo, hoy.minusDays(90), "100.00", "PENDIENTE", "0");
        long reciente = cliente(true);
        cobro(reciente, hoy.minusDays(1), "999.00", "PENDIENTE", "0");

        List<Long> orden = service.listar().stream().map(MorosoDto::clienteId).filter(id -> id == inactivo || id == reciente).toList();

        assertThat(orden).containsExactly(inactivo, reciente);
        assertThat(enLaLista(reciente).orElseThrow().diasAtraso()).isEqualTo(ChronoUnit.DAYS.between(hoy.minusDays(1), hoy));
    }
}
