package com.pluscontable.auth.service;

import com.pluscontable.auth.config.JwtProvider;
import com.pluscontable.auth.config.SesionToken;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
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
    private static final String MSG_SIN_CAMBIO_PENDIENTE = "No tiene un cambio de contraseña pendiente";
    static final int PASSWORD_MINIMO = 8;
    static final int PASSWORD_MAXIMO = 72;

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
        if (found.isEmpty()) found = buscarEscritoDistinto(request.username()); // un correo se puede escribir con mayúsculas
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
        if (u.isDebeCambiarPassword()) {
            // HU-37: con la contraseña temporal se obtiene un token restringido para cambiarla. Entrar no la gasta: si el cliente
            // se corta antes del cambio, vuelve a entrar con la misma; se gasta recién al cambiarla (ver cambiarPassword)
            usuarioRepository.save(u);
            log(u.getId(), u.getUsername(), "LOGIN_EXITOSO", "Primer ingreso con la contraseña temporal: debe cambiarla", ip);
            return new LoginResponse(jwtProvider.generateCambioPassword(u.getUsername()), "Bearer",
                    jwtProvider.getExpiration().toSeconds(), usuarioDto(u), true);
        }
        usuarioRepository.save(u);
        log(u.getId(), u.getUsername(), "LOGIN_EXITOSO", null, ip);

        List<String> roles = u.getRoles().stream().map(Rol::getNombre).sorted().toList();
        String token = jwtProvider.generate(u.getUsername(), roles, u.getClienteId());
        return new LoginResponse(token, "Bearer", jwtProvider.getExpiration().toSeconds(),
                new UserDto(u.getId(), u.getUsername(), u.getNombreCompleto(), roles));
    }

    private Optional<Usuario> buscarEscritoDistinto(String escrito) {
        String normal = escrito == null ? null : escrito.trim().toLowerCase(Locale.ROOT);
        return normal == null || normal.equals(escrito) ? Optional.empty() : usuarioRepository.findByUsername(normal);
    }

    private UserDto usuarioDto(Usuario u) {
        return new UserDto(u.getId(), u.getUsername(), u.getNombreCompleto(),
                u.getRoles().stream().map(Rol::getNombre).sorted().toList());
    }

    /**
     * HU-37: cambio obligatorio de la contraseña temporal en el primer ingreso. Solo lo permite el token restringido del primer
     * ingreso y mientras la cuenta tenga el cambio pendiente. Guarda solo el hash de la nueva, revoca el token restringido y
     * entrega el token normal de la cuenta. Nunca registra las contraseñas.
     */
    @Transactional
    public LoginResponse cambiarPassword(String username, SesionToken sesion, String passwordNueva, String ip) {
        if (sesion == null || !sesion.cambiarPassword()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MSG_SIN_CAMBIO_PENDIENTE);
        }
        Usuario u = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, MSG_INVALIDAS));
        if (!u.isDebeCambiarPassword()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MSG_SIN_CAMBIO_PENDIENTE);
        }
        validarPasswordNueva(u, passwordNueva);
        u.setPasswordHash(passwordEncoder.encode(passwordNueva));
        u.setDebeCambiarPassword(false);
        u.setTemporalUsadaEn(LocalDateTime.now()); // la temporal se gasta aquí; además, el hash nuevo ya no coincide con ella
        usuarioRepository.save(u);
        if (sesion.jti() != null && !revocados.existsById(sesion.jti())) {
            LocalDateTime expiraEn = LocalDateTime.ofInstant(sesion.expira() == null ? Instant.now() : sesion.expira(), ZoneId.systemDefault());
            revocados.save(new TokenRevocado(sesion.jti(), username, expiraEn));
        }
        log(u.getId(), u.getUsername(), "CAMBIO_PASSWORD", "Cambio obligatorio de la contraseña temporal", ip);
        List<String> roles = u.getRoles().stream().map(Rol::getNombre).sorted().toList();
        return new LoginResponse(jwtProvider.generate(u.getUsername(), roles, u.getClienteId()), "Bearer",
                jwtProvider.getExpiration().toSeconds(), usuarioDto(u), false);
    }

    private void validarPasswordNueva(Usuario u, String nueva) {
        if (nueva == null || nueva.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan o son inválidos: passwordNueva");
        }
        // BCrypt solo usa los primeros 72 bytes: se rechaza lo más largo en vez de truncarlo en silencio
        if (nueva.length() < PASSWORD_MINIMO || nueva.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña nueva debe tener de " + PASSWORD_MINIMO + " a " + PASSWORD_MAXIMO + " caracteres");
        }
        if (nueva.equalsIgnoreCase(u.getUsername())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña nueva no puede ser igual al correo");
        }
        if (passwordEncoder.matches(nueva, u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña nueva no puede ser la temporal");
        }
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
