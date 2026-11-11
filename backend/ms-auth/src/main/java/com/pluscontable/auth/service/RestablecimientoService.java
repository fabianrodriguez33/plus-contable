package com.pluscontable.auth.service;

import com.pluscontable.auth.dto.SolicitudPendienteDto;
import com.pluscontable.auth.model.BitacoraSeguridad;
import com.pluscontable.auth.model.SolicitudRestablecimiento;
import com.pluscontable.auth.model.Usuario;
import com.pluscontable.auth.repository.BitacoraRepository;
import com.pluscontable.auth.repository.SolicitudRestablecimientoRepository;
import com.pluscontable.auth.repository.UsuarioRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * HU-41 · Restablecer las credenciales de acceso. El cliente que olvidó su contraseña la pide desde el login con su correo; el
 * estudio ve la solicitud y la atiende con «Restablecer acceso» (HU-37), que genera otra temporal y deja sin valor la anterior.
 * Decisión del 11/11: el sistema no envía correos, el estudio entrega la temporal al cliente por su medio.
 */
@Service
public class RestablecimientoService {

    /** Igual exista o no la cuenta (criterio 2): la respuesta no confirma si el correo está registrado. */
    public static final String MENSAJE_SOLICITUD = "Si el correo corresponde a una cuenta de cliente, el estudio recibirá tu solicitud "
            + "y te entregará una nueva contraseña temporal. Al ingresar con ella deberás cambiarla.";

    private final UsuarioRepository usuarios;
    private final SolicitudRestablecimientoRepository solicitudes;
    private final BitacoraRepository bitacora;

    public RestablecimientoService(UsuarioRepository usuarios, SolicitudRestablecimientoRepository solicitudes, BitacoraRepository bitacora) {
        this.usuarios = usuarios;
        this.solicitudes = solicitudes;
        this.bitacora = bitacora;
    }

    /**
     * Registra la solicitud solo si el correo es de una cuenta de cliente activa y no tiene otra pendiente; si no, no hace nada.
     * Siempre devuelve el mismo mensaje. Solo un correo vacío o demasiado largo da 400, sin revelar nada de la cuenta.
     * shortcut: sin límite de pedidos por IP; el índice único evita que se acumulen pendientes. Agregar uno si se abusa del endpoint.
     */
    public String solicitar(String correo, String ip) {
        String c = correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
        if (c.isEmpty() || c.length() > CuentaClienteService.CORREO_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el correo con el que ingresas");
        }
        usuarios.findByUsername(c)
                .filter(u -> u.getClienteId() != null && u.isActivo())
                .filter(u -> solicitudes.findByUsuarioIdAndAtendidaEnIsNull(u.getId()).isEmpty())
                .ifPresent(u -> registrar(u, ip));
        return MENSAJE_SOLICITUD;
    }

    private void registrar(Usuario u, String ip) {
        try {
            solicitudes.saveAndFlush(new SolicitudRestablecimiento(u.getId(), ip));
        } catch (DataIntegrityViolationException e) {
            return; // otro pedido de la misma cuenta ganó al mismo tiempo: ya hay una pendiente
        }
        bitacora.save(new BitacoraSeguridad(u.getId(), u.getUsername(), "RESTABLECIMIENTO_SOLICITADO",
                "Cuenta " + u.getId() + " del cliente " + u.getClienteId(), ip));
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
}
