package com.pluscontable.tributario.alertaplazo;

import com.pluscontable.tributario.model.TipoNotificacion;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** HU-19: las consultas de las alertas del plazo de respuesta. Solo lee notificaciones (HU-18); escribe en su propia tabla. */
@Repository
public class AlertaPlazoRepositorio {

    /** Una notificación PENDIENTE con su cliente, candidata a recibir alerta. */
    public record Candidata(Long notificacionId, Long clienteId, String razonSocial, TipoNotificacion tipo, LocalDate plazo) {
    }

    private final JdbcTemplate jdbc;

    public AlertaPlazoRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Las notificaciones PENDIENTES con plazo entre las dos fechas (incluidas), la de plazo más próximo primero. */
    public List<Candidata> pendientesConPlazoEntre(LocalDate desde, LocalDate hasta) {
        return jdbc.query("""
                SELECT n.id, n.cliente_id, c.razon_social, n.tipo, n.plazo_respuesta
                FROM tributario_schema.notificaciones_sol n
                JOIN tributario_schema.clientes c ON c.id = n.cliente_id
                WHERE n.estado = 'PENDIENTE' AND n.plazo_respuesta BETWEEN ? AND ?
                ORDER BY n.plazo_respuesta, n.id
                """, (rs, i) -> new Candidata(rs.getLong(1), rs.getLong(2), rs.getString(3), TipoNotificacion.valueOf(rs.getString(4)),
                rs.getObject(5, LocalDate.class)), desde, hasta);
    }

    /** Guarda la alerta; false si esa notificación ya tenía la alerta de esos días (no se repite). */
    public boolean registrar(Long notificacionId, int diasAntes, LocalDate fechaAlerta, Long avisoId) {
        return jdbc.update("""
                INSERT INTO tributario_schema.alertas_plazo_notificacion(notificacion_id, dias_antes, fecha_alerta, aviso_id)
                VALUES (?, ?, ?, ?) ON CONFLICT (notificacion_id, dias_antes) DO NOTHING
                """, notificacionId, diasAntes, fechaAlerta, avisoId) > 0;
    }

    /** Los avisos de la bandeja que creó cada alerta de esa notificación. */
    public List<Long> avisosDe(Long notificacionId) {
        return jdbc.queryForList("SELECT aviso_id FROM tributario_schema.alertas_plazo_notificacion WHERE notificacion_id = ? ORDER BY id",
                Long.class, notificacionId);
    }

    /** Las alertas de notificaciones que siguen PENDIENTES y cuyo plazo no pasó, con los días que faltan desde hoy. */
    public List<AlertaPlazoDto> visibles(LocalDate hoy) {
        return jdbc.query("""
                SELECT a.id, a.notificacion_id, n.cliente_id, c.ruc, c.razon_social, n.tipo, n.plazo_respuesta, a.dias_antes, a.fecha_alerta
                FROM tributario_schema.alertas_plazo_notificacion a
                JOIN tributario_schema.notificaciones_sol n ON n.id = a.notificacion_id
                JOIN tributario_schema.clientes c ON c.id = n.cliente_id
                WHERE n.estado = 'PENDIENTE' AND n.plazo_respuesta >= ?
                ORDER BY n.plazo_respuesta, a.fecha_alerta DESC, a.id DESC
                """, (rs, i) -> {
            LocalDate plazo = rs.getObject(7, LocalDate.class);
            return new AlertaPlazoDto(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5),
                    TipoNotificacion.valueOf(rs.getString(6)), plazo, rs.getInt(8), rs.getObject(9, LocalDate.class),
                    ChronoUnit.DAYS.between(hoy, plazo));
        }, hoy);
    }
}
