package com.pluscontable.tributario.obligacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.obligacion.GeneradorObligaciones.Omision;
import com.pluscontable.tributario.service.ProgramacionMensualService;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-07, regla confirmada por Beto (02/11/2026): IGV y RENTA se generan siempre y con el mismo cronograma en MYPE, RER y GENERAL;
 * solo cambia el cálculo del impuesto, no la fecha ni los tipos del mes. RER reemplazó a REMIPE el 10/11. Contra PostgreSQL real; cada
 * prueba se revierte al terminar. Lo que todavía no está confirmado (qué tributos más corresponden a cada régimen, ESSALUD, quinta y AFP)
 * no se prueba aquí: ver las preguntas pendientes en el PR.
 */
@SpringBootTest
class RegimenProgramacionPostgresTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ProgramacionMensualService programacion;

    /** RUC ficticio terminado en 9 u 8: ambos son del grupo 8-9 del cronograma. */
    private Long cliente(String ruc, String regimen) {
        return jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario)
                VALUES (?, 'Cliente de régimen HU-07', ?) RETURNING id
                """, Long.class, ruc, regimen);
    }

    private void trabajador(Long clienteId) {
        jdbc.update("""
                INSERT INTO tributario_schema.trabajadores
                    (cliente_id, nombre_completo, tipo_documento, numero_documento, sistema_pensionario, fecha_ingreso, remuneracion)
                VALUES (?, 'Trabajador HU-07', 'DNI', '12345678', 'ONP', DATE '2025-01-01', 1500.00)
                """, clienteId);
    }

    private List<String> filas(Long clienteId) {
        return jdbc.queryForList("""
                SELECT tipo_obligacion || ' ' || fecha_vencimiento FROM tributario_schema.obligaciones
                WHERE cliente_id = ? AND periodo = DATE '2026-01-01' ORDER BY tipo_obligacion
                """, String.class, clienteId);
    }

    @ParameterizedTest
    @CsvSource({"20999999991,MYPE", "20999999992,RER", "20999999993,GENERAL"})
    @Transactional
    void sinTrabajadoresCadaRegimenRecibeIgvYRentaConLaFechaDeSuGrupoDelRuc(String ruc, String regimen) {
        Long id = cliente(ruc, regimen);

        var r = programacion.programar(id, YearMonth.of(2026, 1));

        // los RUC terminados en 1, 2 y 3 caen en grupos distintos; se compara cada uno con la fecha de su grupo en el cronograma
        String fechaDelGrupo = jdbc.queryForObject("""
                SELECT fecha_vencimiento FROM tributario_schema.cronograma_tributario
                WHERE periodo_tributario = DATE '2026-01-01' AND ultimo_digito_ruc = ?
                """, String.class, GrupoRucCronograma.paraCliente(ruc, false));
        assertThat(filas(id)).containsExactly("IGV " + fechaDelGrupo, "RENTA " + fechaDelGrupo);
        assertThat(r.creadas()).hasSize(2);
        assertThat(r.noGeneradas()).extracting(Omision::tipo).containsExactlyInAnyOrder(TipoObligacion.PLAME,
                TipoObligacion.ESSALUD, TipoObligacion.RETENCION_QUINTA, TipoObligacion.AFP);
    }

    @Test
    @Transactional
    void conElMismoUltimoDigitoLosTresRegimenesReciben_LosMismosTiposYLaMismaFecha() {
        Long mype = cliente("20999999989", "MYPE");
        Long rer = cliente("20999999979", "RER");
        Long general = cliente("20999999969", "GENERAL");
        for (Long id : List.of(mype, rer, general)) trabajador(id);

        var rMype = programacion.programar(mype, YearMonth.of(2026, 1));
        var rRer = programacion.programar(rer, YearMonth.of(2026, 1));
        var rGeneral = programacion.programar(general, YearMonth.of(2026, 1));

        assertThat(filas(mype)).containsExactly("IGV 2026-02-23", "PLAME 2026-02-23", "RENTA 2026-02-23");
        assertThat(filas(rer)).isEqualTo(filas(mype));
        assertThat(filas(general)).isEqualTo(filas(mype));
        assertThat(rRer.noGeneradas()).isEqualTo(rMype.noGeneradas());
        assertThat(rGeneral.noGeneradas()).isEqualTo(rMype.noGeneradas());
        assertThat(rRer.fechaVencimiento()).isEqualTo(rMype.fechaVencimiento()).isEqualTo(rGeneral.fechaVencimiento());
    }

    @Test
    @Transactional
    void cambiarElRegimenNoRecalculaLoYaProgramadoNiAgregaNada() {
        Long id = cliente("20999999959", "MYPE");
        trabajador(id);
        programacion.programar(id, YearMonth.of(2026, 1));
        List<String> antes = filas(id);

        jdbc.update("UPDATE tributario_schema.clientes SET regimen_tributario = 'RER' WHERE id = ?", id);
        var otraVez = programacion.programar(id, YearMonth.of(2026, 1));

        assertThat(filas(id)).isEqualTo(antes);
        assertThat(otraVez.creadas()).isEmpty();
    }
}
