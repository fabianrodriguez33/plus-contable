package com.pluscontable.tributario.alertaplazo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/** HU-19: la tarea diaria corre a las 7:00 hora de Lima y genera las alertas de hoy. */
class AlertaPlazoProgramadorTest {

    @Test
    void correTodosLosDiasALas7DeLaMananaEnLaZonaDeLima() throws Exception {
        Method tarea = AlertaPlazoProgramador.class.getMethod("generarAlertasDelDia");

        Scheduled programada = tarea.getAnnotation(Scheduled.class);

        assertThat(programada).isNotNull();
        assertThat(programada.cron()).isEqualTo("0 0 7 * * *");
        assertThat(programada.zone()).isEqualTo("America/Lima");
    }

    @Test
    void laTareaGeneraLasAlertasDeHoy() {
        AlertaPlazoService service = mock(AlertaPlazoService.class);

        new AlertaPlazoProgramador(service).generarAlertasDelDia();

        verify(service).generarDeHoy();
    }
}
