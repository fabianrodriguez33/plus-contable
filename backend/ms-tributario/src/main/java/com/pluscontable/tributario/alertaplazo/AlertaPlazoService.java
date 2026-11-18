package com.pluscontable.tributario.alertaplazo;

import com.pluscontable.tributario.alertaplazo.AlertaPlazoRepositorio.Candidata;
import com.pluscontable.tributario.aviso.AvisoInternoDto;
import com.pluscontable.tributario.aviso.AvisoInternoService;
import com.pluscontable.tributario.model.TipoNotificacion;
import com.pluscontable.tributario.service.NotificacionAtendidaEvento;
import com.pluscontable.tributario.sire.RecordatorioSireFechas;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-19 · Alertas escalonadas del plazo de respuesta de una notificación del Buzón SOL (HU-18). El día en que faltan
 * exactamente N días (N de la lista, supuesto 5, 3, 1 y 0 por confirmar con Beto) y la notificación sigue PENDIENTE, se crea
 * su alerta, una sola vez por escalón, como aviso interno del núcleo de HU-12. Una notificación ATENDIDA no genera alertas
 * y las que ya tenía dejan de listarse. Solo dentro del sistema: sin correo ni WhatsApp (canal sin definir).
 */
@Service
public class AlertaPlazoService {

    /** «Hoy» es el de Lima, aunque el servidor corra en UTC (igual que HU-31). */
    static final ZoneId ZONA = ZoneId.of("America/Lima");
    static final String ORIGEN = "HU-19";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AlertaPlazoRepositorio repositorio;
    private final AvisoInternoService avisos;
    private final List<Integer> diasAlerta;
    private final Clock clock;

    @Autowired
    public AlertaPlazoService(AlertaPlazoRepositorio repositorio, AvisoInternoService avisos,
                              @Value("${app.tributario.alertas-carta-dias:5,3,1,0}") List<Integer> diasAlerta) {
        this(repositorio, avisos, diasAlerta, Clock.system(ZONA));
    }

    AlertaPlazoService(AlertaPlazoRepositorio repositorio, AvisoInternoService avisos, List<Integer> diasAlerta, Clock clock) {
        if (diasAlerta == null || diasAlerta.isEmpty() || diasAlerta.stream().anyMatch(d -> d == null || d < 0)
                || new HashSet<>(diasAlerta).size() != diasAlerta.size()) {
            throw new IllegalStateException("app.tributario.alertas-carta-dias debe tener días enteros, sin repetir y no negativos");
        }
        this.repositorio = repositorio;
        this.avisos = avisos;
        this.diasAlerta = List.copyOf(diasAlerta);
        this.clock = clock;
    }

    Clock reloj() {
        return clock;
    }

    /** Las alertas de hoy (hoy de Lima). */
    @Transactional
    public int generarDeHoy() {
        return generar(LocalDate.now(clock));
    }

    /** Crea las alertas que corresponden en {@code hoy}; devuelve cuántas nuevas. Repetirlo el mismo día no duplica. */
    @Transactional
    public int generar(LocalDate hoy) {
        int creadas = 0;
        for (Candidata c : repositorio.pendientesConPlazoEntre(hoy, hoy.plusDays(Collections.max(diasAlerta)))) {
            if (!RecordatorioSireFechas.corresponde(c.plazo(), hoy, diasAlerta)) continue;
            int faltan = (int) ChronoUnit.DAYS.between(hoy, c.plazo());
            AvisoInternoDto aviso = avisos.crear(ORIGEN, "notificacion:" + c.notificacionId() + ":dias:" + faltan,
                    mensaje(c, faltan), List.of(c.clienteId()));
            if (repositorio.registrar(c.notificacionId(), faltan, hoy, aviso.id())) creadas++;
        }
        return creadas;
    }

    /**
     * Al atender una notificación, sus avisos salen de la bandeja no leída (el núcleo de HU-12 solo sabe «leído»): se marcan leídos
     * en la misma transacción. Repetirlo no cambia nada.
     */
    @EventListener
    public void alAtender(NotificacionAtendidaEvento evento) {
        for (Long aviso : repositorio.avisosDe(evento.notificacionId())) avisos.marcarLeido(aviso);
    }

    /** Las alertas de notificaciones aún pendientes, con el cliente y los días restantes desde hoy (Lima). */
    @Transactional(readOnly = true)
    public List<AlertaPlazoDto> listar() {
        return repositorio.visibles(LocalDate.now(clock));
    }

    static String mensaje(Candidata c, int faltan) {
        String cuanto = faltan == 0 ? "vence hoy" : faltan == 1 ? "falta 1 día" : "faltan " + faltan + " días";
        return "Plazo de respuesta de " + nombre(c.tipo()) + " de " + c.razonSocial() + ": " + cuanto + " (vence el "
                + c.plazo().format(FECHA) + ")";
    }

    private static String nombre(TipoNotificacion tipo) {
        return switch (tipo) {
            case ESQUELA -> "la esquela";
            case CARTA_INDUCTIVA -> "la carta inductiva";
            case COMUNICADO -> "el comunicado";
        };
    }
}
