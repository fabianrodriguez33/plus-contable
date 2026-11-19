package com.pluscontable.tributario.vencimiento;

import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-16 · lista de solo lectura de obligaciones VENCIDAS SIN PAGO de clientes activos. Lee las constancias de
 * {@code pagos_obligacion} (HU-13); no toca los honorarios (HU-29) ni guarda ningún estado nuevo.
 */
@Service
public class ObligacionesVencidasSinPagoService {

    /** «Hoy» es el de Lima, aunque el servidor corra en UTC. */
    static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public ObligacionesVencidasSinPagoService(JdbcTemplate jdbc) {
        this(jdbc, Clock.system(ZONA));
    }

    ObligacionesVencidasSinPagoService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Las más atrasadas primero. {@code clienteId} nulo = toda la cartera activa. */
    @Transactional(readOnly = true)
    public List<ObligacionVencidaSinPagoDto> listar(Long clienteId) {
        LocalDate hoy = LocalDate.now(clock);
        // El SQL descarta lo evidente; qué es «vencida sin pago» lo decide la regla pura.
        return jdbc.query("""
                SELECT o.id, o.cliente_id, c.ruc, c.razon_social, o.tipo_obligacion, o.periodo, o.fecha_vencimiento,
                       o.estado, o.monto_determinado,
                       EXISTS (SELECT 1 FROM tributario_schema.pagos_obligacion p WHERE p.obligacion_id = o.id) AS tiene_constancia
                FROM tributario_schema.obligaciones o
                JOIN tributario_schema.clientes c ON c.id = o.cliente_id
                WHERE c.activo = TRUE AND o.fecha_vencimiento < ? AND o.estado <> 'PAGADO'
                  AND (?::BIGINT IS NULL OR o.cliente_id = ?)
                ORDER BY o.fecha_vencimiento, o.id
                """, (rs, n) -> new Object[]{rs.getLong("id"), rs.getLong("cliente_id"), rs.getString("ruc"),
                rs.getString("razon_social"), rs.getString("tipo_obligacion"), rs.getDate("periodo").toLocalDate(),
                rs.getDate("fecha_vencimiento").toLocalDate(), Obligacion.Estado.valueOf(rs.getString("estado")),
                rs.getBigDecimal("monto_determinado"), rs.getBoolean("tiene_constancia")},
                java.sql.Date.valueOf(hoy), clienteId, clienteId).stream()
                .filter(f -> ReglaVencidaSinPago.corresponde((Obligacion.Estado) f[7], (LocalDate) f[6], hoy, (Boolean) f[9]))
                .map(f -> new ObligacionVencidaSinPagoDto((Long) f[0], (Long) f[1], (String) f[2], (String) f[3],
                        f[4] == null ? null : TipoObligacion.valueOf((String) f[4]), (LocalDate) f[5], (LocalDate) f[6],
                        ReglaVencidaSinPago.diasVencida((LocalDate) f[6], hoy), (Obligacion.Estado) f[7],
                        (java.math.BigDecimal) f[8]))
                .toList();
    }
}
