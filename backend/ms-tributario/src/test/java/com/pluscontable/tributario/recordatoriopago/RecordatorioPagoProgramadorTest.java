package com.pluscontable.tributario.recordatoriopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pluscontable.tributario.avisocliente.AvisoClienteProgramador;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

/**
 * HU-15: el proceso diario que entrega los recordatorios de pago. Detecta un cambio de la hora, de la zona o del día que usa: «hoy» y
 * la hora son los de Lima, no los de la máquina, y corre después del proceso de avisos automáticos de HU-33.
 */
class RecordatorioPagoProgramadorTest {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final TimeZone zonaOriginal = TimeZone.getDefault();
    private final RecordatorioPagoService service = mock(RecordatorioPagoService.class);

    @AfterEach
    void restaurarZona() {
        TimeZone.setDefault(zonaOriginal);
    }

    @Test
    void correTodosLosDiasALas705DeLima() throws Exception {
        var cron = RecordatorioPagoProgramador.class.getMethod("entregarRecordatoriosDelDia").getAnnotation(Scheduled.class);

        assertThat(cron.cron()).isEqualTo("0 5 7 * * *");
        assertThat(cron.zone()).isEqualTo("America/Lima");
    }

    @Test
    void laExpresionDispara7_05CadaDiaEnLimaYNoEnOtraHora() throws Exception {
        var cron = RecordatorioPagoProgramador.class.getMethod("entregarRecordatoriosDelDia").getAnnotation(Scheduled.class);
        var expresion = CronExpression.parse(cron.cron());

        var dia = ZonedDateTime.of(2026, 11, 18, 0, 0, 0, 0, LIMA);
        var primera = expresion.next(dia);
        var siguiente = expresion.next(primera);

        assertThat(primera).isEqualTo(ZonedDateTime.of(2026, 11, 18, 7, 5, 0, 0, LIMA));
        assertThat(siguiente).isEqualTo(ZonedDateTime.of(2026, 11, 19, 7, 5, 0, 0, LIMA));   // todos los días, una vez
    }

    @Test
    void correDespuesDelProcesoDeAvisosAutomaticosDeHu33() throws Exception {
        var hu15 = CronExpression.parse(RecordatorioPagoProgramador.class.getMethod("entregarRecordatoriosDelDia")
                .getAnnotation(Scheduled.class).cron());
        var hu33 = CronExpression.parse(AvisoClienteProgramador.class.getMethod("generarAvisosDelDia")
                .getAnnotation(Scheduled.class).cron());
        var medianoche = ZonedDateTime.of(2026, 11, 18, 0, 0, 0, 0, LIMA);

        assertThat(hu15.next(medianoche)).isAfter(hu33.next(medianoche));
    }

    @Test
    void usaElDiaDeLimaAunqueEnUtcYaSeaOtroDia() {
        // 17/11 22:00 en Lima = 18/11 03:00 UTC
        var reloj = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), LIMA);

        new RecordatorioPagoProgramador(service, reloj).entregarRecordatoriosDelDia();

        verify(service).entregar(LocalDate.of(2026, 11, 17));
    }

    @Test
    void elConstructorRealUsaLimaAunqueLaMaquinaEsteEnOtraZona() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati")); // UTC+14: casi siempre otro día que Lima
        var antes = LocalDate.now(LIMA);

        new RecordatorioPagoProgramador(service).entregarRecordatoriosDelDia();

        var dia = ArgumentCaptor.forClass(LocalDate.class);
        verify(service).entregar(dia.capture());
        assertThat(dia.getValue()).isBetween(antes, LocalDate.now(LIMA));
    }
}
