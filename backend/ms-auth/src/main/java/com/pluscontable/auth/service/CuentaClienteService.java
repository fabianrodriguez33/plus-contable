package com.pluscontable.auth.service;

import com.pluscontable.auth.dto.CuentaClienteResponse;
import com.pluscontable.auth.model.BitacoraSeguridad;
import com.pluscontable.auth.model.Rol;
import com.pluscontable.auth.model.Usuario;
import com.pluscontable.auth.repository.BitacoraRepository;
import com.pluscontable.auth.repository.RolRepository;
import com.pluscontable.auth.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final long temporalHoras;
    private final TransactionTemplate transaccion;
    private final RestablecimientoService restablecimientos;

    public CuentaClienteService(UsuarioRepository usuarios, RolRepository roles, BitacoraRepository bitacora,
                                PasswordEncoder encoder, ClienteTributarioClient tributario, GeneradorPassword generador,
                                @Value("${app.security.temporal-horas:48}") long temporalHoras,
                                TransactionTemplate transaccion, RestablecimientoService restablecimientos) {
        this.restablecimientos = restablecimientos;
        this.temporalHoras = temporalHoras;
        this.transaccion = transaccion;
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
    public CuentaClienteResponse crear(Long clienteId, String correo, String autorizacion, String creador, String ip) {
        String normalizado = normalizar(correo);
        // La llamada a ms-tributario (puede tardar hasta 8 s) va ANTES de abrir la transacción: así no se retiene una
        // conexión a la base mientras se espera al otro servicio. Solo lo que escribe en la base va dentro de la transacción.
        ClienteTributarioClient.ClienteResumen cliente = tributario.buscar(clienteId, autorizacion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado: " + clienteId));
        return transaccion.execute(estado -> guardar(clienteId, normalizado, cliente, creador, ip));
    }

    private CuentaClienteResponse guardar(Long clienteId, String normalizado, ClienteTributarioClient.ClienteResumen cliente,
                                          String creador, String ip) {
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
        u.setTemporalVenceEn(LocalDateTime.now().plusHours(temporalHoras));
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
        return new CuentaClienteResponse(u.getId(), normalizado, clienteId, temporal, u.getTemporalVenceEn());
    }

    /**
     * HU-37 · «Restablecer acceso» (decisión de Beto del 11/11): genera otra contraseña temporal para la cuenta existente de la
     * empresa. La anterior deja de servir (cambia el hash), se reinician las 48 horas, vuelve a exigirse el cambio de contraseña y
     * se cierran las sesiones activas de la cuenta (sube la versión de sesión). La nueva solo viaja en esta respuesta.
     *
     * @param restablecedor usuario del token que lo pide (queda en la bitácora de seguridad)
     */
    public CuentaClienteResponse restablecer(Long clienteId, String restablecedor, String ip) {
        return transaccion.execute(estado -> {
            Usuario u = usuarios.findByClienteId(clienteId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_SIN_CUENTA));
            String temporal = generador.generar();
            u.setPasswordHash(encoder.encode(temporal));
            u.setDebeCambiarPassword(true);
            u.setTemporalUsadaEn(null);
            u.setTemporalVenceEn(LocalDateTime.now().plusHours(temporalHoras));
            u.setSesionVersion(u.getSesionVersion() + 1);
            usuarios.save(u);
            restablecimientos.atender(u.getId(), restablecedor); // HU-41: si el cliente lo había pedido, queda atendido
            Long quienId = usuarios.findByUsername(restablecedor).map(Usuario::getId).orElse(null);
            // la bitácora nunca lleva la contraseña
            bitacora.save(new BitacoraSeguridad(quienId, restablecedor, "CUENTA_CLIENTE_RESTABLECIDA",
                    "Cuenta " + u.getId() + " (" + u.getUsername() + ") del cliente " + clienteId, ip));
            return new CuentaClienteResponse(u.getId(), u.getUsername(), clienteId, temporal, u.getTemporalVenceEn());
        });
    }

    static final String MSG_EMPRESA_CON_CUENTA = "La empresa ya tiene una cuenta de acceso";
    static final String MSG_CORREO_EN_USO = "Ese correo ya está registrado";
    static final String MSG_SIN_CUENTA = "El cliente no tiene una cuenta de acceso";

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
