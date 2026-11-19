package com.pluscontable.tributario.vencimiento;

import com.pluscontable.tributario.aviso.AvisoInternoService;
import com.pluscontable.tributario.model.Obligacion;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-11: une {@link ReglaVencimientoProximo} con la bandeja de avisos internos. Crea un aviso por cada obligación
 * PENDIENTE de un cliente activo que vence dentro de la ventana. Solo avisa al estudio; no calcula montos ni reglas fiscales.
 * Los días de anticipación los recibe quien lo llame: este servicio no fija ningún valor (pendiente de Beto vía Julio).
 * Todavía no hay job que lo ejecute (T59/T60) ni pantalla (T58).
 */
@Service
public class AvisoVencimientoProximoService {

    public static final String ORIGEN = "HU11_VENCIMIENTO";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record Resultado(int candidatas, int avisosCreados, int yaAvisadas) {
    }

    private record Fila(Long obligacionId, Long clienteId, String razonSocial, String ruc, String tipo,
                        LocalDate periodo, LocalDate fechaVencimiento, Obligacion.Estado estado) {
    }

    private final JdbcTemplate jdbc;
    private final AvisoInternoService avisos;

    public AvisoVencimientoProximoService(JdbcTemplate jdbc, AvisoInternoService avisos) {
        this.jdbc = jdbc;
        this.avisos = avisos;
    }

    /**
     * Idempotente: la clave es la obligación y su fecha de vencimiento, así que repetir la ejecución no duplica, y si la
     * fecha de vencimiento cambia (HU-10) se avisa de nuevo con la fecha nueva. Es todo o nada: si falla un aviso no se
     * guarda ninguno, y la siguiente ejecución lo reintenta completo.
     */
    @Transactional
    public Resultado generar(LocalDate hoy, int diasAnticipacion) {
        Objects.requireNonNull(hoy, "hoy");
        if (diasAnticipacion < 0) throw new IllegalArgumentException("diasAnticipacion no puede ser negativo");

        // El SQL solo descarta lo evidente; la decisión de qué es «próximo» es de la regla pura.
        List<Fila> filas = jdbc.query("""
                SELECT o.id, o.cliente_id, c.razon_social, c.ruc, o.tipo_obligacion, o.periodo, o.fecha_vencimiento, o.estado
                FROM tributario_schema.obligaciones o
                JOIN tributario_schema.clientes c ON c.id = o.cliente_id
                WHERE c.activo = TRUE AND o.estado = 'PENDIENTE' AND o.fecha_vencimiento BETWEEN ? AND ?
                ORDER BY o.fecha_vencimiento, o.id
                """, (rs, n) -> new Fila(rs.getLong("id"), rs.getLong("cliente_id"), rs.getString("razon_social"),
                rs.getString("ruc"), rs.getString("tipo_obligacion"), rs.getDate("periodo").toLocalDate(),
                rs.getDate("fecha_vencimiento").toLocalDate(), Obligacion.Estado.valueOf(rs.getString("estado"))),
                java.sql.Date.valueOf(hoy), java.sql.Date.valueOf(hoy.plusDays(diasAnticipacion)));

        Map<Long, Fila> porId = filas.stream().collect(Collectors.toMap(Fila::obligacionId, f -> f));
        List<ReglaVencimientoProximo.Candidato> proximos = ReglaVencimientoProximo.seleccionar(
                filas.stream().map(f -> new ReglaVencimientoProximo.Candidato(f.obligacionId(), f.estado(), f.fechaVencimiento()))
                        .toList(), hoy, diasAnticipacion);

        int creados = 0;
        for (ReglaVencimientoProximo.Candidato candidato : proximos) {
            Fila f = porId.get(candidato.obligacionId());
            String clave = claveEvento(f.obligacionId(), f.fechaVencimiento());
            if (existe(clave)) continue;
            avisos.crear(ORIGEN, clave, mensaje(f), List.of(f.clienteId()));
            creados++;
        }
        return new Resultado(proximos.size(), creados, proximos.size() - creados);
    }

    static String claveEvento(Long obligacionId, LocalDate fechaVencimiento) {
        return "VENCIMIENTO:obligacion:" + obligacionId + ":" + fechaVencimiento;
    }

    private static String mensaje(Fila f) {
        String tipo = f.tipo() == null ? "Obligación" : "Obligación " + f.tipo();
        return tipo + " del periodo " + f.periodo().getYear() + "-" + String.format("%02d", f.periodo().getMonthValue())
                + " de " + f.razonSocial() + " (RUC " + f.ruc() + ") vence el " + FECHA.format(f.fechaVencimiento()) + ".";
    }

    /**
     * Se consulta antes de crear porque la clave solo identifica la obligación y la fecha: si el cliente cambió de razón
     * social entre dos ejecuciones, el mensaje distinto haría que AvisoInternoService rechazara el reintento.
     */
    private boolean existe(String clave) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tributario_schema.avisos WHERE origen = ? AND clave_evento = ?", Integer.class, ORIGEN, clave);
        return n != null && n > 0;
    }
}
