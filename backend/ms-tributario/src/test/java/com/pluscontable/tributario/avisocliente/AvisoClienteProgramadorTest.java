package com.pluscontable.tributario.avisocliente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;

/** HU-33: «hoy» y la hora del proceso diario son los de Lima, no los de la máquina. */
class AvisoClienteProgramadorTest {
    private final TimeZone zonaOriginal = TimeZone.getDefault();
    private final AvisoClienteService service = mock(AvisoClienteService.class);

    @AfterEach
    void restaurarZona() {
        TimeZone.setDefault(zonaOriginal);
    }

    @Test
    void usaElDiaDeLimaAunqueEnUtcYaSeaOtroDia() {
        // 17/11 22:00 en Lima = 18/11 03:00 UTC
        var reloj = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("America/Lima"));

        new AvisoClienteProgramador(service, reloj).generarAvisosDelDia();

        verify(service).generar(LocalDate.of(2026, 11, 17));
    }

    @Test
    void elConstructorRealUsaLimaAunqueLaMaquinaEsteEnOtraZona() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati")); // UTC+14: casi siempre otro día que Lima
        var antes = LocalDate.now(ZoneId.of("America/Lima"));

        new AvisoClienteProgramador(service).generarAvisosDelDia();

        var dia = ArgumentCaptor.forClass(LocalDate.class);
        verify(service).generar(dia.capture());
        assertThat(dia.getValue()).isBetween(antes, LocalDate.now(ZoneId.of("America/Lima")));
    }

    @Test
    void correTodosLosDiasALas7DeLima() throws Exception {
        var cron = AvisoClienteProgramador.class.getMethod("generarAvisosDelDia").getAnnotation(Scheduled.class);

        assertThat(cron.cron()).isEqualTo("0 0 7 * * *");
        assertThat(cron.zone()).isEqualTo("America/Lima");
    }
}
