package com.pluscontable.tributario.aviso;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.util.List;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Núcleo compartido. Cada historia proporciona una clave estable para su evento; no define reglas tributarias. */
@Service
public class AvisoInternoService {
    private final JdbcTemplate jdbc;

    public AvisoInternoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Un reintento idéntico devuelve el aviso existente. La restricción SQL resuelve carreras concurrentes. */
    @Transactional
    public AvisoInternoDto crear(String origen, String claveEvento, String mensaje, List<Long> clienteIds) {
        String origenValido = texto(origen, 40, "origen");
        String claveValida = texto(claveEvento, 160, "claveEvento");
        String mensajeValido = texto(mensaje, 500, "mensaje");
        if (clienteIds == null || clienteIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ValidationException("clienteIds debe contener identificadores válidos");
        }
        List<Long> ids = List.copyOf(new TreeSet<>(clienteIds));
        for (Long id : ids) {
            Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.clientes WHERE id = ?", Integer.class, id);
            if (existe == null || existe == 0) throw new ValidationException("Cliente inexistente: " + id);
        }
        List<Long> nuevos = jdbc.query("""
                INSERT INTO tributario_schema.avisos(origen, clave_evento, mensaje)
                VALUES (?, ?, ?) ON CONFLICT (origen, clave_evento) DO NOTHING RETURNING id
                """, (rs, fila) -> rs.getLong(1), origenValido, claveValida, mensajeValido);
        if (nuevos.isEmpty()) {
            Long previoId = jdbc.queryForObject("SELECT id FROM tributario_schema.avisos WHERE origen = ? AND clave_evento = ?",
                    Long.class, origenValido, claveValida);
            AvisoInternoDto previo = buscar(previoId);
            if (!previo.mensaje().equals(mensajeValido) || !previo.clienteIds().equals(ids)) {
                throw new ValidationException("Clave de evento ya utilizada con otro contenido");
            }
            return previo;
        }
        long avisoId = nuevos.getFirst();
        for (Long id : ids) {
            jdbc.update("INSERT INTO tributario_schema.avisos_clientes(aviso_id, cliente_id) VALUES (?, ?)", avisoId, id);
        }
        return buscar(avisoId);
    }

    @Transactional(readOnly = true)
    public List<AvisoInternoDto> bandeja(Boolean leido) {
        String filtro = leido == null ? "" : leido ? " WHERE leido_en IS NOT NULL" : " WHERE leido_en IS NULL";
        return jdbc.query("SELECT id FROM tributario_schema.avisos" + filtro + " ORDER BY creado_en DESC, id DESC",
                (rs, fila) -> buscar(rs.getLong(1)));
    }

    @Transactional
    public AvisoInternoDto marcarLeido(Long id) {
        if (id == null || id <= 0) throw new ValidationException("id inválido");
        jdbc.update("UPDATE tributario_schema.avisos SET leido_en = CURRENT_TIMESTAMP WHERE id = ? AND leido_en IS NULL", id);
        return buscar(id);
    }

    @Transactional(readOnly = true)
    public AvisoInternoDto buscar(Long id) {
        List<AvisoInternoDto> encontrados = jdbc.query("""
                SELECT id, origen, clave_evento, mensaje, creado_en, leido_en
                FROM tributario_schema.avisos WHERE id = ?
                """, (rs, fila) -> new AvisoInternoDto(rs.getLong("id"), rs.getString("origen"),
                rs.getString("clave_evento"), rs.getString("mensaje"), rs.getTimestamp("creado_en").toInstant(),
                rs.getTimestamp("leido_en") == null ? null : rs.getTimestamp("leido_en").toInstant(),
                jdbc.query("SELECT cliente_id FROM tributario_schema.avisos_clientes WHERE aviso_id = ? ORDER BY cliente_id",
                        (clientes, n) -> clientes.getLong(1), rs.getLong("id"))), id);
        if (encontrados.isEmpty()) throw new EntityNotFoundException("Aviso no encontrado");
        return encontrados.getFirst();
    }

    private String texto(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank() || valor.trim().length() > maximo) {
            throw new ValidationException(campo + " es obligatorio y admite hasta " + maximo + " caracteres");
        }
        return valor.trim();
    }
}
