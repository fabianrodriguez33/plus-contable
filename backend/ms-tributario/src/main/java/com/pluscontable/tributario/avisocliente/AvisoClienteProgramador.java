package com.pluscontable.tributario.avisocliente;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Corre una vez al día, 7:00 hora de Lima, y usa la fecha de Lima como «hoy». */
@Configuration
@EnableScheduling
public class AvisoClienteProgramador {
    private final AvisoClienteService service;

    public AvisoClienteProgramador(AvisoClienteService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "America/Lima")
    public void generarAvisosDelDia() {
        service.generar(LocalDate.now(ZoneId.of("America/Lima")));
    }
}
