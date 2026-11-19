package com.pluscontable.tributario.avisocliente;

import com.pluscontable.tributario.sire.RecordatorioSireFechas;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HU-33: genera el aviso previo al vencimiento del pago y lo entrega al cliente en su portal. */
@Service
public class AvisoClienteService {
    /** Supuesto pendiente de confirmar con Beto: los mismos días que SIRE (3, 1 y 0 días antes). */
    public static final List<Integer> DIAS_AVISO = RecordatorioSireFechas.DIAS_AVISO_INICIALES;

    private record Candidata(Long id, Long clienteId, String tributo, LocalDate vence, java.math.BigDecimal saldo) {
    }

    private final JdbcTemplate jdbc;

    public AvisoClienteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Crea los avisos que corresponden hoy. Solo avisa si queda saldo por pagar: una obligación PAGADA, o sin monto
     * declarado, no genera aviso. Un pago parcial sigue avisando por el saldo. Repetirlo el mismo día no duplica.
     */
    @Transactional
    public int generar(LocalDate hoy) {
        int creados = 0;
        var candidatas = jdbc.query("""
                SELECT o.id, o.cliente_id, o.tipo_obligacion, o.fecha_vencimiento,
                       o.monto_determinado - COALESCE((SELECT SUM(p.monto_pagado)
                           FROM tributario_schema.pagos_obligacion p WHERE p.obligacion_id = o.id), 0) AS saldo
                FROM tributario_schema.obligaciones o
                JOIN tributario_schema.clientes c ON c.id = o.cliente_id
                WHERE o.estado IN ('DECLARADO', 'PAGO_PARCIAL') AND o.monto_determinado IS NOT NULL
                  AND c.activo AND o.fecha_vencimiento BETWEEN ? AND ?
                """, (rs, n) -> new Candidata(rs.getLong(1), rs.getLong(2), rs.getString(3),
                rs.getObject(4, LocalDate.class), rs.getBigDecimal(5)), hoy, hoy.plusDays(DIAS_AVISO.stream().max(Integer::compare).get()));
        for (Candidata f : candidatas) {
            if (f.saldo().signum() <= 0 || !RecordatorioSireFechas.corresponde(f.vence(), hoy, DIAS_AVISO)) continue;
            creados += jdbc.update("""
                    INSERT INTO tributario_schema.avisos_cliente(cliente_id, obligacion_id, tributo, monto, fecha_vencimiento, fecha_aviso)
                    VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (obligacion_id, fecha_aviso) DO NOTHING
                    """, f.clienteId(), f.id(), f.tributo() == null ? "OBLIGACION" : f.tributo(), f.saldo(), f.vence(), hoy);
        }
        return creados;
    }

    /** Los avisos del cliente, el más reciente primero. */
    @Transactional(readOnly = true)
    public List<AvisoClienteDto> listar(Long clienteId) {
        return jdbc.query("""
                SELECT id, obligacion_id, tributo, monto, fecha_vencimiento, fecha_aviso
                FROM tributario_schema.avisos_cliente WHERE cliente_id = ? ORDER BY fecha_aviso DESC, id DESC
                """, (rs, n) -> new AvisoClienteDto(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getBigDecimal(4),
                rs.getObject(5, LocalDate.class), rs.getObject(6, LocalDate.class)), clienteId);
    }
}
