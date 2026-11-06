package com.pluscontable.auth.config;

import java.time.Instant;

/** HU-36 (tarea 2): datos del token que el filtro deja en la autenticacion para cerrar la sesion (jti puede ser null en tokens anteriores). */
public record SesionToken(String jti, Instant expira) {
}
