package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.dto.CobroHonorarioDto;
import com.pluscontable.tributario.dto.PagoHonorarioEntradaDto;
import com.pluscontable.tributario.dto.ProgramacionCobroEntradaDto;
import com.pluscontable.tributario.model.EstadoCobro;
import com.pluscontable.tributario.model.MedioPago;
import com.pluscontable.tributario.model.Periodicidad;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-29 · Tareas 140 y 143: la tabla {@code pagos_honorarios}, la columna {@code pagado} de los cobros y el pago de punta
 * a punta contra PostgreSQL de pruebas. Usa clientes ficticios que se borran al terminar (lo demás en cascada).
 */
@SpringBootTest
class PagoHonorarioEsquemaPostgresTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired CobroHonorarioService cobros;
    @Autowired PagoHonorarioService pagos;

    private final List<String> rucs = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (String ruc : rucs) {
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
        }
    }

    /** Un cliente con acuerdo de 450 y sus cobros mensuales programados; devuelve el primer cobro. */
    private CobroHonorarioDto clienteConCobros(long[] clienteId) {
        String ruc = RucFicticio.nuevo();
        rucs.add(ruc);
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario, activo) VALUES (?, ?, 'MYPE', true)",
                ruc, "Cliente ficticio (pagos)");
        clienteId[0] = jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
        LocalDate inicio = LocalDate.of(2026, 1, 15);
        jdbc.update("INSERT INTO tributario_schema.acuerdos_servicio (cliente_id, honorario_mensual, honorario_desde, fecha_inicio) VALUES (?, ?, ?, ?)",
                clienteId[0], new BigDecimal("450.00"), inicio, inicio);
        return cobros.programar(clienteId[0], new ProgramacionCobroEntradaDto(Periodicidad.MENSUAL)).cobros().get(0);
    }

    private static PagoHonorarioEntradaDto pago(String monto) {
        return new PagoHonorarioEntradaDto(LocalDate.now(), new BigDecimal(monto), MedioPago.TRANSFERENCIA, null);
    }

    @Test
    void unPagoParcialSeGuardaYElCobroSigueEntreLosPendientesConSuSaldo() {
        long[] cliente = new long[1];
        CobroHonorarioDto cobro = clienteConCobros(cliente);

        CobroHonorarioDto r = pagos.registrar(cliente[0], cobro.id(), pago("150.00"), "contador1");

        assertThat(r.estado()).isEqualTo(EstadoCobro.PAGO_PARCIAL);
        assertThat(r.saldo()).isEqualByComparingTo("300.00");
        assertThat(jdbc.queryForObject("SELECT medio_pago || '|' || registrado_por FROM tributario_schema.pagos_honorarios WHERE cobro_id = ?",
                String.class, cobro.id())).isEqualTo("TRANSFERENCIA|contador1");
        assertThat(cobros.pendientes(cliente[0])).hasSize(12).first().satisfies(c -> {
            assertThat(c.id()).isEqualTo(cobro.id());
            assertThat(c.pagado()).isEqualByComparingTo("150.00");
        });
    }

    @Test
    void unPagoConOtroGuardaElMedioEspecificado() {
        long[] cliente = new long[1];
        CobroHonorarioDto cobro = clienteConCobros(cliente);

        pagos.registrar(cliente[0], cobro.id(), new PagoHonorarioEntradaDto(LocalDate.now(), new BigDecimal("50.00"), MedioPago.OTRO,
                "Tarjeta de crédito"), "contador1");

        assertThat(jdbc.queryForObject("SELECT medio_pago || '|' || medio_pago_detalle FROM tributario_schema.pagos_honorarios WHERE cobro_id = ?",
                String.class, cobro.id())).isEqualTo("OTRO|Tarjeta de crédito");
    }

    @Test
    void completarElPagoLoDejaPagadoFueraDePendientesYNoAdmiteOtroPago() {
        long[] cliente = new long[1];
        CobroHonorarioDto cobro = clienteConCobros(cliente);
        pagos.registrar(cliente[0], cobro.id(), pago("150.00"), "contador1");

        CobroHonorarioDto r = pagos.registrar(cliente[0], cobro.id(), pago("300.00"), "contador1");

        assertThat(r.estado()).isEqualTo(EstadoCobro.PAGADO);
        assertThat(cobros.pendientes(cliente[0])).hasSize(11).noneMatch(c -> c.id().equals(cobro.id()));
        assertThatThrownBy(() -> pagos.registrar(cliente[0], cobro.id(), pago("1.00"), "contador1"))
                .isInstanceOf(ValidationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.pagos_honorarios WHERE cobro_id = ?",
                Integer.class, cobro.id())).isEqualTo(2);
    }

    @Test
    void dosPagosSimultaneosQueJuntosSuperanElSaldoSoloDejanPasarUno() throws Exception {
        long[] cliente = new long[1];
        CobroHonorarioDto cobro = clienteConCobros(cliente); // monto 450
        CyclicBarrier largada = new CyclicBarrier(2);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> pagar300 = () -> {
                largada.await(5, TimeUnit.SECONDS);
                try {
                    pagos.registrar(cliente[0], cobro.id(), pago("300.00"), "contador1");
                    return true;
                } catch (ValidationException e) {
                    return false; // el segundo ve el saldo de 150 y se rechaza
                }
            };
            List<Future<Boolean>> resultados = hilos.invokeAll(List.of(pagar300, pagar300));

            long exitosos = 0;
            for (Future<Boolean> r : resultados) if (r.get()) exitosos++;
            assertThat(exitosos).isEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.pagos_honorarios WHERE cobro_id = ?",
                Integer.class, cobro.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT pagado FROM tributario_schema.cobros_honorarios WHERE id = ?",
                BigDecimal.class, cobro.id())).isEqualByComparingTo("300.00");
    }

    @Test
    void conUnPagoRegistradoNoSePuedeCambiarLaPeriodicidadNiSePierdeElPago() {
        long[] cliente = new long[1];
        CobroHonorarioDto cobro = clienteConCobros(cliente);
        pagos.registrar(cliente[0], cobro.id(), pago("100.00"), "contador1");

        assertThatThrownBy(() -> cobros.programar(cliente[0], new ProgramacionCobroEntradaDto(Periodicidad.QUINCENAL)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("pagos registrados");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.pagos_honorarios WHERE cobro_id = ?",
                Integer.class, cobro.id())).isEqualTo(1);
    }

    @Test
    void unCobroYaPagadoAntesDeHoyEnEsteMesTambienImpideCambiarLaPeriodicidad() {
        long[] cliente = new long[1];
        clienteConCobros(cliente); // mensual: los cobros van de hoy en adelante
        long programacion = jdbc.queryForObject("SELECT id FROM tributario_schema.programaciones_cobro WHERE cliente_id = ?",
                Long.class, cliente[0]);
        // el cobro del día 1 de este mes, ya pagado (si hoy es día 1, es el de hoy)
        jdbc.update("INSERT INTO tributario_schema.cobros_honorarios (cliente_id, programacion_id, fecha_cobro, monto, estado, pagado) "
                + "VALUES (?, ?, ?, 450.00, 'PAGADO', 450.00)", cliente[0], programacion, LocalDate.now().withDayOfMonth(1));

        // si se dejara cambiar, el mes ya pagado se volvería a cobrar con las quincenas
        assertThatThrownBy(() -> cobros.programar(cliente[0], new ProgramacionCobroEntradaDto(Periodicidad.QUINCENAL)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("pagos registrados");
    }

    @Test
    void lasTablasRechazanMontosNoPositivosMediosFueraDeListaYPagadoMayorAlMonto() {
        long[] cliente = new long[1];
        CobroHonorarioDto cobro = clienteConCobros(cliente);
        String insertar = "INSERT INTO tributario_schema.pagos_honorarios (cobro_id, fecha_pago, monto, medio_pago, registrado_por) VALUES (?, ?, ?, ?, 'x')";

        assertThatThrownBy(() -> jdbc.update(insertar, cobro.id(), LocalDate.now(), BigDecimal.ZERO, "EFECTIVO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, cobro.id(), LocalDate.now(), BigDecimal.ONE, "CHEQUE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // «Otro» exige el detalle y los demás medios no lo admiten
        assertThatThrownBy(() -> jdbc.update(insertar, cobro.id(), LocalDate.now(), BigDecimal.ONE, "OTRO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        String conDetalle = "INSERT INTO tributario_schema.pagos_honorarios (cobro_id, fecha_pago, monto, medio_pago, medio_pago_detalle, registrado_por) "
                + "VALUES (?, ?, ?, ?, ?, 'x')";
        assertThatThrownBy(() -> jdbc.update(conDetalle, cobro.id(), LocalDate.now(), BigDecimal.ONE, "OTRO", "  "))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(conDetalle, cobro.id(), LocalDate.now(), BigDecimal.ONE, "EFECTIVO", "algo"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE tributario_schema.cobros_honorarios SET pagado = monto + 1 WHERE id = ?", cobro.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE tributario_schema.cobros_honorarios SET estado = 'ANULADO' WHERE id = ?", cobro.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void borrarElClienteBorraLosPagosDeSusCobros() {
        long[] cliente = new long[1];
        CobroHonorarioDto cobro = clienteConCobros(cliente);
        pagos.registrar(cliente[0], cobro.id(), pago("100.00"), "contador1");

        jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", cliente[0]);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.pagos_honorarios WHERE cobro_id = ?",
                Integer.class, cobro.id())).isZero();
    }
}
