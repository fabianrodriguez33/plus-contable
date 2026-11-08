package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.model.CuotaFraccionamiento;
import com.pluscontable.tributario.model.EstadoCuota;
import com.pluscontable.tributario.model.Fraccionamiento;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import com.pluscontable.tributario.repository.CuotaFraccionamientoRepository;
import com.pluscontable.tributario.repository.FraccionamientoRepository;
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

/**
 * HU-17 · Tarea 23: las tablas {@code fraccionamientos} y {@code cuotas_fraccionamiento} que crea schema.sql (requiere
 * PostgreSQL de pruebas). Usa un cliente ficticio que se borra al terminar; lo demás se borra en cascada.
 */
@SpringBootTest
class FraccionamientoEsquemaPostgresTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired FraccionamientoRepository fraccionamientos;
    @Autowired CuotaFraccionamientoRepository cuotas;

    private String ruc;
    private long cliente;

    @BeforeEach
    void crearCliente() {
        ruc = RucFicticio.nuevo();
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario) VALUES (?, ?, 'MYPE')",
                ruc, "Cliente ficticio (fraccionamiento)");
        cliente = jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
    }

    @AfterEach
    void borrarCliente() {
        jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
    }

    private long insertarFraccionamiento(String tributo, String monto, int cuotasTotal) {
        jdbc.update("INSERT INTO tributario_schema.fraccionamientos (cliente_id, tributo, monto_total, numero_cuotas, fecha_primera_cuota) VALUES (?, ?, ?, ?, ?)",
                cliente, tributo, new BigDecimal(monto), cuotasTotal, LocalDate.of(2026, 12, 15));
        return jdbc.queryForObject("SELECT max(id) FROM tributario_schema.fraccionamientos WHERE cliente_id = ?", Long.class, cliente);
    }

    private int insertarCuota(long fraccionamiento, int numero, String monto) {
        return jdbc.update("INSERT INTO tributario_schema.cuotas_fraccionamiento (fraccionamiento_id, numero, fecha_vencimiento, monto) VALUES (?, ?, ?, ?)",
                fraccionamiento, numero, LocalDate.of(2026, 12, 15).plusMonths(numero - 1L), new BigDecimal(monto));
    }

    private int cuotasDe(long fraccionamiento) {
        return jdbc.queryForObject("SELECT count(*) FROM tributario_schema.cuotas_fraccionamiento WHERE fraccionamiento_id = ?", Integer.class, fraccionamiento);
    }

    @Test
    void lasTablasTienenLasColumnasDelDiseno() {
        assertThat(columnas("fraccionamientos")).containsExactlyInAnyOrder("id", "cliente_id", "tributo", "monto_total",
                "numero_cuotas", "fecha_primera_cuota", "registrado_en");
        assertThat(columnas("cuotas_fraccionamiento")).containsExactlyInAnyOrder("id", "fraccionamiento_id", "numero",
                "fecha_vencimiento", "monto", "estado", "fecha_pago");
    }

    private List<String> columnas(String tabla) {
        return jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = 'tributario_schema' AND table_name = ?", String.class, tabla);
    }

    @Test
    void elTributoAdmiteTodosLosDeTipoObligacionIncluidosEssaludQuintaYAfp() {
        for (TipoObligacion t : TipoObligacion.values()) {
            assertThat(insertarFraccionamiento(t.name(), "600.00", 3)).isPositive();
        }

        assertThatThrownBy(() -> insertarFraccionamiento("OTRO", "600.00", 3)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elMontoDebeSerMayorQueCeroYLasCuotasAlMenosDos() {
        assertThatThrownBy(() -> insertarFraccionamiento("IGV", "0", 3)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarFraccionamiento("IGV", "-5.00", 3)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarFraccionamiento("IGV", "600.00", 1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unaCuotaNaceConEstadoPendienteYSinFechaDePago() {
        long f = insertarFraccionamiento("IGV", "600.00", 2);
        insertarCuota(f, 1, "300.00");

        assertThat(jdbc.queryForObject("SELECT estado FROM tributario_schema.cuotas_fraccionamiento WHERE fraccionamiento_id = ?", String.class, f))
                .isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("SELECT fecha_pago FROM tributario_schema.cuotas_fraccionamiento WHERE fraccionamiento_id = ?", LocalDate.class, f))
                .isNull();
    }

    @Test
    void elNumeroDeCuotaNoSeRepiteEnUnFraccionamientoPeroSiEnOtro() {
        long f1 = insertarFraccionamiento("IGV", "600.00", 2);
        long f2 = insertarFraccionamiento("RENTA", "600.00", 2);
        insertarCuota(f1, 1, "300.00");

        assertThatThrownBy(() -> insertarCuota(f1, 1, "300.00")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(insertarCuota(f2, 1, "300.00")).isEqualTo(1);
    }

    @Test
    void laCuotaTieneMontoPositivoYEstadoPendienteOPagada() {
        long f = insertarFraccionamiento("IGV", "600.00", 2);
        insertarCuota(f, 1, "300.00");

        assertThatThrownBy(() -> insertarCuota(f, 2, "0")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE tributario_schema.cuotas_fraccionamiento SET estado = 'VENCIDA' WHERE fraccionamiento_id = ?", f))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("UPDATE tributario_schema.cuotas_fraccionamiento SET estado = 'PAGADA' WHERE fraccionamiento_id = ?", f)).isEqualTo(1);
    }

    @Test
    void lasEntidadesGuardanYElCalendarioLeeLasCuotasDelClientePorRango() {
        Fraccionamiento f = new Fraccionamiento();
        f.setClienteId(cliente);
        f.setTributo(TipoObligacion.ESSALUD);
        f.setMontoTotal(new BigDecimal("900.00"));
        f.setNumeroCuotas(3);
        f.setFechaPrimeraCuota(LocalDate.of(2026, 12, 15));
        Fraccionamiento guardado = fraccionamientos.saveAndFlush(f);
        for (int i = 1; i <= 3; i++) {
            CuotaFraccionamiento c = new CuotaFraccionamiento();
            c.setFraccionamientoId(guardado.getId());
            c.setNumero(i);
            c.setFechaVencimiento(LocalDate.of(2026, 12, 15).plusMonths(i - 1L));
            c.setMonto(new BigDecimal("300.00"));
            cuotas.saveAndFlush(c);
        }

        List<CuotaFraccionamiento> rango = cuotas.delClienteEntre(cliente, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 28));

        assertThat(rango).extracting(CuotaFraccionamiento::getNumero).containsExactly(2, 3);
        assertThat(rango).extracting(CuotaFraccionamiento::getEstado).containsOnly(EstadoCuota.PENDIENTE);
        assertThat(cuotas.delClienteEntre(cliente, LocalDate.of(1900, 1, 1), LocalDate.of(2999, 12, 31))).hasSize(3);
        assertThat(cuotas.delClienteEntre(cliente + 100000, LocalDate.of(1900, 1, 1), LocalDate.of(2999, 12, 31))).isEmpty();
        assertThat(fraccionamientos.findByClienteIdOrderByRegistradoEnDescIdDesc(cliente)).hasSize(1);
        assertThat(cuotas.findByFraccionamientoIdOrderByNumero(guardado.getId())).hasSize(3);
    }

    @Test
    void alBorrarElClienteSeBorranSusFraccionamientosYCuotas() {
        long f = insertarFraccionamiento("IGV", "600.00", 2);
        insertarCuota(f, 1, "300.00");
        assertThat(cuotasDe(f)).isEqualTo(1);

        jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", cliente);

        assertThat(cuotasDe(f)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tributario_schema.fraccionamientos WHERE id = ?", Integer.class, f)).isZero();
    }
}
