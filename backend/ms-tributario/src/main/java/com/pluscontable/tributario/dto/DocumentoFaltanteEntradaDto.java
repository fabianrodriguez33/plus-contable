package com.pluscontable.tributario.dto;

import java.time.LocalDate;

/** fechaSolicitud es opcional: si no se indica, es hoy. */
public record DocumentoFaltanteEntradaDto(String documento, LocalDate fechaSolicitud, LocalDate fechaLimite) {
}
