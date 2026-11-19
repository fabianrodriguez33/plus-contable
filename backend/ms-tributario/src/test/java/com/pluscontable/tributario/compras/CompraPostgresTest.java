package com.pluscontable.tributario.compras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.compras.ReglaClasificacionCompra.Tipo;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
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

/** HU-25 con PostgreSQL desechable: cargar, listar, clasificar, aislamiento por empresa y la alerta cerca del cierre. */
@SpringBootTest
class CompraPostgresTest {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate NOVIEMBRE = LocalDate.of(2026, 11, 1);
    private static final String RUC_PROVEEDOR = "20100070970";

    @Autowired JdbcTemplate jdbc;
    private final List<Long> clientes = new ArrayList<>();

    private CompraService servicio(LocalDate hoy, int dias) {
        return new CompraService(jdbc, Clock.fixed(hoy.atTime(12, 0).atZone(LIMA).toInstant(), LIMA), dias);
    }

    private CompraService servicio(LocalDate hoy) {
        return servicio(hoy, 5);
    }

    @AfterEach
    void limpiar() {
        for (Long id : clientes) jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", id);
    }

    private long cliente() {
        String ruc = "20" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
        Long id = jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes(ruc, razon_social, regimen_tributario)
                VALUES (?, 'Cliente HU25 SAC', 'MYPE') RETURNING id""", Long.class, ruc);
        clientes.add(id);
        return id;
    }

    private static CompraEntradaDto compra(String comprobante, String importe) {
        return new CompraEntradaDto(NOVIEMBRE, RUC_PROVEEDOR, "Proveedor Prueba SAC", comprobante, LocalDate.of(2026, 11, 5), new BigDecimal(importe));
    }

    @Test
    void elContadorCargaUnaCompraQueQuedaSinClasificarYPendiente() {
        long c = cliente();
        var r = servicio(LocalDate.of(2026, 11, 10)).registrar(c, compra("F001-100", "1180.00"), "contador1");

        assertThat(r.clienteId()).isEqualTo(c);
        assertThat(r.clasificada()).isFalse();
        assertThat(r.tipo()).isNull();
        assertThat(r.descripcion()).isNull();
        assertThat(r.importe()).isEqualByComparingTo("1180.00");
        assertThat(r.proveedorRuc()).isEqualTo(RUC_PROVEEDOR);
        assertThat(r.alertaCierre()).isFalse();   // faltan 20 días para el cierre
        assertThat(jdbc.queryForObject("SELECT registrada_por FROM tributario_schema.compras_cliente WHERE id = ?", String.class, r.id())).isEqualTo("contador1");
    }

    @Test
    void validaLosDatosReuniendoLosErroresYRechazaElComprobanteRepetido() {
        long c = cliente();
        var s = servicio(LocalDate.of(2026, 11, 10));
        var malo = new CompraEntradaDto(LocalDate.of(2026, 11, 7), "123", null, " ", null, BigDecimal.ZERO);
        assertThatThrownBy(() -> s.registrar(c, malo, "c")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("primer día del mes").hasMessageContaining("proveedorRuc").hasMessageContaining("comprobante es obligatorio")
                .hasMessageContaining("fechaComprobante").hasMessageContaining("importe");
        assertThatThrownBy(() -> s.registrar(c, null, "c")).isInstanceOf(ValidationException.class);

        s.registrar(c, compra("F001-1", "10.00"), "c");
        assertThatThrownBy(() -> s.registrar(c, compra("F001-1", "20.00"), "c")).hasMessageContaining("ya está cargado");
        long otro = cliente();
        s.registrar(otro, compra("F001-1", "20.00"), "c");   // el mismo comprobante en otra empresa sí se puede
        assertThatThrownBy(() -> s.registrar(-1L, compra("F001-2", "1.00"), "c")).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void elClienteClasificaConDescripcionYQuedaRegistradoQuienYCuando() {
        long c = cliente();
        var s = servicio(LocalDate.of(2026, 11, 10));
        var compra = s.registrar(c, compra("F001-7", "3500.00"), "contador1");

        var r = s.clasificar(c, compra.id(), new ClasificacionEntradaDto(Tipo.ACTIVO_FIJO, "  Laptop para el área contable  "), "cliente@example.test");

        assertThat(r.clasificada()).isTrue();
        assertThat(r.tipo()).isEqualTo(Tipo.ACTIVO_FIJO);
        assertThat(r.descripcion()).isEqualTo("Laptop para el área contable");
        assertThat(r.clasificadaEn()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT clasificada_por FROM tributario_schema.compras_cliente WHERE id = ?", String.class, compra.id()))
                .isEqualTo("cliente@example.test");
        assertThat(s.listar(c, true)).extracting(CompraDto::id).containsExactly(compra.id());
        assertThat(s.listar(c, false)).isEmpty();
    }

    @Test
    void laDescripcionYElTipoSonObligatoriosYUnaClasificadaNoSeCambia() {
        long c = cliente();
        var s = servicio(LocalDate.of(2026, 11, 10));
        long id = s.registrar(c, compra("F001-8", "100.00"), "c").id();

        assertThatThrownBy(() -> s.clasificar(c, id, new ClasificacionEntradaDto(Tipo.MERCADERIA_O_GASTO, "  "), "u")).hasMessageContaining("descripción");
        assertThatThrownBy(() -> s.clasificar(c, id, new ClasificacionEntradaDto(null, "algo"), "u")).hasMessageContaining("tipo es obligatorio");
        assertThatThrownBy(() -> s.clasificar(c, id, new ClasificacionEntradaDto(null, null), "u"))
                .hasMessageContaining("tipo es obligatorio").hasMessageContaining("descripción");
        assertThatThrownBy(() -> s.clasificar(c, id, null, "u")).isInstanceOf(ValidationException.class);
        assertThat(s.listar(c, null).get(0).clasificada()).isFalse();

        s.clasificar(c, id, new ClasificacionEntradaDto(Tipo.MERCADERIA_O_GASTO, "Útiles de oficina"), "u");
        assertThatThrownBy(() -> s.clasificar(c, id, new ClasificacionEntradaDto(Tipo.ACTIVO_FIJO, "Otra"), "u")).hasMessageContaining("ya está clasificada");
        assertThat(s.listar(c, null).get(0).tipo()).isEqualTo(Tipo.MERCADERIA_O_GASTO);
    }

    @Test
    void unaEmpresaNuncaVeNiClasificaLasComprasDeOtra_T213() {
        long suyo = cliente();
        long ajeno = cliente();
        var s = servicio(LocalDate.of(2026, 11, 10));
        long deAjeno = s.registrar(ajeno, compra("F001-9", "50.00"), "c").id();
        s.registrar(suyo, compra("F001-10", "60.00"), "c");

        assertThat(s.listar(suyo, null)).extracting(CompraDto::clienteId).containsOnly(suyo).hasSize(1);
        assertThatThrownBy(() -> s.clasificar(suyo, deAjeno, new ClasificacionEntradaDto(Tipo.ACTIVO_FIJO, "x"), "u")).isInstanceOf(EntityNotFoundException.class);
        assertThat(s.listar(ajeno, false)).hasSize(1);   // la de la otra empresa no se tocó
    }

    @Test
    void laAlertaApareceCercaDelCierreEnLasSinClasificarYLosDiasSonConfigurables() {
        long c = cliente();
        long sin = servicio(NOVIEMBRE).registrar(c, compra("F001-20", "10.00"), "c").id();
        long con = servicio(NOVIEMBRE).registrar(c, compra("F001-21", "10.00"), "c").id();
        servicio(NOVIEMBRE).clasificar(c, con, new ClasificacionEntradaDto(Tipo.MERCADERIA_O_GASTO, "Insumos"), "u");

        assertThat(alerta(servicio(LocalDate.of(2026, 11, 24)), c, sin)).isFalse();
        assertThat(alerta(servicio(LocalDate.of(2026, 11, 25)), c, sin)).isTrue();           // 5 días antes del cierre (30/11)
        assertThat(alerta(servicio(LocalDate.of(2026, 11, 25)), c, con)).isFalse();          // la clasificada no avisa
        assertThat(alerta(servicio(LocalDate.of(2026, 11, 20), 10), c, sin)).isTrue();       // con 10 días de anticipación
        assertThat(alerta(servicio(LocalDate.of(2026, 12, 4)), c, sin)).isTrue();            // pasado el cierre sigue pendiente
    }

    private boolean alerta(CompraService s, long cliente, long compra) {
        return s.listar(cliente, null).stream().filter(x -> x.id() == compra).findFirst().orElseThrow().alertaCierre();
    }

    @Test
    void listaLaMasRecientePrimeroYFiltraPorClasificada() {
        long c = cliente();
        var s = servicio(LocalDate.of(2026, 11, 10));
        var octubre = s.registrar(c, new CompraEntradaDto(LocalDate.of(2026, 10, 1), RUC_PROVEEDOR, null, "F001-30", LocalDate.of(2026, 10, 3), new BigDecimal("5.00")), "c");
        var noviembre = s.registrar(c, compra("F001-31", "6.00"), "c");
        assertThat(s.listar(c, null)).extracting(CompraDto::id).containsExactly(noviembre.id(), octubre.id());
        assertThat(s.listar(c, false)).hasSize(2);
        assertThat(s.listar(c, true)).isEmpty();
    }
}
