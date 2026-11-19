package com.pluscontable.tributario.compras;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** Corre todos los días a las 7:10 de Lima (después de los procesos de HU-33 a las 7:00 y de HU-15 a las 7:05) y usa la fecha de Lima. */
@Configuration
public class AlertaComprasProgramador {
    private final AlertaComprasService service;
    private final Clock reloj;

    @Autowired // con dos constructores Spring necesita saber cuál usar
    public AlertaComprasProgramador(AlertaComprasService service) {
        this(service, Clock.system(ZoneId.of("America/Lima")));
    }

    AlertaComprasProgramador(AlertaComprasService service, Clock reloj) {
        this.service = service;
        this.reloj = reloj;
    }

    @Scheduled(cron = "0 10 7 * * *", zone = "America/Lima")
    public void avisarComprasSinValidar() {
        service.generar(LocalDate.now(reloj));
    }
}
