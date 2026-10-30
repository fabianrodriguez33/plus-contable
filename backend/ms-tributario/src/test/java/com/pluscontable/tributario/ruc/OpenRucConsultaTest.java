package com.pluscontable.tributario.ruc;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Servidor simulado en el equipo (127.0.0.1): ninguna prueba sale a la red real. */
class OpenRucConsultaTest {

    private static final String RUC = "20131312955";
    private static final String CUERPO_OK = """
            {"ruc":"20131312955","razon_social":"ENTIDAD DE PRUEBA","estado":"ACTIVO","condicion":"HABIDO",
             "direccion":"AV. DE PRUEBA 123","ubigeo":"150101","source":"SUNAT","as_of":"2026-10-29",
             "_more":{"message":"publicidad","get_api_key":"https://ejemplo.invalid"}}""";

    private HttpServer servidor;

    @AfterEach
    void detener() {
        if (servidor != null) servidor.stop(0);
    }

    private OpenRucConsulta con(HttpHandler manejador, long lecturaMs) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/", manejador);
        servidor.start();
        return new OpenRucConsulta("http://127.0.0.1:" + servidor.getAddress().getPort() + "/", 500, lecturaMs);
    }

    private static void responder(HttpExchange e, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().add("Content-Type", "application/json");
        e.sendResponseHeaders(estado, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) e.getResponseBody().write(bytes);
        e.close();
    }

    @Test
    void devuelveLosDatosEIgnoraElBloquePublicitario() throws Exception {
        AtomicReference<String> ruta = new AtomicReference<>();
        AtomicReference<String> autorizacion = new AtomicReference<>();
        var consulta = con(e -> {
            ruta.set(e.getRequestURI().getPath());
            autorizacion.set(e.getRequestHeaders().getFirst("Authorization"));
            responder(e, 200, CUERPO_OK);
        }, 2000);

        var resultado = consulta.consultar(RUC);

        assertThat(resultado).isEqualTo(new ResultadoConsulta.Encontrado(new DatosRuc(RUC, "ENTIDAD DE PRUEBA",
                "ACTIVO", "HABIDO", "AV. DE PRUEBA 123", "150101", "SUNAT", LocalDate.of(2026, 10, 29))));
        assertThat(ruta.get()).isEqualTo("/api/ruc/" + RUC);
        assertThat(autorizacion.get()).isNull();
    }

    @Test
    void un404EsRucNoEncontrado() throws Exception {
        assertThat(con(e -> responder(e, 404, "{\"error\":\"not found\"}"), 2000).consultar(RUC))
                .isInstanceOf(ResultadoConsulta.NoEncontrado.class);
    }

    @Test
    void elLimiteDeConsultasEsServicioNoDisponible() throws Exception {
        assertThat(con(e -> responder(e, 429, "{}"), 2000).consultar(RUC))
                .isInstanceOf(ResultadoConsulta.NoDisponible.class);
    }

    @Test
    void unErrorDelServidorEsServicioNoDisponible() throws Exception {
        assertThat(con(e -> responder(e, 503, ""), 2000).consultar(RUC))
                .isInstanceOf(ResultadoConsulta.NoDisponible.class);
    }

    @Test
    void siElProveedorTardaMasQueElTimeoutNoBloquea() throws Exception {
        var consulta = con(e -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            responder(e, 200, CUERPO_OK);
        }, 300);

        long inicio = System.nanoTime();
        var resultado = consulta.consultar(RUC);
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(resultado).isInstanceOf(ResultadoConsulta.NoDisponible.class);
        assertThat(ms).isLessThan(1200);
    }

    @Test
    void conElServicioCaidoDevuelveNoDisponible() throws Exception {
        var consulta = con(e -> responder(e, 200, CUERPO_OK), 2000);
        servidor.stop(0);

        assertThat(consulta.consultar(RUC)).isInstanceOf(ResultadoConsulta.NoDisponible.class);
    }

    @Test
    void unaRespuestaQueNoEsJsonEsNoDisponible() throws Exception {
        assertThat(con(e -> responder(e, 200, "<html>no es json</html>"), 2000).consultar(RUC))
                .isInstanceOf(ResultadoConsulta.NoDisponible.class);
    }

    @Test
    void siLaRespuestaEsDeOtroRucSeDescarta() throws Exception {
        assertThat(con(e -> responder(e, 200, CUERPO_OK.replace("20131312955", "20100047218")), 2000).consultar(RUC))
                .isInstanceOf(ResultadoConsulta.NoDisponible.class);
    }

    @Test
    void siFaltaLaRazonSocialSeDescarta() throws Exception {
        assertThat(con(e -> responder(e, 200, "{\"ruc\":\"20131312955\",\"estado\":\"ACTIVO\"}"), 2000).consultar(RUC))
                .isInstanceOf(ResultadoConsulta.NoDisponible.class);
    }

    @Test
    void noSigueRedirecciones() throws Exception {
        var consulta = con(e -> {
            e.getResponseHeaders().add("Location", "http://127.0.0.1:1/otro");
            e.sendResponseHeaders(302, -1);
            e.close();
        }, 2000);

        assertThat(consulta.consultar(RUC)).isInstanceOf(ResultadoConsulta.NoDisponible.class);
    }

    @Test
    void unaFechaIlegibleNoInvalidaLosDatos() throws Exception {
        var resultado = con(e -> responder(e, 200, CUERPO_OK.replace("2026-10-29", "ayer")), 2000).consultar(RUC);

        assertThat(resultado).isInstanceOfSatisfying(ResultadoConsulta.Encontrado.class,
                r -> assertThat(r.datos().fechaDatos()).isNull());
    }
}
