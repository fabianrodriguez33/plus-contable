package com.pluscontable.tributario.compras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pluscontable.tributario.recordatoriopago.RecordatorioPagoProgramador;
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

/** HU-25 · T212: el proceso diario de la alerta al contador. Detecta un cambio de la hora, de la zona o del día que usa. */
class AlertaComprasProgramadorTest {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final TimeZone zonaOriginal = TimeZone.getDefault();
    private final AlertaComprasService service = mock(AlertaComprasService.class);

    @AfterEach
    void restaurarZona() {
        TimeZone.setDefault(zonaOriginal);
    }

    private static Scheduled cron() throws Exception {
        return AlertaComprasProgramador.class.getMethod("avisarComprasSinValidar").getAnnotation(Scheduled.class);
    }

    @Test
    void correTodosLosDiasALas710DeLima() throws Exception {
        assertThat(cron().cron()).isEqualTo("0 10 7 * * *");
        assertThat(cron().zone()).isEqualTo("America/Lima");
        var expresion = CronExpression.parse(cron().cron());
        var primera = expresion.next(ZonedDateTime.of(2026, 11, 18, 0, 0, 0, 0, LIMA));
        assertThat(primera).isEqualTo(ZonedDateTime.of(2026, 11, 18, 7, 10, 0, 0, LIMA));
        assertThat(expresion.next(primera)).isEqualTo(ZonedDateTime.of(2026, 11, 19, 7, 10, 0, 0, LIMA));
    }

    @Test
    void correDespuesDelProcesoDeRecordatoriosDeHu15() throws Exception {
        var hu15 = CronExpression.parse(RecordatorioPagoProgramador.class.getMethod("entregarRecordatoriosDelDia")
                .getAnnotation(Scheduled.class).cron());
        var medianoche = ZonedDateTime.of(2026, 11, 18, 0, 0, 0, 0, LIMA);
        assertThat(CronExpression.parse(cron().cron()).next(medianoche)).isAfter(hu15.next(medianoche));
    }

    @Test
    void usaElDiaDeLimaAunqueEnUtcYaSeaOtroDia() {
        // 17/11 22:00 en Lima = 18/11 03:00 UTC
        new AlertaComprasProgramador(service, Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), LIMA)).avisarComprasSinValidar();
        verify(service).generar(LocalDate.of(2026, 11, 17));
    }

    @Test
    void elConstructorRealUsaLimaAunqueLaMaquinaEsteEnOtraZona() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati")); // UTC+14
        var antes = LocalDate.now(LIMA);

        new AlertaComprasProgramador(service).avisarComprasSinValidar();

        var dia = ArgumentCaptor.forClass(LocalDate.class);
        verify(service).generar(dia.capture());
        assertThat(dia.getValue()).isBetween(antes, LocalDate.now(LIMA));
    }
}
