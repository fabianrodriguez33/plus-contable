package com.pluscontable.tributario.compras;

import com.pluscontable.tributario.service.RucValidator;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-25: el contador carga las compras de un cliente (con el SIRE como referencia); el cliente <b>propone</b> cómo clasificarlas
 * («mercadería o gasto» o «activo fijo», con una descripción) y la propuesta queda <b>pendiente de validación del contador</b>, que la
 * valida. Toda lectura y escritura va por {@code cliente_id}: una compra de otra empresa nunca se ve ni se toca (aislamiento, T213).
 */
@Service
public class CompraService {

    /** «Hoy» es el de Lima, aunque el servidor corra en UTC. */
    static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int diasAntesCierre;

    @Autowired
    public CompraService(JdbcTemplate jdbc, @Value("${app.tributario.compras.alerta-dias-antes-cierre:5}") int diasAntesCierre) {
        this(jdbc, Clock.system(ZONA), diasAntesCierre);
    }

    CompraService(JdbcTemplate jdbc, Clock clock, int diasAntesCierre) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.diasAntesCierre = diasAntesCierre;
    }

    /** 404 si el cliente no existe; 400 con todos los errores juntos; 400 si el comprobante de ese proveedor ya está cargado. */
    @Transactional
    public CompraDto registrar(Long clienteId, CompraEntradaDto e, String usuario) {
        existeCliente(clienteId);
        List<String> errores = new ArrayList<>();
        if (e == null) throw new ValidationException("Faltan los datos de la compra");
        if (e.periodo() == null) errores.add("periodo es obligatorio");
        else if (e.periodo().getDayOfMonth() != 1) errores.add("periodo debe ser el primer día del mes");
        String rucError = RucValidator.motivoInvalido(e.proveedorRuc());
        if (rucError != null) errores.add("proveedorRuc: " + rucError);
        if (e.proveedorRazonSocial() != null && e.proveedorRazonSocial().trim().length() > 200) errores.add("proveedorRazonSocial admite hasta 200 caracteres");
        if (e.comprobante() == null || e.comprobante().isBlank()) errores.add("comprobante es obligatorio");
        else if (e.comprobante().trim().length() > 30) errores.add("comprobante admite hasta 30 caracteres");
        if (e.fechaComprobante() == null) errores.add("fechaComprobante es obligatoria");
        if (e.importe() == null || e.importe().compareTo(BigDecimal.ZERO) <= 0) errores.add("importe debe ser mayor que cero");
        if (!errores.isEmpty()) throw new ValidationException(String.join("; ", errores));
        try {
            Long id = jdbc.queryForObject("""
                    INSERT INTO tributario_schema.compras_cliente(cliente_id, periodo, proveedor_ruc, proveedor_razon_social, comprobante,
                                                                  fecha_comprobante, importe, registrada_por)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, clienteId, e.periodo(), e.proveedorRuc(),
                    e.proveedorRazonSocial() == null || e.proveedorRazonSocial().isBlank() ? null : e.proveedorRazonSocial().trim(),
                    e.comprobante().trim(), e.fechaComprobante(), e.importe(), usuario == null || usuario.isBlank() ? "desconocido" : usuario);
            return buscar(clienteId, id);
        } catch (DuplicateKeyException ex) {
            throw new ValidationException("Ese comprobante de ese proveedor ya está cargado para el cliente");
        }
    }

    /** Las compras del cliente, la más reciente primero. {@code estado} nulo = todas. */
    @Transactional(readOnly = true)
    public List<CompraDto> listar(Long clienteId, ReglaClasificacionCompra.Estado estado) {
        existeCliente(clienteId);
        String filtro = estado == null ? "" : switch (estado) {
            case SIN_CLASIFICAR -> " AND tipo_clasificacion IS NULL";
            case PROPUESTA -> " AND tipo_clasificacion IS NOT NULL AND validada_en IS NULL";
            case VALIDADA -> " AND validada_en IS NOT NULL";
        };
        return jdbc.query(SELECT + " WHERE cliente_id = ?" + filtro + " ORDER BY periodo DESC, fecha_comprobante DESC, id DESC",
                this::fila, clienteId);
    }

    /**
     * El cliente propone la clasificación; queda PROPUESTA, pendiente de validación del contador. Puede cambiar su propuesta mientras el
     * contador no la haya validado. 404 si la compra no existe <b>en ese cliente</b>; 400 si ya fue validada o falta el tipo o la
     * descripción. Registra quién y cuándo propuso.
     */
    @Transactional
    public CompraDto proponer(Long clienteId, Long compraId, ClasificacionEntradaDto e, String usuario) {
        CompraDto actual = buscar(clienteId, compraId);
        List<String> errores = new ArrayList<>();
        if (actual.estado() == ReglaClasificacionCompra.Estado.VALIDADA) errores.add("La compra ya fue validada por el contador y no se cambia");
        if (e == null || e.tipo() == null) errores.add("tipo es obligatorio (MERCADERIA_O_GASTO o ACTIVO_FIJO)");
        String descripcion = ReglaClasificacionCompra.descripcionValida(e == null ? null : e.descripcion());
        if (descripcion != null) errores.add(descripcion);
        if (!errores.isEmpty()) throw new ValidationException(String.join("; ", errores));
        jdbc.update("""
                UPDATE tributario_schema.compras_cliente
                SET tipo_clasificacion = ?, descripcion = ?, clasificada_por = ?, clasificada_en = CURRENT_TIMESTAMP
                WHERE id = ? AND cliente_id = ? AND validada_en IS NULL""",
                e.tipo().name(), e.descripcion().trim(), usuario == null || usuario.isBlank() ? "desconocido" : usuario, compraId, clienteId);
        return buscar(clienteId, compraId);
    }

    /**
     * El contador valida la propuesta del cliente tal como está. 404 si la compra no existe en ese cliente; 400 si el cliente todavía no
     * propuso o ya está validada. Registra quién y cuándo validó.
     */
    @Transactional
    public CompraDto validar(Long clienteId, Long compraId, String usuario) {
        CompraDto actual = buscar(clienteId, compraId);
        if (actual.estado() == ReglaClasificacionCompra.Estado.VALIDADA) throw new ValidationException("La compra ya está validada");
        if (actual.estado() == ReglaClasificacionCompra.Estado.SIN_CLASIFICAR) throw new ValidationException("El cliente todavía no propuso una clasificación");
        jdbc.update("""
                UPDATE tributario_schema.compras_cliente SET validada_por = ?, validada_en = CURRENT_TIMESTAMP
                WHERE id = ? AND cliente_id = ? AND tipo_clasificacion IS NOT NULL AND validada_en IS NULL""",
                usuario == null || usuario.isBlank() ? "desconocido" : usuario, compraId, clienteId);
        return buscar(clienteId, compraId);
    }

    private static final String SELECT = """
            SELECT id, cliente_id, periodo, proveedor_ruc, proveedor_razon_social, comprobante, fecha_comprobante, importe,
                   tipo_clasificacion, descripcion, clasificada_en, validada_por, validada_en
            FROM tributario_schema.compras_cliente""";

    private CompraDto fila(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        String tipo = rs.getString("tipo_clasificacion");
        java.sql.Timestamp validadaEn = rs.getTimestamp("validada_en");
        ReglaClasificacionCompra.Estado estado = ReglaClasificacionCompra.estado(tipo != null, validadaEn != null);
        return new CompraDto(rs.getLong("id"), rs.getLong("cliente_id"), rs.getObject("periodo", LocalDate.class), rs.getString("proveedor_ruc"),
                rs.getString("proveedor_razon_social"), rs.getString("comprobante"), rs.getObject("fecha_comprobante", LocalDate.class),
                rs.getBigDecimal("importe"), estado, tipo == null ? null : ReglaClasificacionCompra.Tipo.valueOf(tipo),
                rs.getString("descripcion"), rs.getTimestamp("clasificada_en") == null ? null : rs.getTimestamp("clasificada_en").toInstant(),
                rs.getString("validada_por"), validadaEn == null ? null : validadaEn.toInstant(),
                ReglaClasificacionCompra.alertaCierre(estado, LocalDate.now(clock), diasAntesCierre));
    }

    private CompraDto buscar(Long clienteId, Long compraId) {
        return jdbc.query(SELECT + " WHERE id = ? AND cliente_id = ?", this::fila, compraId, clienteId).stream().findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Compra no encontrada"));
    }

    private void existeCliente(Long clienteId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.clientes WHERE id = ?", Integer.class, clienteId);
        if (n == null || n == 0) throw new EntityNotFoundException("Cliente no encontrado");
    }
}
