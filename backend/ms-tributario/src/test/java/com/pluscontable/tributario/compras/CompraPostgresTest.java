package com.pluscontable.tributario.compras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.compras.ReglaClasificacionCompra.Estado;
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

/** HU-25 con PostgreSQL desechable: cargar, proponer, validar, aislamiento por empresa y la alerta cerca del fin de mes. */
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
        assertThat(r.estado()).isEqualTo(Estado.SIN_CLASIFICAR);
        assertThat(r.tipo()).isNull();
        assertThat(r.descripcion()).isNull();
        assertThat(r.validadaPor()).isNull();
        assertThat(r.importe()).isEqualByComparingTo("1180.00");
        assertThat(r.proveedorRuc()).isEqualTo(RUC_PROVEEDOR);
        assertThat(r.alertaCierre()).isFalse();   // faltan 20 días para el último día del mes
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
    void elClienteProponeConDescripcionYQuedaPendienteDeValidacion() {
        long c = cliente();
        var s = servicio(LocalDate.of(2026, 11, 10));
        var compra = s.registrar(c, compra("F001-7", "3500.00"), "contador1");

        var r = s.proponer(c, compra.id(), new ClasificacionEntradaDto(Tipo.ACTIVO_FIJO, "  Laptop para el área contable  "), "cliente@example.test");

        assertThat(r.estado()).isEqualTo(Estado.PROPUESTA);
        assertThat(r.tipo()).isEqualTo(Tipo.ACTIVO_FIJO);
        assertThat(r.descripcion()).isEqualTo("Laptop para el área contable");
        assertThat(r.clasificadaEn()).isNotNull();
        assertThat(r.validadaEn()).isNull();
        assertThat(jdbc.queryForObject("SELECT clasificada_por FROM tributario_schema.compras_cliente WHERE id = ?", String.class, compra.id()))
                .isEqualTo("cliente@example.test");
        assertThat(s.listar(c, Estado.PROPUESTA)).extracting(CompraDto::id).containsExactly(compra.id());
        assertThat(s.listar(c, Estado.SIN_CLASIFICAR)).isEmpty();
        assertThat(s.listar(c, Estado.VALIDADA)).isEmpty();
    }

    @Test
    void elClientePuedeCambiarSuPropuestaMientrasElContadorNoLaValide() {
        long c = cliente();
        var s = servicio(LocalDate.of(2026, 11, 10));
        long id = s.registrar(c, compra("F001-8", "100.00"), "c").id();
        s.proponer(c, id, new ClasificacionEntradaDto(Tipo.MERCADERIA_O_GASTO, "Útiles"), "u");

        var corregida = s.proponer(c, id, new ClasificacionEntradaDto(Tipo.ACTIVO_FIJO, "Impresora"), "u");

        assertThat(corregida.estado()).isEqualTo(Estado.PROPUESTA);
        assertThat(corregida.tipo()).isEqualTo(Tipo.ACTIVO_FIJO);
        assertThat(corregida.descripcion()).isEqualTo("Impresora");
    }

    @Test
    void elContadorValidaYQuedaRegistradoQuienYCuando_yUnaValidadaYaNoSeCambia() {
        long c = cliente();
        var s = servicio(LocalDate.of(2026, 11, 10));
        long id = s.registrar(c, compra("F001-9", "100.00"), "c").id();
        s.proponer(c, id, new ClasificacionEntradaDto(Tipo.MERCADERIA_O_GASTO, "Útiles de oficina"), "u");

        var r = s.validar(c, id, "contador1");

        assertThat(r.estado()).isEqualTo(Estado.VALIDADA);
        assertThat(r.validadaPor()).isEqualTo("contador1");
        assertThat(r.validadaEn()).isNotNull();
        assertThat(r.tipo()).isEqualTo(Tipo.MERCADERIA_O_GASTO);   // se valida la propuesta tal como está
        assertThat(s.listar(c, Estado.VALIDADA)).extracting(CompraDto::id).containsExactly(id);
        assertThatThrownBy(() -> s.proponer(c, id, new ClasificacionEntradaDto(Tipo.ACTIVO_FIJO, "Otra"), "u")).hasMessageContaining("ya fue validada");
        assertThatThrownBy(() -> s.validar(c, id, "contador1")).hasMessageContaining("ya está validada");
        assertThat(s.listar(c, null).get(0).tipo()).isEqualTo(Tipo.MERCADERIA_O_GASTO);
    }

    @Test
    void noSeValidaSinPropuestaDelCliente() {
        long c = cliente();
        var s = servicio(LocalDate.of(2026, 11, 10));
        long id = s.registrar(c, compra("F001-10", "100.00"), "c").id();
        assertThatThrownBy(() -> s.validar(c, id, "contador1")).hasMessageContaining("todavía no propuso");
        assertThat(s.listar(c, null).get(0).estado()).isEqualTo(Estado.SIN_CLASIFICAR);
    }

    @Test
    void laDescripcionYElTipoSonObligatoriosAlProponer() {
        long c = cliente();
        var s = servicio(LocalDate.of(2026, 11, 10));
        long id = s.registrar(c, compra("F001-11", "100.00"), "c").id();

        assertThatThrownBy(() -> s.proponer(c, id, new ClasificacionEntradaDto(Tipo.MERCADERIA_O_GASTO, "  "), "u")).hasMessageContaining("descripción");
        assertThatThrownBy(() -> s.proponer(c, id, new ClasificacionEntradaDto(null, "algo"), "u")).hasMessageContaining("tipo es obligatorio");
        assertThatThrownBy(() -> s.proponer(c, id, new ClasificacionEntradaDto(null, null), "u"))
                .hasMessageContaining("tipo es obligatorio").hasMessageContaining("descripción");
        assertThatThrownBy(() -> s.proponer(c, id, null, "u")).isInstanceOf(ValidationException.class);
        assertThat(s.listar(c, null).get(0).estado()).isEqualTo(Estado.SIN_CLASIFICAR);
    }

    @Test
    void unaEmpresaNuncaVeNiProponeNiValidaLasComprasDeOtra_T213() {
        long suyo = cliente();
        long ajeno = cliente();
        var s = servicio(LocalDate.of(2026, 11, 10));
        long deAjeno = s.registrar(ajeno, compra("F001-12", "50.00"), "c").id();
        s.registrar(suyo, compra("F001-13", "60.00"), "c");

        assertThat(s.listar(suyo, null)).extracting(CompraDto::clienteId).containsOnly(suyo).hasSize(1);
        assertThatThrownBy(() -> s.proponer(suyo, deAjeno, new ClasificacionEntradaDto(Tipo.ACTIVO_FIJO, "x"), "u")).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> s.validar(suyo, deAjeno, "c")).isInstanceOf(EntityNotFoundException.class);
        assertThat(s.listar(ajeno, Estado.SIN_CLASIFICAR)).hasSize(1);   // la de la otra empresa no se tocó
    }

    @Test
    void laAlertaEsDelUltimoDiaDelMesCalendarioAvisaSinClasificarYPropuestasPeroNoValidadasYLosDiasSonConfigurables() {
        long c = cliente();
        var inicio = servicio(NOVIEMBRE);
        long sin = inicio.registrar(c, compra("F001-20", "10.00"), "c").id();
        long propuesta = inicio.registrar(c, compra("F001-21", "10.00"), "c").id();
        long validada = inicio.registrar(c, compra("F001-22", "10.00"), "c").id();
        inicio.proponer(c, propuesta, new ClasificacionEntradaDto(Tipo.MERCADERIA_O_GASTO, "Insumos"), "u");
        inicio.proponer(c, validada, new ClasificacionEntradaDto(Tipo.MERCADERIA_O_GASTO, "Insumos"), "u");
        inicio.validar(c, validada, "contador1");

        assertThat(alerta(servicio(LocalDate.of(2026, 11, 24)), c, sin)).isFalse();
        assertThat(alerta(servicio(LocalDate.of(2026, 11, 25)), c, sin)).isTrue();           // 5 días antes del 30/11
        assertThat(alerta(servicio(LocalDate.of(2026, 11, 25)), c, propuesta)).isTrue();     // pendiente de validación: también
        assertThat(alerta(servicio(LocalDate.of(2026, 11, 25)), c, validada)).isFalse();     // la validada no avisa
        assertThat(alerta(servicio(LocalDate.of(2026, 11, 20), 10), c, sin)).isTrue();       // con 10 días de anticipación
        assertThat(alerta(servicio(LocalDate.of(2026, 12, 4)), c, sin)).isFalse();           // el mes siguiente la cuenta empieza de nuevo
        assertThat(alerta(servicio(LocalDate.of(2026, 12, 26)), c, sin)).isTrue();           // 5 días antes del 31/12
    }

    private boolean alerta(CompraService s, long cliente, long compra) {
        return s.listar(cliente, null).stream().filter(x -> x.id() == compra).findFirst().orElseThrow().alertaCierre();
    }

    @Test
    void listaLaMasRecientePrimero() {
        long c = cliente();
        var s = servicio(LocalDate.of(2026, 11, 10));
        var octubre = s.registrar(c, new CompraEntradaDto(LocalDate.of(2026, 10, 1), RUC_PROVEEDOR, null, "F001-30", LocalDate.of(2026, 10, 3), new BigDecimal("5.00")), "c");
        var noviembre = s.registrar(c, compra("F001-31", "6.00"), "c");
        assertThat(s.listar(c, null)).extracting(CompraDto::id).containsExactly(noviembre.id(), octubre.id());
        assertThat(s.listar(c, Estado.SIN_CLASIFICAR)).hasSize(2);
        assertThat(s.listar(c, Estado.VALIDADA)).isEmpty();
    }
}
