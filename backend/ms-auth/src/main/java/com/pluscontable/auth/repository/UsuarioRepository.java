package com.pluscontable.auth.repository;

import com.pluscontable.auth.model.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    /** HU-37: una empresa tiene a lo sumo una cuenta de acceso. */
    boolean existsByClienteId(Long clienteId);
}
