package com.pluscontable.tributario.obligacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

class CronogramaSunatLoaderTest {
    @Test
    void csvVersionadoTieneOchentaYCuatroFilas() throws Exception {
        try (var reader = new InputStreamReader(new ClassPathResource("cronograma_sunat_2026.csv")
                .getInputStream(), StandardCharsets.UTF_8)) {
            var filas = CronogramaCsvReader.leer(reader);
            assertThat(filas).hasSize(84);
            assertThat(filas.stream().map(CronogramaTributario::periodoTributario).distinct().count()).isEqualTo(12);
        }
    }

    @Test
    void preparaCargaIdempotenteDeOchentaYCuatroFilas() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new CronogramaSunatLoader(jdbc).run(new DefaultApplicationArguments());
        ArgumentCaptor<BatchPreparedStatementSetter> captor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbc).batchUpdate(anyString(), captor.capture());
        assertThat(captor.getValue().getBatchSize()).isEqualTo(84);
    }
}
