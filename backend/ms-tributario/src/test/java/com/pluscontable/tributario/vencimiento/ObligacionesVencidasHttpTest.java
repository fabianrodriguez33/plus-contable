package com.pluscontable.tributario.vencimiento;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-16 · T47, de punta a punta por HTTP real con PostgreSQL desechable, con la definición de Beto: la declarada con la fecha pasada
 * aparece como vencida; con un pago parcial (registrado con el endpoint de HU-13) sigue vencida y muestra lo pagado y el saldo; al
 * pagarse todo deja de aparecer; la sin declarar va aparte; y los permisos (estudio sí; cliente MYPE 403; sin sesión 401). Usa el «hoy»
 * real de Lima, por eso las fechas son relativas. Clientes y obligaciones ficticios que se borran al terminar.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ObligacionesVencidasHttpTest {

    private static final String RUTA = "/api/tributario/obligaciones-vencidas";
    private static final int[] FACTORES = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final AtomicLong SECUENCIA = new AtomicLong(System.nanoTime() % 100_000_000L);
    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Lima"));

    @LocalServerPort int puerto;
    @Value("${app.jwt.secret}") String secreto;
    @Autowired JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<Long> clientes = new ArrayList<>();
    private int mes = 0;

    @AfterEach
    void limpiar() {
        for (Long id : clientes) jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", id);
    }

    private static String rucFicticio() {
        String cuerpo = "20" + String.format("%08d", SECUENCIA.incrementAndGet() % 100_000_000L);
        int suma = 0;
        for (int i = 0; i < FACTORES.length; i++) suma += (cuerpo.charAt(i) - '0') * FACTORES[i];
        int dv = 11 - (suma % 11);
        return cuerpo + (dv == 10 ? 0 : dv == 11 ? 1 : dv);
    }

    private long cliente() {
        Long id = jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes(ruc, razon_social, regimen_tributario)
                VALUES (?, 'Cliente HU16 HTTP SAC', 'MYPE') RETURNING id""", Long.class, rucFicticio());
        clientes.add(id);
        return id;
    }

    private long obligacion(long cliente, String estado, LocalDate vence) {
        LocalDate periodo = LocalDate.of(2020, 1, 1).plusMonths(mes++);
        return jdbc.queryForObject("""
                INSERT INTO tributario_schema.obligaciones(cliente_id, tipo_obligacion, periodo, fecha_vencimiento, estado,
                                                           fecha_declaracion, numero_formulario, monto_determinado)
                VALUES (?, 'IGV', ?, ?, ?, '2026-11-05', 'F-1', 1000.00) RETURNING id""", Long.class, cliente, periodo, vence, estado);
    }

    private String token(String rol) {
        return token(rol, null);
    }

    /** Como ms-auth: el claim clienteId solo va en el token de una cuenta de cliente. */
    private String token(String rol, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var b = Jwts.builder().subject("usuario-prueba").claim("roles", List.of(rol));
        if (clienteId != null) b.claim("clienteId", clienteId);
        return b.issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private HttpResponse<String> consultar(String ruta, String rol) throws Exception {
        return consultarConToken(ruta, rol == null ? null : token(rol));
    }

    private HttpResponse<String> consultarConToken(String ruta, String token) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode lista(long cliente, String rol) throws Exception {
        var r = consultar(RUTA + "?clienteId=" + cliente, rol);
        assertThat(r.statusCode()).isEqualTo(200);
        return json.readTree(r.body());
    }

    private static List<Long> ids(JsonNode filas) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode n : filas) ids.add(n.get("obligacionId").asLong());
        return ids;
    }

    /** Formulario multipart sin archivo, como lo envía la pantalla de pagos (HU-13). */
    private HttpResponse<String> pagar(long obligacion, String monto, String constancia) throws Exception {
        String frontera = "----pc" + UUID.randomUUID();
        StringBuilder cuerpo = new StringBuilder();
        for (String[] campo : new String[][] {{"fechaPago", HOY.toString()}, {"montoPagado", monto}, {"numeroConstancia", constancia}}) {
            cuerpo.append("--").append(frontera).append("\r\nContent-Disposition: form-data; name=\"").append(campo[0])
                    .append("\"\r\n\r\n").append(campo[1]).append("\r\n");
        }
        cuerpo.append("--").append(frontera).append("--\r\n");
        var r = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/tributario/obligaciones/" + obligacion + "/pagos"))
                .header("Authorization", "Bearer " + token("CONTADOR")).header("Content-Type", "multipart/form-data; boundary=" + frontera)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString())).build();
        return http.send(r, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void vencidaConPagoParcialSigueVencidaConSaldoYAlPagarTodoDejaDeAparecer() throws Exception {
        long c = cliente();
        long vencida = obligacion(c, "DECLARADO", HOY.minusDays(5));       // monto determinado 1000.00
        obligacion(c, "DECLARADO", HOY);                                   // vence hoy: todavía no
        obligacion(c, "DECLARADO", HOY.plusDays(3));                       // futura

        for (String rol : List.of("CONTADOR", "ADMIN")) {
            JsonNode v = lista(c, rol).get("vencidas");
            assertThat(ids(v)).containsExactly(vencida);
            assertThat(v.get(0).get("diasVencida").asLong()).isEqualTo(5);
            assertThat(v.get(0).get("razonSocial").asText()).isEqualTo("Cliente HU16 HTTP SAC");
            assertThat(v.get(0).get("montoPagado").decimalValue()).isEqualByComparingTo("0");
            assertThat(v.get(0).get("saldo").decimalValue()).isEqualByComparingTo("1000.00");
        }

        assertThat(pagar(vencida, "400.00", "C-VENC-1").statusCode()).isEqualTo(201);
        JsonNode conParcial = lista(c, "CONTADOR").get("vencidas");
        assertThat(ids(conParcial)).containsExactly(vencida);                // con pago parcial sigue vencida
        assertThat(conParcial.get(0).get("estado").asText()).isEqualTo("PAGO_PARCIAL");
        assertThat(conParcial.get(0).get("montoPagado").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(conParcial.get(0).get("saldo").decimalValue()).isEqualByComparingTo("600.00");

        assertThat(pagar(vencida, "600.00", "C-VENC-2").statusCode()).isEqualTo(201);
        assertThat(lista(c, "CONTADOR").get("vencidas")).isEmpty();          // pagada: nunca vencida
    }

    @Test
    void laSinDeclararConFechaPasadaVaAparteComoPendienteDeAtencion() throws Exception {
        long c = cliente();
        long sinDeclarar = obligacion(c, "PENDIENTE", HOY.minusDays(2));
        obligacion(c, "PENDIENTE", HOY.plusDays(2));

        JsonNode r = lista(c, "CONTADOR");
        assertThat(r.get("vencidas")).isEmpty();
        assertThat(ids(r.get("sinDeclarar"))).containsExactly(sinDeclarar);
    }

    @Test
    void sinFiltroDevuelveLaCarteraYElFiltroPorClienteAisla() throws Exception {
        long a = cliente();
        long b = cliente();
        long deA = obligacion(a, "DECLARADO", HOY.minusDays(2));
        long deB = obligacion(b, "DECLARADO", HOY.minusDays(9));

        assertThat(ids(lista(a, "CONTADOR").get("vencidas"))).containsExactly(deA);
        assertThat(ids(lista(b, "CONTADOR").get("vencidas"))).containsExactly(deB);
        var todos = json.readTree(consultar(RUTA, "CONTADOR").body());
        assertThat(ids(todos.get("vencidas"))).contains(deA, deB);
    }

    @Test
    void elClienteVeSusVencidasConPagadoYSaldo_peroNoLasSinDeclararNiLasDeOtraEmpresa() throws Exception {
        long suyo = cliente();
        long ajeno = cliente();
        long parcial = obligacion(suyo, "DECLARADO", HOY.minusDays(4));
        assertThat(pagar(parcial, "250.00", "C-CLI-1").statusCode()).isEqualTo(201);
        obligacion(suyo, "PENDIENTE", HOY.minusDays(6));               // sin declarar: es del estudio, no la ve el cliente
        obligacion(ajeno, "DECLARADO", HOY.minusDays(8));
        String ruta = RUTA + "?clienteId=" + suyo;

        var r = consultarConToken(ruta, token("CLIENTE_MYPE", suyo));

        assertThat(r.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(r.body());
        assertThat(ids(cuerpo.get("vencidas"))).containsExactly(parcial);                         // con pago parcial sigue vencida
        assertThat(cuerpo.get("vencidas").get(0).get("montoPagado").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(cuerpo.get("vencidas").get(0).get("saldo").decimalValue()).isEqualByComparingTo("750.00");
        assertThat(cuerpo.get("sinDeclarar")).isEmpty();
        // el estudio sí ve las sin declarar de ese mismo cliente
        assertThat(json.readTree(consultar(ruta, "CONTADOR").body()).get("sinDeclarar")).hasSize(1);
    }

    @Test
    void elClienteNuncaVeLaCarteraNiOtraEmpresaYSinSesionEs401() throws Exception {
        long suyo = cliente();
        long ajeno = cliente();
        obligacion(ajeno, "DECLARADO", HOY.minusDays(5));
        String delCliente = token("CLIENTE_MYPE", suyo);

        assertThat(consultarConToken(RUTA, delCliente).statusCode()).isEqualTo(403);                                  // sin clienteId = cartera
        assertThat(consultarConToken(RUTA + "?clienteId=" + ajeno, delCliente).statusCode()).isEqualTo(403);
        assertThat(consultarConToken(RUTA + "?clienteId=" + suyo, token("CLIENTE_MYPE", null)).statusCode()).isEqualTo(403);
        assertThat(consultar(RUTA + "?clienteId=" + suyo, null).statusCode()).isEqualTo(401);
    }
}
