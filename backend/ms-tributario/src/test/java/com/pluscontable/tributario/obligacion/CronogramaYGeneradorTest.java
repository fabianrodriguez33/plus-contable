package com.pluscontable.tributario.obligacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CronogramaYGeneradorTest {
    private static final String CABECERA = "periodo_tributario,ultimo_digito_ruc,fecha_vencimiento\n";

    @Test
    void leePeriodoGrupoYFecha() throws IOException {
        List<CronogramaTributario> filas = CronogramaCsvReader.leer(new StringReader(
                CABECERA + "2026-01,0,2026-02-16\n2026-01,2-3,2026-02-18\n"));
        assertEquals(2, filas.size());
        assertEquals(YearMonth.of(2026, 1), filas.get(0).periodoTributario());
        assertEquals(LocalDate.of(2026, 2, 16), filas.get(0).fechaVencimiento());
    }

    @Test
    void rechazaCsvInvalidoODuplicado() {
        assertThrows(IllegalArgumentException.class, () -> leer(CABECERA
                + "2026-01,0,2026-02-16\n2026-01,0,2026-02-17\n"));
        assertThrows(IllegalArgumentException.class, () -> leer(CABECERA + "2026-01,OTRO,2026-02-16\n"));
        assertThrows(IllegalArgumentException.class, () -> leer(CABECERA + "2026-01,0,2026-02-30\n"));
        assertThrows(IllegalArgumentException.class, () -> leer("otra_cabecera\n2026-01,0,2026-02-16\n"));
        assertThrows(IllegalArgumentException.class, () -> leer(CABECERA + "2026-01,0\n"));
    }

    @Test
    void evitaDuplicadosEnLaGeneracion() {
        var clave = new GeneradorObligaciones.Clave(1L, TipoObligacion.IGV, LocalDate.of(2026, 1, 1));
        var otra = new GeneradorObligaciones.Clave(1L, TipoObligacion.RENTA, LocalDate.of(2026, 1, 1));
        var fecha = LocalDate.of(2026, 2, 16);
        var primera = new GeneradorObligaciones.Solicitud(clave, fecha);
        var segunda = new GeneradorObligaciones.Solicitud(otra, fecha);
        assertEquals(2, GeneradorObligaciones.preparar(List.of(primera, primera, segunda), Set.of()).size());
        assertEquals(List.of(segunda), GeneradorObligaciones.preparar(List.of(primera, segunda), Set.of(clave)));
    }

    @Test
    void rechazaDatosInvalidosDeGeneracion() {
        var clave = new GeneradorObligaciones.Clave(1L, TipoObligacion.IGV, LocalDate.of(2026, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new GeneradorObligaciones.Clave(0L, TipoObligacion.IGV, LocalDate.of(2026, 1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new GeneradorObligaciones.Solicitud(clave, null));
        assertThrows(IllegalArgumentException.class,
                () -> GeneradorObligaciones.preparar(null, Set.of()));
    }

    @Test
    void recursoCompletoTieneOchentaYCuatroFilasYDocePeriodos() throws IOException {
        var recurso = new ClassPathResource("cronograma_sunat_2026.csv");
        try (var reader = new java.io.InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8)) {
            var filas = CronogramaCsvReader.leer(reader);
            assertEquals(84, filas.size());
            assertEquals(12, filas.stream().map(CronogramaTributario::periodoTributario).distinct().count());
        }
    }

    private static void leer(String csv) throws IOException {
        CronogramaCsvReader.leer(new StringReader(csv));
    }
}
