package com.pluscontable.tributario.recordatoriopago;

import com.pluscontable.tributario.model.Obligacion;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-15: el contador programa un recordatorio de pago para una fecha y, ese día, el cliente lo recibe como un aviso de pago en su
 * portal (la tabla y la pantalla de HU-33) con el saldo pendiente. Se programa sobre una obligación DECLARADA o con PAGO_PARCIAL
 * (decisión de Beto, 18/11). Reutiliza {@link ReglaRecordatorioPago}; no calcula montos ni reglas fiscales.
 */
@Service
public class RecordatorioPagoService {

    /** «Hoy» es el de Lima, aunque el servidor corra en UTC. */
    static final ZoneId ZONA = ZoneId.of("America/Lima");

    public record Resultado(int enviados, int omitidos) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public RecordatorioPagoService(JdbcTemplate jdbc) {
        this(jdbc, Clock.system(ZONA));
    }

    RecordatorioPagoService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private record Obligacion_(Obligacion.Estado estado, LocalDate vence, BigDecimal monto, boolean clienteActivo, BigDecimal pagado) {
    }

    private record Pendiente(Long id, Long obligacionId, Long clienteId, String tributo, LocalDate fecha, Obligacion.Estado estado,
                             LocalDate vence, BigDecimal saldo, boolean clienteActivo) {
    }

    /** 404 si la obligación no existe; 400 con todos los errores juntos si no se puede programar. */
    @Transactional
    public RecordatorioPagoDto programar(Long obligacionId, LocalDate fecha, String usuario) {
        Obligacion_ o = buscar(obligacionId);
        LocalDate hoy = LocalDate.now(clock);
        List<String> errores = new ArrayList<>();
        if (o.estado() == Obligacion.Estado.PENDIENTE) errores.add("La obligación todavía no está declarada");
        else if (o.estado() == Obligacion.Estado.PAGADO) errores.add("La obligación ya está pagada");
        if (o.monto() == null || o.monto().signum() <= 0) errores.add("La obligación no tiene monto determinado");
        else if (o.monto().subtract(o.pagado()).signum() <= 0 && o.estado() != Obligacion.Estado.PAGADO) errores.add("La obligación no tiene saldo pendiente");
        if (!o.clienteActivo()) errores.add("El cliente está inactivo");
        if (fecha == null) errores.add("fechaRecordatorio es obligatoria");
        else {
            if (fecha.isBefore(hoy)) errores.add("fechaRecordatorio no puede ser una fecha pasada");
            if (fecha.isAfter(o.vence())) errores.add("fechaRecordatorio no puede ser posterior a la fecha de vencimiento");
            Integer repetido = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM tributario_schema.recordatorios_pago
                    WHERE obligacion_id = ? AND fecha_recordatorio = ? AND estado = 'PROGRAMADO'""", Integer.class, obligacionId, fecha);
            if (repetido != null && repetido > 0) errores.add("Ya hay un recordatorio programado para esa fecha");
        }
        if (!errores.isEmpty()) throw new ValidationException(String.join("; ", errores));
        if (ReglaRecordatorioPago.evaluar(o.estado(), o.vence(), fecha, hoy) == ReglaRecordatorioPago.Resultado.NO_ELEGIBLE) {
            throw new ValidationException("La obligación no es elegible para un recordatorio en esa fecha");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO tributario_schema.recordatorios_pago(obligacion_id, fecha_recordatorio, programado_por)
                VALUES (?, ?, ?) RETURNING id""", Long.class, obligacionId, fecha, usuario == null || usuario.isBlank() ? "desconocido" : usuario);
        return porId(id);
    }

    @Transactional(readOnly = true)
    public List<RecordatorioPagoDto> listar(Long obligacionId) {
        buscar(obligacionId);
        return jdbc.query(SELECT + " WHERE r.obligacion_id = ? ORDER BY r.fecha_recordatorio, r.id", this::fila, obligacionId);
    }

    /** Solo se cancela uno todavía programado; uno ya enviado, omitido o cancelado no cambia. */
    @Transactional
    public RecordatorioPagoDto cancelar(Long obligacionId, Long id) {
        RecordatorioPagoDto actual = jdbc.query(SELECT + " WHERE r.id = ? AND r.obligacion_id = ?", this::fila, id, obligacionId)
                .stream().findFirst().orElseThrow(() -> new EntityNotFoundException("Recordatorio no encontrado"));
        if (!"PROGRAMADO".equals(actual.estado())) throw new ValidationException("Solo se cancela un recordatorio programado");
        jdbc.update("UPDATE tributario_schema.recordatorios_pago SET estado = 'CANCELADO', resuelto_en = CURRENT_TIMESTAMP WHERE id = ?", id);
        return porId(id);
    }

