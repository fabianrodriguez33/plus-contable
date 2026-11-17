package com.pluscontable.auth.repository;

import com.pluscontable.auth.model.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    /** HU-37: una empresa tiene a lo sumo una cuenta de acceso. */
    boolean existsByClienteId(Long clienteId);

    /** HU-37: la cuenta de acceso de una empresa (a lo sumo una). */
    Optional<Usuario> findByClienteId(Long clienteId);

    /** HU-37: versión de sesión vigente de la cuenta; vacío si el usuario ya no existe. */
    @Query("select u.sesionVersion from Usuario u where u.username = :username")
    Optional<Integer> sesionVersionDe(@Param("username") String username);
}
