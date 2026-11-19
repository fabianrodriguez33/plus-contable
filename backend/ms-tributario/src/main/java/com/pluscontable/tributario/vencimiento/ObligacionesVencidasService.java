package com.pluscontable.tributario.vencimiento;

import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.TipoObligacion;
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
 * HU-16 · lista de solo lectura, de clientes activos, con la definición de Beto ({@link ReglaObligacionVencida}): las
 * <b>vencidas</b> (declaradas con saldo, con lo pagado y el saldo) y, aparte, las <b>sin declarar</b> con la fecha pasada
 * (pendientes de atención). Lee los pagos de HU-13; no toca los honorarios (HU-29) ni guarda ningún estado.
 */
@Service
public class ObligacionesVencidasService {

    /** «Hoy» es el de Lima, aunque el servidor corra en UTC. */
    static final ZoneId ZONA = ZoneId.of("America/Lima");

    public record Resultado(List<ObligacionVencidaDto> vencidas, List<ObligacionVencidaDto> sinDeclarar) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public ObligacionesVencidasService(JdbcTemplate jdbc) {
        this(jdbc, Clock.system(ZONA));
    }

    ObligacionesVencidasService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Las más atrasadas primero. {@code clienteId} nulo = toda la cartera activa. */
    @Transactional(readOnly = true)
    public Resultado listar(Long clienteId) {
        LocalDate hoy = LocalDate.now(clock);
        // El SQL descarta lo evidente; qué es «vencida» y qué es «sin declarar» lo decide la regla pura.
        List<ObligacionVencidaDto> todas = jdbc.query("""
                SELECT o.id, o.cliente_id, c.ruc, c.razon_social, o.tipo_obligacion, o.periodo, o.fecha_vencimiento, o.estado,
                       o.monto_determinado,
                       COALESCE((SELECT SUM(p.monto_pagado) FROM tributario_schema.pagos_obligacion p WHERE p.obligacion_id = o.id), 0) AS pagado
                FROM tributario_schema.obligaciones o
                JOIN tributario_schema.clientes c ON c.id = o.cliente_id
                WHERE c.activo = TRUE AND o.fecha_vencimiento < ? AND o.estado <> 'PAGADO'
                  AND (?::BIGINT IS NULL OR o.cliente_id = ?)
                ORDER BY o.fecha_vencimiento, o.id
                """, (rs, n) -> {
            BigDecimal monto = rs.getBigDecimal("monto_determinado");
            BigDecimal pagado = rs.getBigDecimal("pagado");
            LocalDate vence = rs.getDate("fecha_vencimiento").toLocalDate();
            String tipo = rs.getString("tipo_obligacion");
            return new ObligacionVencidaDto(rs.getLong("id"), rs.getLong("cliente_id"), rs.getString("ruc"), rs.getString("razon_social"),
                    tipo == null ? null : TipoObligacion.valueOf(tipo), rs.getDate("periodo").toLocalDate(), vence,
                    ReglaObligacionVencida.diasVencida(vence, hoy), Obligacion.Estado.valueOf(rs.getString("estado")), monto, pagado,
                    monto == null ? null : monto.subtract(pagado));
        }, java.sql.Date.valueOf(hoy), clienteId, clienteId);

        List<ObligacionVencidaDto> vencidas = new ArrayList<>();
        List<ObligacionVencidaDto> sinDeclarar = new ArrayList<>();
        for (ObligacionVencidaDto o : todas) {
            if (ReglaObligacionVencida.vencida(o.estado(), o.fechaVencimiento(), hoy)) vencidas.add(o);
            else if (ReglaObligacionVencida.sinDeclarar(o.estado(), o.fechaVencimiento(), hoy)) sinDeclarar.add(o);
        }
        return new Resultado(List.copyOf(vencidas), List.copyOf(sinDeclarar));
    }
}
