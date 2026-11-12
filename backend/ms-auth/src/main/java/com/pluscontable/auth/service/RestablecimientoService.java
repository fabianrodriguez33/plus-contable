package com.pluscontable.auth.service;

import com.pluscontable.auth.dto.SolicitudPendienteDto;
import com.pluscontable.auth.model.BitacoraSeguridad;
import com.pluscontable.auth.model.IntentoRestablecimiento;
import com.pluscontable.auth.model.SolicitudRestablecimiento;
import com.pluscontable.auth.repository.BitacoraRepository;
import com.pluscontable.auth.repository.IntentoRestablecimientoRepository;
import com.pluscontable.auth.repository.SolicitudRestablecimientoRepository;
import com.pluscontable.auth.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * HU-41 · Restablecer las credenciales de acceso. El cliente que olvidó su contraseña la pide desde el login con su correo; el
 * estudio ve la solicitud y la atiende con «Restablecer acceso» (HU-37), que genera otra temporal y deja sin valor la anterior.
 * Decisión del 11/11: el sistema no envía correos, el estudio entrega la temporal al cliente por su medio.
 *
 * <p>Revisión de Julio: el endpoint es público, así que (1) se limitan los pedidos por IP y por correo; (2) la respuesta hace el
 * mismo trabajo exista o no la cuenta: valida, cuenta y guarda un intento, y busca la cuenta recién después de responder, en otro
 * hilo; (3) la solicitud y su bitácora se guardan en una sola transacción.
 */
@Service
public class RestablecimientoService {

    private static final Logger log = LoggerFactory.getLogger(RestablecimientoService.class);

    /** Igual exista o no la cuenta (criterio 2): la respuesta no confirma si el correo está registrado. */
    public static final String MENSAJE_SOLICITUD = "Si el correo corresponde a una cuenta de cliente, el estudio recibirá tu solicitud "
            + "y te entregará una nueva contraseña temporal. Al ingresar con ella deberás cambiarla.";
    public static final String MENSAJE_DEMASIADOS = "Hiciste demasiados pedidos. Intenta de nuevo más tarde.";

    private final UsuarioRepository usuarios;
    private final SolicitudRestablecimientoRepository solicitudes;
    private final IntentoRestablecimientoRepository intentos;
    private final BitacoraRepository bitacora;
    private final TransactionTemplate transaccion;
    private final Executor segundoPlano;
    private final int maximoPorIp;
    private final int maximoPorCorreo;
    private final long ventanaMinutos;

    public RestablecimientoService(UsuarioRepository usuarios, SolicitudRestablecimientoRepository solicitudes,
                                   IntentoRestablecimientoRepository intentos, BitacoraRepository bitacora,
                                   TransactionTemplate transaccion, @Qualifier("applicationTaskExecutor") Executor segundoPlano,
                                   @Value("${app.restablecimiento.maximo-por-ip:5}") int maximoPorIp,
                                   @Value("${app.restablecimiento.maximo-por-correo:3}") int maximoPorCorreo,
                                   @Value("${app.restablecimiento.ventana-minutos:60}") long ventanaMinutos) {
        this.usuarios = usuarios;
        this.solicitudes = solicitudes;
        this.intentos = intentos;
        this.bitacora = bitacora;
        this.transaccion = transaccion;
        this.segundoPlano = segundoPlano;
        this.maximoPorIp = maximoPorIp;
        this.maximoPorCorreo = maximoPorCorreo;
        this.ventanaMinutos = ventanaMinutos;
    }

    /**
     * Siempre el mismo mensaje y el mismo trabajo antes de responder, exista o no la cuenta. 400 si el correo está vacío o es
     * demasiado largo; 429 si la IP o el correo superan el límite de la ventana (también sin revelar nada de la cuenta).
     * shortcut: contar y guardar el intento no es atómico; dos pedidos simultáneos pueden pasar el límite por uno. Bloquear si importa.
     */
    public String solicitar(String correo, String ip) {
        String c = correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
        if (c.isEmpty() || c.length() > CuentaClienteService.CORREO_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el correo con el que ingresas");
        }
        String origen = ip == null || ip.isBlank() ? "desconocida" : ip;
        String huella = huella(c);
        LocalDateTime ahora = LocalDateTime.now();
        intentos.borrarAnterioresA(ahora.minusDays(1));
        LocalDateTime desde = ahora.minusMinutes(ventanaMinutos);
        if (intentos.countByIpAndFechaAfter(origen, desde) >= maximoPorIp
                || intentos.countByCorreoHuellaAndFechaAfter(huella, desde) >= maximoPorCorreo) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MENSAJE_DEMASIADOS);
        }
        intentos.save(new IntentoRestablecimiento(huella, origen));
        segundoPlano.execute(() -> registrarSiCorresponde(c, origen));
        return MENSAJE_SOLICITUD;
    }

    /**
     * En segundo plano: si el correo es de una cuenta de cliente activa sin otra pendiente, guarda la solicitud y su bitácora en
     * una sola transacción (si una falla, no queda ninguna).
     */
    void registrarSiCorresponde(String correo, String ip) {
        try {
            transaccion.executeWithoutResult(estado -> usuarios.findByUsername(correo)
                    .filter(u -> u.getClienteId() != null && u.isActivo())
                    .filter(u -> solicitudes.findByUsuarioIdAndAtendidaEnIsNull(u.getId()).isEmpty())
                    .ifPresent(u -> {
                        solicitudes.saveAndFlush(new SolicitudRestablecimiento(u.getId(), ip));
                        bitacora.save(new BitacoraSeguridad(u.getId(), u.getUsername(), "RESTABLECIMIENTO_SOLICITADO",
                                "Cuenta " + u.getId() + " del cliente " + u.getClienteId(), ip));
                    }));
        } catch (DataIntegrityViolationException e) {
            // otro pedido de la misma cuenta ganó al mismo tiempo: ya hay una pendiente y esta transacción se deshizo entera
        } catch (RuntimeException e) {
            log.error("No se pudo registrar la solicitud de restablecimiento", e); // el cliente ya recibió su respuesta
        }
    }

    /** Para el estudio: las solicitudes sin atender, la más antigua primero. */
    public List<SolicitudPendienteDto> pendientes() {
        return solicitudes.pendientes();
    }

    /** La llama «Restablecer acceso» (HU-37) dentro de su transacción: la solicitud pendiente de la cuenta queda atendida. */
    void atender(Long usuarioId, String quien) {
        solicitudes.findByUsuarioIdAndAtendidaEnIsNull(usuarioId).ifPresent(s -> {
            s.atender(quien);
            solicitudes.save(s);
        });
    }

    static String huella(String correo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(correo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 está en toda JVM
        }
    }
}
