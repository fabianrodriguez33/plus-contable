package com.pluscontable.auth.service;

import com.pluscontable.auth.dto.CuentaClienteResponse;
import com.pluscontable.auth.model.BitacoraSeguridad;
import com.pluscontable.auth.model.Rol;
import com.pluscontable.auth.model.Usuario;
import com.pluscontable.auth.repository.BitacoraRepository;
import com.pluscontable.auth.repository.RolRepository;
import com.pluscontable.auth.repository.UsuarioRepository;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * HU-37 · Crear la cuenta de acceso de un cliente MYPE. Una sola cuenta por empresa; el cliente entra con su correo (que queda
 * como nombre de usuario) y una contraseña temporal aleatoria que se devuelve una única vez y se guarda solo como hash.
 */
@Service
public class CuentaClienteService {

    static final String ROL_CLIENTE = "CLIENTE_MYPE";
    /** Largo de usuarios.username: el correo se guarda también como nombre de usuario. */
    static final int CORREO_MAXIMO = 60;
    private static final Pattern CORREO = Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$");

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final BitacoraRepository bitacora;
    private final PasswordEncoder encoder;
    private final ClienteTributarioClient tributario;
    private final GeneradorPassword generador;

    public CuentaClienteService(UsuarioRepository usuarios, RolRepository roles, BitacoraRepository bitacora,
                                PasswordEncoder encoder, ClienteTributarioClient tributario, GeneradorPassword generador) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.bitacora = bitacora;
        this.encoder = encoder;
        this.tributario = tributario;
        this.generador = generador;
    }

    /**
     * @param autorizacion encabezado Authorization del contador, que se reenvía a ms-tributario para comprobar la empresa
     * @param creador      usuario del token que crea la cuenta (queda en la bitácora de seguridad)
     */
    @Transactional
    public CuentaClienteResponse crear(Long clienteId, String correo, String autorizacion, String creador, String ip) {
        String normalizado = normalizar(correo);
        ClienteTributarioClient.ClienteResumen cliente = tributario.buscar(clienteId, autorizacion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado: " + clienteId));
        if (usuarios.existsByClienteId(clienteId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_EMPRESA_CON_CUENTA);
        }
        if (usuarios.existsByUsername(normalizado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_CORREO_EN_USO);
        }
        Rol rol = roles.findByNombre(ROL_CLIENTE)
                .orElseThrow(() -> new IllegalStateException("Falta el rol " + ROL_CLIENTE));

        String temporal = generador.generar();
        Usuario u = new Usuario();
        u.setUsername(normalizado);
        u.setEmail(normalizado);
        u.setPasswordHash(encoder.encode(temporal));
        u.setNombreCompleto(nombreDe(cliente, clienteId));
        u.setClienteId(clienteId);
        u.setDebeCambiarPassword(true);
        u.setRoles(new HashSet<>(Set.of(rol)));
        try {
            u = usuarios.save(u);
        } catch (DataIntegrityViolationException e) {
            // dos contadores a la vez: gana el primero; el índice único parcial y el de username lo impiden
            boolean empresa = e.getMessage() != null && e.getMessage().contains("ux_usuarios_cliente_id");
            throw new ResponseStatusException(HttpStatus.CONFLICT, empresa ? MSG_EMPRESA_CON_CUENTA : MSG_CORREO_EN_USO);
        }
        Long creadorId = usuarios.findByUsername(creador).map(Usuario::getId).orElse(null);
        // la bitácora nunca lleva la contraseña
        bitacora.save(new BitacoraSeguridad(creadorId, creador, "CUENTA_CLIENTE_CREADA",
                "Cuenta " + u.getId() + " (" + normalizado + ") para el cliente " + clienteId, ip));
        return new CuentaClienteResponse(u.getId(), normalizado, clienteId, temporal);
    }

    static final String MSG_EMPRESA_CON_CUENTA = "La empresa ya tiene una cuenta de acceso";
    static final String MSG_CORREO_EN_USO = "Ese correo ya está registrado";

    private static String normalizar(String correo) {
        String c = correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
        if (c.isEmpty() || c.length() > CORREO_MAXIMO || !CORREO.matcher(c).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Faltan o son inválidos: correo (con formato válido y hasta " + CORREO_MAXIMO + " caracteres)");
        }
        return c;
    }

    private static String nombreDe(ClienteTributarioClient.ClienteResumen cliente, Long clienteId) {
        String r = cliente.razonSocial();
        if (r == null || r.isBlank()) return "Cliente " + clienteId;
        return r.length() > 150 ? r.substring(0, 150) : r;
    }
}
