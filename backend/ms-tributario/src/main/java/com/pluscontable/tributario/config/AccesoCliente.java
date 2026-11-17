package com.pluscontable.tributario.config;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * HU-38 (tarea 94): un CLIENTE_MYPE solo ve datos de su empresa. El estudio (CONTADOR, ADMIN) ve todas.
 * Se cierra por defecto: una cuenta de cliente sin claim clienteId no ve ningún cliente. Un AccessDeniedException
 * responde 403, igual que el resto de la matriz.
 */
public final class AccesoCliente {

    private AccesoCliente() {
    }

    public static void exigir(Long clienteIdPedido) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_CLIENTE_MYPE"))) {
            return; // el estudio, o sin sesion (la seguridad de rutas ya respondio 401)
        }
        Long propio = auth.getDetails() instanceof DatosToken d ? d.clienteId() : null;
        if (propio == null || !propio.equals(clienteIdPedido)) {
            throw new AccessDeniedException("Un cliente solo accede a los datos de su empresa");
        }
    }
}
