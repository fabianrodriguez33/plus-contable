package com.pluscontable.tributario.service;

/** HU-18: se publica, dentro de la misma transacción, cuando una notificación pasa a ATENDIDA. */
public record NotificacionAtendidaEvento(Long notificacionId) {
}
