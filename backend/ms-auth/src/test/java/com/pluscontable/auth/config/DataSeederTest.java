package com.pluscontable.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.auth.model.Rol;
import com.pluscontable.auth.model.Usuario;
import com.pluscontable.auth.repository.RolRepository;
import com.pluscontable.auth.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** HT-01: el administrador inicial sale de variables de entorno; sin contraseña no se crea ninguno. */
class DataSeederTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RolRepository roles = mock(RolRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private DataSeeder seeder(String clave) {
        return new DataSeeder(usuarios, roles, encoder, "admin", clave);
    }

    @Test
    void creaElAdministradorConLaContrasenaConfiguradaYGuardadaCifrada() {
        Rol admin = new Rol();
        admin.setNombre("ADMIN");
        when(usuarios.existsByUsername("admin")).thenReturn(false);
        when(roles.findByNombre("ADMIN")).thenReturn(Optional.of(admin));

        seeder("clave-del-entorno").run();

        ArgumentCaptor<Usuario> c = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(c.capture());
        assertThat(c.getValue().getUsername()).isEqualTo("admin");
        assertThat(c.getValue().getPasswordHash()).isNotEqualTo("clave-del-entorno");
        assertThat(encoder.matches("clave-del-entorno", c.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void siElAdministradorYaExisteNoHaceNadaNiExigeContrasena() {
        when(usuarios.existsByUsername("admin")).thenReturn(true);

        seeder("").run();

        verify(usuarios, never()).save(any());
    }

    @Test
    void sinContrasenaConfiguradaNoCreaAdministradorYFallaConUnMensajeClaro() {
        when(usuarios.existsByUsername("admin")).thenReturn(false);

        assertThatThrownBy(() -> seeder(" ").run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
        verify(usuarios, never()).save(any());
    }
}
