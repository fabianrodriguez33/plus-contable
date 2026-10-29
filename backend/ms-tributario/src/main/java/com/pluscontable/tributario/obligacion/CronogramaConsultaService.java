package com.pluscontable.tributario.obligacion;

import com.pluscontable.tributario.repository.ClienteRepository;
import jakarta.persistence.EntityNotFoundException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Busca la fecha por periodo y RUC; no decide qué obligaciones usan el cronograma. */
@Service
public class CronogramaConsultaService {
    private final ClienteRepository clientes;
    private final JdbcTemplate jdbc;

    public CronogramaConsultaService(ClienteRepository clientes, JdbcTemplate jdbc) {
        this.clientes = clientes;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Optional<LocalDate> buscar(Long clienteId, YearMonth periodo) {
        if (clienteId == null || clienteId <= 0 || periodo == null) {
            throw new IllegalArgumentException("cliente_id y periodo son obligatorios");
        }
        String ruc = clientes.findById(clienteId)
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado"))
                .getRuc();
        String grupo = GrupoRucCronograma.paraRuc(ruc);
        List<LocalDate> fechas = jdbc.query("""
                SELECT fecha_vencimiento FROM tributario_schema.cronograma_tributario
                WHERE periodo_tributario = ? AND ultimo_digito_ruc = ?
                """, (rs, fila) -> rs.getDate(1).toLocalDate(), Date.valueOf(periodo.atDay(1)), grupo);
        if (fechas.size() > 1) {
            throw new IllegalStateException("Cronograma duplicado para periodo y grupo RUC");
        }
        return fechas.stream().findFirst();
    }
}
