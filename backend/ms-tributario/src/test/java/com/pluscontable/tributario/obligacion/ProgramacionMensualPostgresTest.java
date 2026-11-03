package com.pluscontable.tributario.obligacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.obligacion.GeneradorObligaciones.Omision;
import com.pluscontable.tributario.service.ProgramacionMensualService;
import jakarta.validation.ValidationException;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-07 (tareas 58, 59 y 60) contra PostgreSQL real. Cada prueba se revierte al terminar.
 * Se ejecuta únicamente contra la base de pruebas indicada por el POM.
 */
@SpringBootTest
class ProgramacionMensualPostgresTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ProgramacionMensualService programacion;
    @Autowired ParametrosTributarios parametros;

    /** RUC terminado en 9: grupo 8-9 del cronograma. */
    private Long cliente() {
        return jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario)
                VALUES ('20999999999', 'Cliente de programación HU-07', 'GENERAL') RETURNING id
                """, Long.class);
    }

    private void trabajador(Long clienteId, String remuneracion) {
        jdbc.update("""
                INSERT INTO tributario_schema.trabajadores
                    (cliente_id, nombre_completo, tipo_documento, numero_documento, sistema_pensionario,
                     fecha_ingreso, remuneracion)
                VALUES (?, 'Trabajador HU-07', 'DNI', '12345678', 'ONP', DATE '2025-01-01', ?::numeric)
                """, clienteId, remuneracion);
    }

    private List<String> filas(Long clienteId, String periodo) {
        return jdbc.queryForList("""
                SELECT tipo_obligacion || ' ' || fecha_vencimiento FROM tributario_schema.obligaciones
                WHERE cliente_id = ? AND periodo = ?::date ORDER BY tipo_obligacion
                """, String.class, clienteId, periodo);
    }

    @Test
    @Transactional
    void programaElMesConLaFechaDelGrupoDelRucYSinAfpNiQuinta() {
        Long id = cliente();
        trabajador(id, "1500.00");

        var r = programacion.programar(id, YearMonth.of(2026, 1));

        assertThat(r.creadas()).hasSize(4);
        assertThat(filas(id, "2026-01-01")).containsExactly("ESSALUD 2026-02-23", "IGV 2026-02-23",
                "PLAME 2026-02-23", "RENTA 2026-02-23");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM tributario_schema.obligaciones WHERE cliente_id = ? AND tipo_obligacion = 'AFP'
                """, Integer.class, id)).isZero();
        assertThat(r.noGeneradas()).extracting(Omision::tipo)
                .containsExactlyInAnyOrder(TipoObligacion.AFP, TipoObligacion.RETENCION_QUINTA);
    }

    @Test
    @Transactional
    void sinTrabajadoresSoloSeProgramanIgvYRenta() {
        Long id = cliente();

        var r = programacion.programar(id, YearMonth.of(2026, 1));

        assertThat(filas(id, "2026-01-01")).containsExactly("IGV 2026-02-23", "RENTA 2026-02-23");
        assertThat(r.noGeneradas()).extracting(Omision::tipo).containsExactlyInAnyOrder(TipoObligacion.PLAME,
                TipoObligacion.ESSALUD, TipoObligacion.RETENCION_QUINTA, TipoObligacion.AFP);
    }

    @Test
    @Transactional
    void sinRemuneracionSeProgramaPlamePeroNoEssalud() {
        Long id = cliente();
        jdbc.update("""
                INSERT INTO tributario_schema.trabajadores
                    (cliente_id, nombre_completo, tipo_documento, numero_documento, sistema_pensionario, fecha_ingreso)
                VALUES (?, 'Sin remuneración', 'DNI', '12345679', 'ONP', DATE '2025-01-01')
                """, id);

        programacion.programar(id, YearMonth.of(2026, 1));

        assertThat(filas(id, "2026-01-01")).containsExactly("IGV 2026-02-23", "PLAME 2026-02-23",
                "RENTA 2026-02-23");
    }

    @Test
    void laQuintaViajaApagadaYSinNingunMontoDeUitEnLaConfiguracion() {
        assertThat(parametros.isQuintaActiva()).isFalse();
        assertThat(parametros.getUit()).isEmpty();
        assertThat(parametros.sueldosAlAnio()).isEmpty();
    }

    @Test
    @Transactional
    void programarDosVecesElMismoMesNoDuplica() {
        Long id = cliente();
        trabajador(id, "1500.00");
        programacion.programar(id, YearMonth.of(2026, 1));

        var segunda = programacion.programar(id, YearMonth.of(2026, 1));

        assertThat(segunda.creadas()).isEmpty();
        assertThat(segunda.yaExistian()).hasSize(4);
        assertThat(filas(id, "2026-01-01")).hasSize(4);
    }

    @Test
    @Transactional
    void sinCronogramaDelPeriodoNoQuedaNingunaFila() {
        Long id = cliente();

        assertThatThrownBy(() -> programacion.programar(id, YearMonth.of(2027, 1)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Falta el cronograma de 2027-01");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.obligaciones WHERE cliente_id = ?",
                Integer.class, id)).isZero();
    }

    @Test
    @Transactional
    void sinElGrupoDelClienteEnElCronogramaTambienBloquea() {
        Long id = cliente();
        jdbc.update("""
                DELETE FROM tributario_schema.cronograma_tributario
                WHERE periodo_tributario = DATE '2026-01-01' AND ultimo_digito_ruc = '8-9'
                """);

        assertThatThrownBy(() -> programacion.programar(id, YearMonth.of(2026, 1)))
                .isInstanceOf(ValidationException.class).hasMessage("Falta el cronograma de 2026-01");
    }

    @Test
    @Transactional
    void elBuenContribuyenteUsaLaFechaDeSuGrupoYNoLaDelUltimoDigito() {
        Long id = cliente();
        trabajador(id, "1500.00");
        jdbc.update("UPDATE tributario_schema.clientes SET buen_contribuyente = TRUE WHERE id = ?", id);

        programacion.programar(id, YearMonth.of(2026, 2));

        assertThat(filas(id, "2026-02-01")).hasSize(4).allMatch(f -> f.endsWith("2026-03-24"));
    }

    @Test
    @Transactional
    void cambiarLaMarcaNoRecalculaLoYaProgramado() {
        Long id = cliente();
        trabajador(id, "1500.00");
        programacion.programar(id, YearMonth.of(2026, 2));
        assertThat(filas(id, "2026-02-01")).hasSize(4).allMatch(f -> f.endsWith("2026-03-23"));

        jdbc.update("UPDATE tributario_schema.clientes SET buen_contribuyente = TRUE WHERE id = ?", id);
        var otraVez = programacion.programar(id, YearMonth.of(2026, 2));

        assertThat(otraVez.creadas()).isEmpty();
        assertThat(filas(id, "2026-02-01")).hasSize(4).allMatch(f -> f.endsWith("2026-03-23"));
    }

    @Test
    @Transactional
    void unTrabajadorNuevoAgregaLoQueFaltaSinTocarLoYaProgramado() {
        Long id = cliente();
        programacion.programar(id, YearMonth.of(2026, 1));
        assertThat(filas(id, "2026-01-01")).containsExactly("IGV 2026-02-23", "RENTA 2026-02-23");

        trabajador(id, "1500.00");
        var otraVez = programacion.programar(id, YearMonth.of(2026, 1));

        assertThat(otraVez.yaExistian()).containsExactlyInAnyOrder(TipoObligacion.IGV, TipoObligacion.RENTA);
        assertThat(otraVez.creadas()).extracting(o -> o.tipoObligacion())
                .containsExactlyInAnyOrder(TipoObligacion.PLAME, TipoObligacion.ESSALUD);
        assertThat(filas(id, "2026-01-01")).hasSize(4);
    }

    @Test
    @Transactional
    void losClientesExistentesQuedanSinMarcaDeBuenContribuyente() {
        Long id = cliente();

        assertThat(jdbc.queryForObject(
                "SELECT buen_contribuyente FROM tributario_schema.clientes WHERE id = ?", Boolean.class, id)).isFalse();
    }

    @Test
    @Transactional
    void losVencimientosQuedanEnElClienteCorrecto() {
        Long a = cliente();
        Long b = jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario)
                VALUES ('20999999998', 'Otro cliente HU-07', 'MYPE') RETURNING id
                """, Long.class);

        trabajador(a, "1500.00");
        trabajador(b, "1500.00");

        programacion.programar(a, YearMonth.of(2026, 1));

        assertThat(filas(a, "2026-01-01")).hasSize(4);
        assertThat(filas(b, "2026-01-01")).isEmpty();
    }
}