    /**
     * Entrega los recordatorios cuya fecha es hoy o ya pasó (si el sistema estuvo caído se envían al volver; decisión de Beto, 18/11) y
     * siguen siendo útiles <b>mientras la obligación siga pendiente de pago, aunque ya haya vencido</b>: se envía con el saldo de hoy.
     * Si la obligación se pagó por completo, los recordatorios que le quedaban programados se <b>cancelan</b> (también los de fechas
     * futuras). Si el cliente quedó inactivo o la obligación ya no tiene saldo, no se envía y queda OMITIDO. Si una obligación tiene
     * varios atrasados se envía <b>uno solo</b> (el de la fecha más reciente) y los demás quedan OMITIDO. Si el cliente ya recibió hoy el
     * aviso automático de HU-33 por esa obligación, no se duplica y el recordatorio queda ENVIADO. Repetirlo no cambia nada.
     */
    @Transactional
    public Resultado entregar(LocalDate hoy) {
        jdbc.update("""
                UPDATE tributario_schema.recordatorios_pago r SET estado = 'CANCELADO', resuelto_en = CURRENT_TIMESTAMP
                FROM tributario_schema.obligaciones o
                WHERE o.id = r.obligacion_id AND r.estado = 'PROGRAMADO' AND o.estado = 'PAGADO'""");
        List<Pendiente> pendientes = jdbc.query("""
                SELECT r.id, r.obligacion_id, o.cliente_id, o.tipo_obligacion, r.fecha_recordatorio, o.estado, o.fecha_vencimiento,
                       o.monto_determinado - COALESCE((SELECT SUM(p.monto_pagado) FROM tributario_schema.pagos_obligacion p
                                                       WHERE p.obligacion_id = o.id), 0) AS saldo,
                       c.activo
                FROM tributario_schema.recordatorios_pago r
                JOIN tributario_schema.obligaciones o ON o.id = r.obligacion_id
                JOIN tributario_schema.clientes c ON c.id = o.cliente_id
                WHERE r.estado = 'PROGRAMADO' AND r.fecha_recordatorio <= ?
                ORDER BY r.obligacion_id, r.fecha_recordatorio DESC, r.id DESC
                FOR UPDATE OF r
                """, (rs, n) -> new Pendiente(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                rs.getObject(5, LocalDate.class), Obligacion.Estado.valueOf(rs.getString(6)), rs.getObject(7, LocalDate.class),
                rs.getBigDecimal(8), rs.getBoolean(9)), hoy);
        int enviados = 0;
        int omitidos = 0;
        Long obligacionAtendida = null; // el primero de cada obligación es el de la fecha más reciente
        for (Pendiente p : pendientes) {
            boolean esElMasReciente = !p.obligacionId().equals(obligacionAtendida);
            obligacionAtendida = p.obligacionId();
            boolean corresponde = esElMasReciente && p.clienteActivo() && p.saldo() != null && p.saldo().signum() > 0
                    && ReglaRecordatorioPago.evaluar(p.estado(), p.vence(), p.fecha(), hoy)
                    == ReglaRecordatorioPago.Resultado.CORRESPONDE_ENVIAR;
            if (corresponde) {
                jdbc.update("""
                        INSERT INTO tributario_schema.avisos_cliente(cliente_id, obligacion_id, tributo, monto, fecha_vencimiento, fecha_aviso)
                        VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (obligacion_id, fecha_aviso) DO NOTHING
                        """, p.clienteId(), p.obligacionId(), p.tributo() == null ? "OBLIGACION" : p.tributo(), p.saldo(), p.vence(), hoy);
                enviados++;
            } else {
                omitidos++;
            }
            jdbc.update("UPDATE tributario_schema.recordatorios_pago SET estado = ?, resuelto_en = CURRENT_TIMESTAMP WHERE id = ?",
                    corresponde ? "ENVIADO" : "OMITIDO", p.id());
        }
        return new Resultado(enviados, omitidos);
    }

    private static final String SELECT = """
            SELECT r.id, r.obligacion_id, r.fecha_recordatorio, r.estado, r.programado_por, r.programado_en, r.resuelto_en,
                   o.monto_determinado - COALESCE((SELECT SUM(p.monto_pagado) FROM tributario_schema.pagos_obligacion p
                                                   WHERE p.obligacion_id = o.id), 0) AS saldo
            FROM tributario_schema.recordatorios_pago r JOIN tributario_schema.obligaciones o ON o.id = r.obligacion_id""";

    private RecordatorioPagoDto fila(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new RecordatorioPagoDto(rs.getLong(1), rs.getLong(2), rs.getObject(3, LocalDate.class), rs.getString(4), rs.getString(5),
                rs.getTimestamp(6).toInstant(), rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant(), rs.getBigDecimal(8));
    }

    private RecordatorioPagoDto porId(Long id) {
        return jdbc.queryForObject(SELECT + " WHERE r.id = ?", this::fila, id);
    }

    private Obligacion_ buscar(Long obligacionId) {
        return jdbc.query("""
                SELECT o.estado, o.fecha_vencimiento, o.monto_determinado, c.activo,
                       COALESCE((SELECT SUM(p.monto_pagado) FROM tributario_schema.pagos_obligacion p WHERE p.obligacion_id = o.id), 0)
                FROM tributario_schema.obligaciones o JOIN tributario_schema.clientes c ON c.id = o.cliente_id
                WHERE o.id = ?""", (rs, n) -> new Obligacion_(Obligacion.Estado.valueOf(rs.getString(1)),
                rs.getObject(2, LocalDate.class), rs.getBigDecimal(3), rs.getBoolean(4), rs.getBigDecimal(5)), obligacionId)
                .stream().findFirst().orElseThrow(() -> new EntityNotFoundException("Obligación no encontrada"));
    }
}
