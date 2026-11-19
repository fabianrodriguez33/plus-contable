package com.pluscontable.tributario.recordatoriopago;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** Entrega los recordatorios del día a las 7:05 de Lima, justo después del job de avisos automáticos de HU-33 (7:00). */
@Configuration
public class RecordatorioPagoProgramador {
    private final RecordatorioPagoService service;
    private final Clock reloj;

    @Autowired // con dos constructores Spring necesita saber cuál usar
    public RecordatorioPagoProgramador(RecordatorioPagoService service) {
        this(service, Clock.system(ZoneId.of("America/Lima")));
    }

    RecordatorioPagoProgramador(RecordatorioPagoService service, Clock reloj) {
        this.service = service;
        this.reloj = reloj;
    }

    @Scheduled(cron = "0 5 7 * * *", zone = "America/Lima")
    public void entregarRecordatoriosDelDia() {
        service.entregar(LocalDate.now(reloj));
    }
}
