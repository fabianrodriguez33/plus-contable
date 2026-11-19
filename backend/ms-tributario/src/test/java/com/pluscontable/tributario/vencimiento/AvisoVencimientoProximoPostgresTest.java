package com.pluscontable.tributario.vencimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.aviso.AvisoInternoDto;
import com.pluscontable.tributario.aviso.AvisoInternoService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** HU-11 con PostgreSQL desechable: qué obligaciones avisan, que no se duplique y qué pasa al cambiar datos. */
@SpringBootTest
class AvisoVencimientoProximoPostgresTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 18);

    @Autowired AvisoVencimientoProximoService servicio;
    @Autowired AvisoInternoService avisos;
    @Autowired JdbcTemplate jdbc;
    private final List<Long> clientes = new ArrayList<>();

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

    private int mes = 0;

    /** Cada obligación usa un periodo distinto: hay unicidad por cliente, tipo y periodo. */
    private long obligacion(long cliente, String estado, LocalDate vence) {
        LocalDate periodo = LocalDate.of(2020, 1, 1).plusMonths(mes++);
        return jdbc.queryForObject("""
                INSERT INTO tributario_schema.obligaciones(cliente_id, tipo_obligacion, periodo, fecha_vencimiento, estado)
                VALUES (?, 'IGV', ?, ?, ?) RETURNING id""", Long.class, cliente, periodo, vence, estado);
    }

    private List<AvisoInternoDto> avisosDe(long cliente) {
        return avisos.bandeja(null).stream().filter(a -> a.clienteIds().contains(cliente)).toList();
    }

    @Test
    void avisaSoloPendientesDeClientesActivosDentroDeLaVentana() {
        long activo = cliente(true, "Empresa Activa SAC");
        long inactivo = cliente(false, "Empresa Inactiva SAC");
        obligacion(activo, "PENDIENTE", HOY);                    // hoy: entra
        obligacion(activo, "PENDIENTE", HOY.plusDays(3));        // último día de la ventana: entra
        obligacion(activo, "PENDIENTE", HOY.plusDays(4));        // fuera
        obligacion(activo, "PENDIENTE", HOY.minusDays(1));       // ya venció: no es próxima
        obligacion(activo, "DECLARADO", HOY.plusDays(1));        // no pendiente
        obligacion(activo, "PAGADO", HOY.plusDays(1));           // no pendiente
        obligacion(inactivo, "PENDIENTE", HOY.plusDays(1));      // cliente inactivo

        var r = servicio.generar(HOY, 3);

        assertThat(avisosDe(activo)).hasSize(2).allSatisfy(a -> {
            assertThat(a.origen()).isEqualTo("HU11_VENCIMIENTO");
            assertThat(a.mensaje()).contains("Empresa Activa SAC", "IGV", "2020-");
        });
        assertThat(avisosDe(inactivo)).isEmpty();
        assertThat(r.avisosCreados()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void ejecutarDosVecesNoDuplicaYSiCambiaLaRazonSocialNoFalla() {
        long c = cliente(true, "Nombre Original SAC");
        obligacion(c, "PENDIENTE", HOY.plusDays(2));
        servicio.generar(HOY, 3);
        var segunda = servicio.generar(HOY, 3);
        assertThat(avisosDe(c)).hasSize(1);
        assertThat(segunda.yaAvisadas()).isGreaterThanOrEqualTo(1);

        jdbc.update("UPDATE tributario_schema.clientes SET razon_social = 'Nombre Nuevo SAC' WHERE id = ?", c);
        servicio.generar(HOY.plusDays(1), 3);
        assertThat(avisosDe(c)).hasSize(1);
    }

    @Test
    void siCambiaLaFechaDeVencimientoSeAvisaDeNuevoConLaFechaNueva() {
        long c = cliente(true, "Empresa Fechas SAC");
        long o = obligacion(c, "PENDIENTE", HOY.plusDays(2));
        servicio.generar(HOY, 3);
        jdbc.update("UPDATE tributario_schema.obligaciones SET fecha_vencimiento = ? WHERE id = ?", HOY.plusDays(3), o);
        servicio.generar(HOY, 3);
        assertThat(avisosDe(c)).hasSize(2);
        assertThat(avisosDe(c).stream().map(AvisoInternoDto::mensaje).toList())
                .anyMatch(m -> m.contains("21/11/2026")).anyMatch(m -> m.contains("20/11/2026"));
    }

    @Test
    void obligacionYaDeclaradaDespuesDeAvisarNoGeneraOtroAviso() {
        long c = cliente(true, "Empresa Declara SAC");
        long o = obligacion(c, "PENDIENTE", HOY.plusDays(1));
        servicio.generar(HOY, 3);
        jdbc.update("UPDATE tributario_schema.obligaciones SET estado = 'DECLARADO' WHERE id = ?", o);
        servicio.generar(HOY.plusDays(1), 3);
        assertThat(avisosDe(c)).hasSize(1);
    }

    @Test
    void rechazaDiasNegativosYFechaNula() {
        assertThatThrownBy(() -> servicio.generar(HOY, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.generar(null, 3)).isInstanceOf(NullPointerException.class);
    }
}
