package com.pluscontable.auth.config;

import com.pluscontable.auth.model.Rol;
import com.pluscontable.auth.model.Usuario;
import com.pluscontable.auth.repository.RolRepository;
import com.pluscontable.auth.repository.UsuarioRepository;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crea el usuario administrador inicial si no existe. */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder encoder;
    private final String adminUser;
    private final String adminPass;

    public DataSeeder(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder encoder,
                      @Value("${app.seed.admin-username}") String adminUser,
                      @Value("${app.seed.admin-password}") String adminPass) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.encoder = encoder;
        this.adminUser = adminUser;
        this.adminPass = adminPass;
    }

    @Override
    public void run(String... args) {
        if (usuarios.existsByUsername(adminUser)) {
            return;
        }
        if (adminPass == null || adminPass.isBlank()) {
            throw new IllegalStateException("ADMIN_PASSWORD es obligatorio para crear el administrador inicial");
        }
        Rol admin = roles.findByNombre("ADMIN").orElseThrow();
        Usuario u = new Usuario();
        u.setUsername(adminUser);
        u.setPasswordHash(encoder.encode(adminPass));
        u.setNombreCompleto("Administrador V&F Asesores");
        u.setEmail("admin@vfasesores.pe");
        u.setRoles(new HashSet<>(Set.of(admin)));
        usuarios.save(u);
    }
}
