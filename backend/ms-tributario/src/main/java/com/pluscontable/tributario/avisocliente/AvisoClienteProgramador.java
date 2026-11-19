package com.pluscontable.tributario.avisocliente;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Corre una vez al día, 7:00 hora de Lima, y usa la fecha de Lima como «hoy». */
@Configuration
@EnableScheduling
public class AvisoClienteProgramador {
    private final AvisoClienteService service;
    private final Clock reloj;

    @Autowired // con dos constructores Spring necesita saber cuál usar
    public AvisoClienteProgramador(AvisoClienteService service) {
        this(service, Clock.system(ZoneId.of("America/Lima")));
    }

    AvisoClienteProgramador(AvisoClienteService service, Clock reloj) {
        this.service = service;
        this.reloj = reloj;
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "America/Lima")
    public void generarAvisosDelDia() {
        service.generar(LocalDate.now(reloj));
    }
}
