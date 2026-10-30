package com.pluscontable.tributario.ruc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Proveedor OpenRUC: {@code GET {base}/api/ruc/{ruc}}, sin API key. Solo consulta RUC que empiezan con 20.
 * Ignora los campos que no conoce (la respuesta trae un bloque publicitario {@code _more}) y no sigue redirecciones.
 */
@Component
@ConditionalOnProperty(name = "app.ruc.proveedor", havingValue = "openruc", matchIfMissing = true)
public class OpenRucConsulta implements ConsultaRuc {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(String ruc, @JsonProperty("razon_social") String razonSocial, String estado,
                     String condicion, String direccion, String ubigeo, String source,
                     @JsonProperty("as_of") String asOf) {
    }

    private final RestClient cliente;

    public OpenRucConsulta(@Value("${app.ruc.openruc.base-url}") String baseUrl,
                           @Value("${app.ruc.openruc.connect-timeout-ms}") long conexionMs,
                           @Value("${app.ruc.openruc.read-timeout-ms}") long lecturaMs) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(conexionMs))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofMillis(lecturaMs));
        this.cliente = RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .requestFactory(fabrica)
                .build();
    }

    @Override
    public ResultadoConsulta consultar(String ruc) {
        try {
            return cliente.get().uri("/api/ruc/{ruc}", ruc).accept(MediaType.APPLICATION_JSON)
                    .exchange((peticion, respuesta) -> {
                        HttpStatusCode estado = respuesta.getStatusCode();
                        if (estado.value() == 404) return new ResultadoConsulta.NoEncontrado();
                        if (estado.value() == 429) return new ResultadoConsulta.NoDisponible("límite de consultas del proveedor");
                        if (!estado.is2xxSuccessful()) {
                            return new ResultadoConsulta.NoDisponible("el proveedor respondió HTTP " + estado.value());
                        }
                        return aResultado(ruc, respuesta.bodyTo(Respuesta.class));
                    });
        } catch (RuntimeException e) {
            // tiempo agotado, conexión rechazada, JSON ilegible…: el alta continúa sin la consulta
            return new ResultadoConsulta.NoDisponible("no se pudo consultar al proveedor");
        }
    }

    private static ResultadoConsulta aResultado(String rucPedido, Respuesta r) {
        if (r == null || !rucPedido.equals(r.ruc()) || r.razonSocial() == null || r.razonSocial().isBlank()) {
            return new ResultadoConsulta.NoDisponible("respuesta inesperada del proveedor");
        }
        return new ResultadoConsulta.Encontrado(new DatosRuc(r.ruc(), r.razonSocial().trim(), r.estado(),
                r.condicion(), r.direccion(), r.ubigeo(), r.source(), fecha(r.asOf())));
    }

    private static LocalDate fecha(String texto) {
        try {
            return texto == null ? null : LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
