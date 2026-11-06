package com.pluscontable.auth.service;

import com.pluscontable.auth.config.JwtProvider;
import com.pluscontable.auth.dto.LoginRequest;
import com.pluscontable.auth.dto.LoginResponse;
import com.pluscontable.auth.dto.UserDto;
import com.pluscontable.auth.model.BitacoraSeguridad;
import com.pluscontable.auth.model.Rol;
import com.pluscontable.auth.model.Usuario;
import com.pluscontable.auth.model.TokenRevocado;
import com.pluscontable.auth.repository.BitacoraRepository;
import com.pluscontable.auth.repository.TokenRevocadoRepository;
import com.pluscontable.auth.repository.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final String MSG_INVALIDAS = "Usuario o contraseña incorrectos";

    private final UsuarioRepository usuarioRepository;
    private final BitacoraRepository bitacoraRepository;
    private final TokenRevocadoRepository revocados;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final int maxFailedAttempts;
    private final int lockMinutes;

    public AuthService(UsuarioRepository usuarioRepository, BitacoraRepository bitacoraRepository,
                       TokenRevocadoRepository revocados, PasswordEncoder passwordEncoder, JwtProvider jwtProvider,
                       @Value("${app.security.max-failed-attempts}") int maxFailedAttempts,
                       @Value("${app.security.lock-minutes}") int lockMinutes) {
        this.usuarioRepository = usuarioRepository;
        this.bitacoraRepository = bitacoraRepository;
        this.revocados = revocados;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockMinutes = lockMinutes;
    }

    /**
     * noRollbackFor: los intentos fallidos y la bitacora deben persistirse aunque se lance la excepcion HTTP.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse login(LoginRequest request, String ip) {
        Optional<Usuario> found = usuarioRepository.findByUsername(request.username());
        if (found.isEmpty()) {
            log(null, request.username(), "LOGIN_FALLIDO", "Usuario inexistente", ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, MSG_INVALIDAS);
        }
        Usuario u = found.get();
        LocalDateTime now = LocalDateTime.now();

        if (!u.isActivo()) {
            log(u.getId(), u.getUsername(), "LOGIN_RECHAZADO", "Usuario inactivo", ip);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario inactivo");
        }
        if (u.getBloqueadoHasta() != null && u.getBloqueadoHasta().isAfter(now)) {
            long min = Math.max(1, Duration.between(now, u.getBloqueadoHasta()).toMinutes() + 1);
            log(u.getId(), u.getUsername(), "LOGIN_BLOQUEADO", "Intento con cuenta bloqueada", ip);
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    "Cuenta bloqueada por intentos fallidos. Intente nuevamente en " + min + " minuto(s)");
        }
        // Bloqueo vencido: reinicia contador
        if (u.getBloqueadoHasta() != null) {
            u.setBloqueadoHasta(null);
            u.setIntentosFallidos(0);
        }

        if (!passwordEncoder.matches(request.password(), u.getPasswordHash())) {
            u.setIntentosFallidos(u.getIntentosFallidos() + 1);
            if (u.getIntentosFallidos() >= maxFailedAttempts) {
                u.setBloqueadoHasta(now.plusMinutes(lockMinutes));
                u.setIntentosFallidos(0);
                usuarioRepository.save(u);
                log(u.getId(), u.getUsername(), "CUENTA_BLOQUEADA",
                        "Bloqueo de " + lockMinutes + " min por " + maxFailedAttempts + " intentos fallidos", ip);
                throw new ResponseStatusException(HttpStatus.LOCKED,
                        "Cuenta bloqueada por " + lockMinutes + " minutos tras " + maxFailedAttempts + " intentos fallidos");
            }
            usuarioRepository.save(u);
            log(u.getId(), u.getUsername(), "LOGIN_FALLIDO",
                    "Contraseña incorrecta (intento " + u.getIntentosFallidos() + ")", ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, MSG_INVALIDAS);
        }

        u.setIntentosFallidos(0);
        u.setBloqueadoHasta(null);
        usuarioRepository.save(u);
        log(u.getId(), u.getUsername(), "LOGIN_EXITOSO", null, ip);

        List<String> roles = u.getRoles().stream().map(Rol::getNombre).sorted().toList();
        String token = jwtProvider.generate(u.getUsername(), roles, u.getClienteId());
        return new LoginResponse(token, "Bearer", jwtProvider.getExpiration().toSeconds(),
                new UserDto(u.getId(), u.getUsername(), u.getNombreCompleto(), roles));
    }

    /**
     * HU-36 (tarea 2): cierra la sesion revocando el jti del token. Repetirlo no falla ni duplica la fila.
     * Un token anterior al cambio (sin jti) no se puede revocar: se anota en la bitacora y vence solo.
     */
    @Transactional
    public void logout(String username, String jti, Instant expira, String ip) {
        // Los vencidos ya se rechazan por su exp; aqui solo se limpia la tabla
        revocados.deleteByExpiraEnBefore(LocalDateTime.now());
        Long usuarioId = usuarioRepository.findByUsername(username).map(Usuario::getId).orElse(null);
        if (jti == null || jti.isBlank()) {
            log(usuarioId, username, "LOGOUT", "Token sin jti: no se puede revocar, vence solo", ip);
            return;
        }
        if (!revocados.existsById(jti)) {
            LocalDateTime expiraEn = LocalDateTime.ofInstant(expira == null ? Instant.now() : expira, ZoneId.systemDefault());
            revocados.save(new TokenRevocado(jti, username, expiraEn));
        }
        log(usuarioId, username, "LOGOUT", null, ip);
    }

    @Transactional(readOnly = true)
    public UserDto me(String username) {
        Usuario u = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        return new UserDto(u.getId(), u.getUsername(), u.getNombreCompleto(),
                u.getRoles().stream().map(Rol::getNombre).sorted().toList());
    }

    private void log(Long usuarioId, String username, String evento, String detalle, String ip) {
        bitacoraRepository.save(new BitacoraSeguridad(usuarioId, username, evento, detalle, ip));
    }
}
