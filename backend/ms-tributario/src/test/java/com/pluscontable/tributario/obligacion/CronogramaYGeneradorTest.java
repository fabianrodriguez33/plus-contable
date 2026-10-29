package com.pluscontable.tributario.obligacion;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/** Pruebas ejecutables con Java estándar mientras Maven requiere Java 21. */
public final class CronogramaYGeneradorTest {
    private static int casos;

    public static void main(String[] args) throws IOException {
        String cabecera = "periodo_tributario,ultimo_digito_ruc,fecha_vencimiento\n";
        List<CronogramaTributario> filas = CronogramaCsvReader.leer(new StringReader(
                cabecera + "2026-01,0,2026-02-16\n2026-01,2-3,2026-02-18\n"));
        comprobar(filas.size() == 2);
        comprobar(filas.get(0).periodoTributario().equals(YearMonth.of(2026, 1)));
        comprobar(filas.get(0).fechaVencimiento().equals(LocalDate.of(2026, 2, 16)));
        rechazar(() -> leer(cabecera + "2026-01,0,2026-02-16\n2026-01,0,2026-02-17\n"));
        rechazar(() -> leer(cabecera + "2026-01,OTRO,2026-02-16\n"));
        rechazar(() -> leer(cabecera + "2026-01,0,2026-02-30\n"));
        rechazar(() -> leer("otra_cabecera\n2026-01,0,2026-02-16\n"));
        rechazar(() -> leer(cabecera + "2026-01,0\n"));

        GeneradorObligaciones.Clave clave = new GeneradorObligaciones.Clave(
                1L, TipoObligacion.IGV, LocalDate.of(2026, 1, 1));
        GeneradorObligaciones.Clave otra = new GeneradorObligaciones.Clave(
                1L, TipoObligacion.RENTA, LocalDate.of(2026, 1, 1));
        LocalDate fecha = filas.get(0).fechaVencimiento(); // selección explícita de ejemplo
        var primera = new GeneradorObligaciones.Solicitud(clave, fecha);
        var segunda = new GeneradorObligaciones.Solicitud(otra, fecha);
        comprobar(GeneradorObligaciones.preparar(List.of(primera, primera, segunda), Set.of()).size() == 2);
        comprobar(GeneradorObligaciones.preparar(List.of(primera, segunda), Set.of(clave)).equals(List.of(segunda)));
        rechazar(() -> new GeneradorObligaciones.Clave(0L, TipoObligacion.IGV, LocalDate.of(2026, 1, 1)));
        rechazar(() -> new GeneradorObligaciones.Solicitud(clave, null));
        rechazar(() -> GeneradorObligaciones.preparar(null, Set.of()));

        if (args.length == 1) {
            try (var reader = Files.newBufferedReader(Path.of(args[0]))) {
                List<CronogramaTributario> completo = CronogramaCsvReader.leer(reader);
                comprobar(completo.size() == 84);
                comprobar(completo.stream().map(CronogramaTributario::periodoTributario).distinct().count() == 12);
            }
        }
        System.out.println("HU-07: " + casos + " comprobaciones de cronograma y generación aprobadas");
    }

    private static void leer(String csv) {
        try {
            CronogramaCsvReader.leer(new StringReader(csv));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void comprobar(boolean condicion) {
        if (!condicion) throw new AssertionError("Comprobación fallida");
        casos++;
    }

    private static void rechazar(Runnable accion) {
        try {
            accion.run();
        } catch (IllegalArgumentException esperada) {
            casos++;
            return;
        }
        throw new AssertionError("Se esperaba IllegalArgumentException");
    }
}
