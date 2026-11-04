package com.pluscontable.tributario.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** HU-38 (tarea 94): el estudio ve todo; un CLIENTE_MYPE solo su empresa, y sin claim clienteId no ve nada. */
class AccesoClienteTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void entrarComo(String rol, Long clienteId) {
        var auth = new UsernamePasswordAuthenticationToken("usuario", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
        auth.setDetails(new DatosToken(clienteId));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void elEstudioAccedeACualquierCliente() {
        entrarComo("CONTADOR", null);
        assertThatCode(() -> AccesoCliente.exigir(2L)).doesNotThrowAnyException();
        entrarComo("ADMIN", null);
        assertThatCode(() -> AccesoCliente.exigir(2L)).doesNotThrowAnyException();
    }

    @Test
    void elClienteAccedeASuPropiaEmpresa() {
        entrarComo("CLIENTE_MYPE", 1L);
        assertThatCode(() -> AccesoCliente.exigir(1L)).doesNotThrowAnyException();
    }

    @Test
    void elClienteNoAccedeAOtraEmpresa() {
        entrarComo("CLIENTE_MYPE", 1L);
        assertThatThrownBy(() -> AccesoCliente.exigir(2L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void elClienteSinClaimNoAccedeANada() {
        entrarComo("CLIENTE_MYPE", null);
        assertThatThrownBy(() -> AccesoCliente.exigir(1L)).isInstanceOf(AccessDeniedException.class);

        var sinDetalles = new UsernamePasswordAuthenticationToken("usuario", null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE_MYPE")));
        SecurityContextHolder.getContext().setAuthentication(sinDetalles);
        assertThatThrownBy(() -> AccesoCliente.exigir(1L)).isInstanceOf(AccessDeniedException.class);
    }
}
