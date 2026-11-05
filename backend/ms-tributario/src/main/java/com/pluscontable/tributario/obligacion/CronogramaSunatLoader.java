package com.pluscontable.tributario.obligacion;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Carga idempotente del CSV versionado; no decide qué tipos usan sus fechas. */
@Component
public class CronogramaSunatLoader implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public CronogramaSunatLoader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var recurso = new ClassPathResource("cronograma_sunat_2026.csv");
        List<CronogramaTributario> filas;
        try (var reader = new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8)) {
            filas = CronogramaCsvReader.leer(reader);
        }
        if (filas.size() != 84 || filas.stream().map(CronogramaTributario::periodoTributario).distinct().count() != 12) {
            throw new IllegalStateException("El cronograma SUNAT 2026 debe tener 84 filas de 12 períodos");
        }
        jdbc.batchUpdate("""
                INSERT INTO tributario_schema.cronograma_tributario
                    (periodo_tributario, ultimo_digito_ruc, fecha_vencimiento)
                VALUES (?, ?, ?)
                ON CONFLICT (periodo_tributario, ultimo_digito_ruc) DO NOTHING
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                var fila = filas.get(i);
                ps.setDate(1, Date.valueOf(fila.periodoTributario().atDay(1)));
                ps.setString(2, fila.grupoRuc());
                ps.setDate(3, Date.valueOf(fila.fechaVencimiento()));
            }

            @Override
            public int getBatchSize() {
                return filas.size();
            }
        });
    }
}
