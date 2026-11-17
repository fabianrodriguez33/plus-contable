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

    // Los ids por encima de 127 no se comparten entre cajas de Long: aquí el id del token y el de la ruta son objetos distintos

    @Test
    void elClienteConIdMayorA127AccedeASuPropiaEmpresa() {
        entrarComo("CLIENTE_MYPE", 1000L);
        assertThatCode(() -> AccesoCliente.exigir(1000L)).doesNotThrowAnyException();
    }

    @Test
    void elClienteConIdMayorA127NoAccedeALaEmpresaVecina() {
        entrarComo("CLIENTE_MYPE", 1000L);
        assertThatThrownBy(() -> AccesoCliente.exigir(1001L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> AccesoCliente.exigir(999L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void elIdSeCompararPorSuValorCompletoNoPorSusBitsBajos() {
        entrarComo("CLIENTE_MYPE", 1000L);
        assertThatThrownBy(() -> AccesoCliente.exigir(1000L + (1L << 32))).isInstanceOf(AccessDeniedException.class);
    }
}
