package com.pluscontable.tributario.compras;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.aviso.AvisoInternoDto;
import com.pluscontable.tributario.aviso.AvisoInternoService;
import com.pluscontable.tributario.compras.ReglaClasificacionCompra.Tipo;
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

/** HU-25 · T212 con PostgreSQL desechable: la alerta de fin de mes llega como aviso interno al contador. */
@SpringBootTest
class AlertaComprasServiceTest {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate NOVIEMBRE = LocalDate.of(2026, 11, 1);

    @Autowired JdbcTemplate jdbc;
    @Autowired AvisoInternoService avisos;
    private final List<Long> clientes = new ArrayList<>();

    private AlertaComprasService alerta(int dias) {
        return new AlertaComprasService(jdbc, avisos, dias);
    }

    private CompraService compras(LocalDate hoy) {
        return new CompraService(jdbc, Clock.fixed(hoy.atTime(12, 0).atZone(LIMA).toInstant(), LIMA), 5);
    }

    @AfterEach
    void limpiar() {
        for (Long id : clientes) {
            jdbc.update("DELETE FROM tributario_schema.avisos WHERE id IN (SELECT aviso_id FROM tributario_schema.avisos_clientes WHERE cliente_id = ?)", id);
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", id);
        }
    }

    private long cliente(boolean activo, String razonSocial) {
        String ruc = "20" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
        Long id = jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes(ruc, razon_social, regimen_tributario, activo)
                VALUES (?, ?, 'MYPE', ?) RETURNING id""", Long.class, ruc, razonSocial, activo);
        clientes.add(id);
        return id;
    }

    private long compra(long cliente, String comprobante) {
        return compras(NOVIEMBRE).registrar(cliente, new CompraEntradaDto(NOVIEMBRE, "20100070970", null, comprobante,
                LocalDate.of(2026, 11, 5), new BigDecimal("10.00")), "contador1").id();
    }

    private void validar(long cliente, long compra) {
        compras(NOVIEMBRE).proponer(cliente, compra, new ClasificacionEntradaDto(Tipo.ACTIVO_FIJO, "Equipo"), "cliente");
        compras(NOVIEMBRE).validar(cliente, compra, "contador1");
    }

    private List<AvisoInternoDto> avisosDe(long cliente) {
        return avisos.bandeja(null).stream().filter(a -> a.origen().equals(AlertaComprasService.ORIGEN) && a.clienteIds().contains(cliente)).toList();
    }

    @Test
    void dentroDeLaVentanaAvisaAlContadorDelClienteConComprasSinValidar() {
        long c = cliente(true, "Acme Compras SAC");
        compra(c, "F-1");

        var r = alerta(5).generar(LocalDate.of(2026, 11, 25));    // 5 días antes del 30/11

        assertThat(r.avisosCreados()).isGreaterThanOrEqualTo(1);
        var lista = avisosDe(c);
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).claveEvento()).isEqualTo("COMPRAS_SIN_VALIDAR:cliente:" + c + ":2026-11");
        assertThat(lista.get(0).mensaje()).contains("Acme Compras SAC").contains("compras sin validar").contains("último día del mes");
        assertThat(lista.get(0).leidoEn()).isNull();
    }

    @Test
    void fueraDeLaVentanaNoAvisa() {
        long c = cliente(true, "Acme Compras SAC");
        compra(c, "F-1");

        assertThat(alerta(5).generar(LocalDate.of(2026, 11, 24))).isEqualTo(new AlertaComprasService.Resultado(0, 0));
        assertThat(avisosDe(c)).isEmpty();
    }

    @Test
    void lasDeUnaPropuestaPendienteDeValidacionTambienAvisan_peroSiTodoEstaValidadoNo() {
        long conPropuesta = cliente(true, "Con propuesta SAC");
        long id = compra(conPropuesta, "F-2");
        compras(NOVIEMBRE).proponer(conPropuesta, id, new ClasificacionEntradaDto(Tipo.MERCADERIA_O_GASTO, "Insumos"), "cliente");
        long alDia = cliente(true, "Al día SAC");
        validar(alDia, compra(alDia, "F-3"));
        long sinCompras = cliente(true, "Sin compras SAC");

        alerta(5).generar(LocalDate.of(2026, 11, 28));

        assertThat(avisosDe(conPropuesta)).hasSize(1);
        assertThat(avisosDe(alDia)).isEmpty();
        assertThat(avisosDe(sinCompras)).isEmpty();
    }

    @Test
    void conUnaValidadaYOtraPendienteSiAvisa() {
        long c = cliente(true, "Mixto SAC");
        validar(c, compra(c, "F-4"));
        compra(c, "F-5");
        alerta(5).generar(LocalDate.of(2026, 11, 30));
        assertThat(avisosDe(c)).hasSize(1);
    }

    @Test
    void unClienteInactivoNoAvisa() {
        long c = cliente(false, "Inactivo SAC");
        compra(c, "F-6");
        alerta(5).generar(LocalDate.of(2026, 11, 28));
        assertThat(avisosDe(c)).isEmpty();
    }

    @Test
    void repetirElMismoMesNoDuplicaAunqueCambieLaRazonSocial_yElMesSiguienteAvisaDeNuevo() {
        long c = cliente(true, "Nombre Original SAC");
        compra(c, "F-7");
        alerta(5).generar(LocalDate.of(2026, 11, 26));
        alerta(5).generar(LocalDate.of(2026, 11, 27));
        jdbc.update("UPDATE tributario_schema.clientes SET razon_social = 'Nombre Nuevo SAC' WHERE id = ?", c);
        var tercera = alerta(5).generar(LocalDate.of(2026, 11, 30));

        assertThat(avisosDe(c)).hasSize(1);
        assertThat(tercera.avisosCreados()).isZero();

        alerta(5).generar(LocalDate.of(2026, 12, 27));    // diciembre: 5 días antes del 31
        assertThat(avisosDe(c)).hasSize(2);
        assertThat(avisosDe(c)).extracting(AvisoInternoDto::claveEvento)
                .containsExactlyInAnyOrder("COMPRAS_SIN_VALIDAR:cliente:" + c + ":2026-11", "COMPRAS_SIN_VALIDAR:cliente:" + c + ":2026-12");
    }

    @Test
    void losDiasSonConfigurables() {
        long c = cliente(true, "Config SAC");
        compra(c, "F-8");
        alerta(0).generar(LocalDate.of(2026, 11, 29));
        assertThat(avisosDe(c)).isEmpty();
        alerta(10).generar(LocalDate.of(2026, 11, 20));
        assertThat(avisosDe(c)).hasSize(1);
    }
}
