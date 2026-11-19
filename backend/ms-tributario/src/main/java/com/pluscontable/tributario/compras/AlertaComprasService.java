package com.pluscontable.tributario.compras;

import com.pluscontable.tributario.aviso.AvisoInternoService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-25 · T212: la alerta de fin de mes llega al <b>contador</b> como aviso interno (la bandeja de HU-12), no al cliente (decisión de
 * Beto, 18/11). Desde {@code diasAntesCierre} días antes del último día calendario del mes (propuesta de Beto: 5, configurable) y hasta
 * ese último día, crea <b>un aviso por cliente y por mes</b> si el cliente tiene compras sin validar (sin clasificar o con propuesta
 * pendiente de validación). Repetirlo el mismo mes no duplica. Supuesto mío, por confirmar: un aviso por cliente y mes, y el mensaje no
 * lleva cantidades (se creó el primer día de la ventana y las cifras cambian); la cantidad real está en la pestaña «Compras».
 */
@Service
public class AlertaComprasService {

    public static final String ORIGEN = "HU25_COMPRAS";
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyy-MM");

    private final JdbcTemplate jdbc;
    private final AvisoInternoService avisos;
    private final int diasAntesCierre;

    public AlertaComprasService(JdbcTemplate jdbc, AvisoInternoService avisos,
                                @Value("${app.tributario.compras.alerta-dias-antes-cierre:5}") int diasAntesCierre) {
        this.jdbc = jdbc;
        this.avisos = avisos;
        this.diasAntesCierre = diasAntesCierre;
    }

    public record Resultado(int clientesConPendientes, int avisosCreados) {
    }

    /** Fuera de la ventana de fin de mes no hace nada. Devuelve cuántos clientes tenían pendientes y cuántos avisos nuevos se crearon. */
    @Transactional
    public Resultado generar(LocalDate hoy) {
        Objects.requireNonNull(hoy, "hoy");
        // «Sin validar» = SIN_CLASIFICAR o PROPUESTA; la regla de la ventana es la misma que usa la marca de cada compra.
        if (!ReglaClasificacionCompra.alertaCierre(ReglaClasificacionCompra.Estado.SIN_CLASIFICAR, hoy, diasAntesCierre)) {
            return new Resultado(0, 0);
        }
        record Cliente(Long id, String razonSocial, String ruc) {
        }
        List<Cliente> pendientes = jdbc.query("""
                SELECT c.id, c.razon_social, c.ruc
                FROM tributario_schema.clientes c
                WHERE c.activo = TRUE AND EXISTS (SELECT 1 FROM tributario_schema.compras_cliente k
                                                  WHERE k.cliente_id = c.id AND k.validada_en IS NULL)
                ORDER BY c.id""", (rs, n) -> new Cliente(rs.getLong(1), rs.getString(2), rs.getString(3)));
        int creados = 0;
        for (Cliente c : pendientes) {
            String clave = "COMPRAS_SIN_VALIDAR:cliente:" + c.id() + ":" + MES.format(hoy);
            // Se consulta antes de crear: si el cliente cambió de razón social entre dos días, el mensaje distinto haría que
            // AvisoInternoService rechazara el reintento.
            if (avisos.buscarPorEvento(ORIGEN, clave).isPresent()) continue;
            avisos.crear(ORIGEN, clave, "El cliente " + c.razonSocial() + " (RUC " + c.ruc() + ") tiene compras sin validar y falta poco para el último día del mes. "
                    + "Revise la pestaña «Compras» de su ficha.", List.of(c.id()));
            creados++;
        }
        return new Resultado(pendientes.size(), creados);
    }
}
