package com.pluscontable.tributario.alertaplazo;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** HU-19: genera las alertas una vez al día, a las 7:00 hora de Lima (el servicio usa el «hoy» de Lima). */
@Configuration
@EnableScheduling
public class AlertaPlazoProgramador {

    private final AlertaPlazoService service;

    public AlertaPlazoProgramador(AlertaPlazoService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "America/Lima")
    public void generarAlertasDelDia() {
        service.generarDeHoy();
    }
}
