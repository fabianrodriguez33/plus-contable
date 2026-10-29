package com.pluscontable.tributario.obligacion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lee el formato simple publicado por Julio; no asigna cronogramas a tipos. */
public final class CronogramaCsvReader {
    private static final String CABECERA = "periodo_tributario,ultimo_digito_ruc,fecha_vencimiento";
    private static final Set<String> GRUPOS = Set.of("0", "1", "2-3", "4-5", "6-7", "8-9",
            "BUEN_CONTRIB_UESP");

    private CronogramaCsvReader() {
    }

    public static List<CronogramaTributario> leer(Reader origen) throws IOException {
        if (origen == null) throw new IllegalArgumentException("origen CSV es obligatorio");
        BufferedReader reader = new BufferedReader(origen);
        if (!CABECERA.equals(reader.readLine())) {
            throw new IllegalArgumentException("cabecera de cronograma no reconocida");
        }
        List<CronogramaTributario> filas = new ArrayList<>();
        Set<String> claves = new HashSet<>();
        String linea;
        int numeroLinea = 1;
        while ((linea = reader.readLine()) != null) {
            numeroLinea++;
            String[] columnas = linea.split(",", -1);
            if (columnas.length != 3 || !GRUPOS.contains(columnas[1])) {
                throw new IllegalArgumentException("fila de cronograma inválida: " + numeroLinea);
            }
            try {
                CronogramaTributario fila = new CronogramaTributario(
                        YearMonth.parse(columnas[0]), columnas[1], LocalDate.parse(columnas[2]));
                if (!claves.add(fila.periodoTributario() + "/" + fila.grupoRuc())) {
                    throw new IllegalArgumentException("periodo y grupo RUC duplicados: " + numeroLinea);
                }
                filas.add(fila);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("fecha o periodo inválido en fila " + numeroLinea, e);
            }
        }
        return List.copyOf(filas);
    }
}
