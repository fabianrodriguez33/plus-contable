package com.pluscontable.tributario.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.service.ClienteService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * HT-03 (tarea 12): ms-tributario frente a los tokens que emite ms-auth.
 *
 * <p>El token de estas pruebas se arma con la misma forma que {@code JwtProvider.generate} de ms-auth
 * (subject = usuario, claim {@code roles}, {@code iat}, {@code exp}, HS256 con el secreto compartido),
 * que ms-auth fija en {@code ContratoTokenTest}. Se llama por HTTP real a un endpoint protegido.
 * No se levantan los dos servicios a la vez: son módulos Maven distintos y el contrato lo cubren ambas pruebas.
 * El servicio de clientes va simulado: no se escribe en la base.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TokenEntreServiciosTest {

    private static final String RUTA_PROTEGIDA = "/api/tributario/clientes";

    @LocalServerPort int puerto;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean ClienteService clienteService;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void preparar() {
        when(clienteService.listar()).thenReturn(List.of());
    }

    private SecretKey clave() {
        return Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
    }

    /** Token con la misma forma que emite ms-auth (HU-36: incluye el claim jti). */
    private String tokenDeMsAuth(String usuario, List<String> roles, long msHastaExpirar) {
        long ahora = System.currentTimeMillis();
        return Jwts.builder().id(UUID.randomUUID().toString()).subject(usuario).claim("roles", roles)
                .issuedAt(new Date(ahora)).expiration(new Date(ahora + msHastaExpirar))
                .signWith(clave()).compact();
    }

    /** Token emitido antes de HU-36: sin jti. */
    private String tokenAnteriorSinJti(String usuario, List<String> roles) {
        long ahora = System.currentTimeMillis();
        return Jwts.builder().subject(usuario).claim("roles", roles)
                .issuedAt(new Date(ahora)).expiration(new Date(ahora + 60_000))
                .signWith(clave()).compact();
    }

    private int estado(String autorizacion) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + RUTA_PROTEGIDA)).GET();
        if (autorizacion != null) b.header("Authorization", autorizacion);
        return http.send(b.build(), BodyHandlers.ofString()).statusCode();
    }

    @Test
    void tokenVigenteConLaFormaDeMsAuthSeAcepta() throws Exception {
        assertThat(estado("Bearer " + tokenDeMsAuth("contador1", List.of("CONTADOR"), 60_000))).isEqualTo(200);
        assertThat(estado("Bearer " + tokenDeMsAuth("admin", List.of("ADMIN", "CONTADOR"), 60_000))).isEqualTo(200);
    }

    @Test
    void tokenVencidoSeRechazaCon401() throws Exception {
        assertThat(estado("Bearer " + tokenDeMsAuth("contador1", List.of("CONTADOR"), -1_000))).isEqualTo(401);
    }

    @Test
    void tokenFirmadoConOtraClaveSeRechazaCon401() throws Exception {
        SecretKey otra = Keys.hmacShaKeyFor("otra-clave-ficticia-de-al-menos-32-bytes".getBytes(StandardCharsets.UTF_8));
        String falso = Jwts.builder().subject("contador1").claim("roles", List.of("CONTADOR"))
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otra).compact();
        assertThat(estado("Bearer " + falso)).isEqualTo(401);
    }

    /** Se cambia el rol dentro del contenido y se conserva la firma original: no debe darse acceso. */
    @Test
    void tokenConElRolAlteradoSeRechazaCon401() throws Exception {
        String original = tokenDeMsAuth("cliente1", List.of("CLIENTE_MYPE"), 60_000);
        assertThat(estado("Bearer " + original)).isEqualTo(403);

        String[] partes = original.split("\\.");
        String contenido = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        assertThat(contenido).contains("CLIENTE_MYPE");
        String alterado = contenido.replace("CLIENTE_MYPE", "CONTADOR");
        String contenidoAlterado = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(alterado.getBytes(StandardCharsets.UTF_8));
        String tokenAlterado = partes[0] + "." + contenidoAlterado + "." + partes[2];

        assertThat(estado("Bearer " + tokenAlterado)).isEqualTo(401);
    }

    /** HU-37: token del primer ingreso, con la misma forma que {@code generateCambioPassword} de ms-auth (claim cambiarPassword). */
    private String tokenRestringido(List<String> roles, Object cambiarPassword) {
        long ahora = System.currentTimeMillis();
        var b = Jwts.builder().id(UUID.randomUUID().toString()).subject("cliente1").claim("roles", roles);
        if (cambiarPassword != null) b.claim("cambiarPassword", cambiarPassword);
        return b.issuedAt(new Date(ahora)).expiration(new Date(ahora + 60_000)).signWith(clave()).compact();
    }

    private int estadoEn(String ruta, String autorizacion) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET()
                .header("Authorization", autorizacion).build(), BodyHandlers.ofString()).statusCode();
    }

    @Test
    void elTokenRestringidoDelPrimerIngresoSeRechazaCon403AunqueLleveUnRolDelEstudio() throws Exception {
        // sin el chequeo del claim, este token (con rol CONTADOR) entraría: la protección no depende de que roles esté vacío
        assertThat(estado("Bearer " + tokenRestringido(List.of("CONTADOR"), true))).isEqualTo(403);
        assertThat(estado("Bearer " + tokenRestringido(List.of("ADMIN"), true))).isEqualTo(403);
        assertThat(estado("Bearer " + tokenRestringido(List.of("CLIENTE_MYPE"), true))).isEqualTo(403);
        assertThat(estado("Bearer " + tokenRestringido(List.of(), true))).isEqualTo(403);
    }

    @Test
    void elTokenRestringidoTambienSeRechazaEnUnaRutaAbierta() throws Exception {
        assertThat(estadoEn("/api/tributario/health", "Bearer " + tokenRestringido(List.of(), true))).isEqualTo(403);
    }

    @Test
    void elTokenRestringidoRecibeElMensajeDeCambiarLaContrasenia() throws Exception {
        var r = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + RUTA_PROTEGIDA)).GET()
                .header("Authorization", "Bearer " + tokenRestringido(List.of("CONTADOR"), true)).build(), BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(403);
        assertThat(r.body()).contains("Debe cambiar la contraseña temporal");
    }

    @Test
    void unTokenConElClaimEnFalsoOSinElSeAcepta() throws Exception {
        assertThat(estado("Bearer " + tokenRestringido(List.of("CONTADOR"), false))).isEqualTo(200);
        assertThat(estado("Bearer " + tokenRestringido(List.of("CONTADOR"), null))).isEqualTo(200);
    }

    @Test
    void sinTokenSeRechazaCon401() throws Exception {
        assertThat(estado(null)).isEqualTo(401);
    }

    @Test
    void tokenMalformadoOEsquemaDistintoDeBearerSeRechazaCon401() throws Exception {
        assertThat(estado("Bearer esto-no-es-un-jwt")).isEqualTo(401);
        assertThat(estado("Bearer ")).isEqualTo(401);
        assertThat(estado("Basic " + tokenDeMsAuth("contador1", List.of("CONTADOR"), 60_000))).isEqualTo(401);
    }

    /** HU-36 (tarea 2): el nuevo claim jti no rompe la validacion de ms-tributario; el token sin jti tambien sigue valiendo. */
    @Test
    void tokenConJtiYTokenAnteriorSinJtiSeAceptan() throws Exception {
        assertThat(tokenDeMsAuth("contador1", List.of("CONTADOR"), 60_000)).contains(".");
        assertThat(estado("Bearer " + tokenDeMsAuth("contador1", List.of("CONTADOR"), 60_000))).isEqualTo(200);
        assertThat(estado("Bearer " + tokenAnteriorSinJti("contador1", List.of("CONTADOR")))).isEqualTo(200);
    }

    /**
     * RIESGO RESIDUAL conocido (HU-36, opcion B): ms-auth revoca el jti al cerrar sesion, pero ms-tributario
     * solo verifica firma y vencimiento y no consulta la revocacion. Esta prueba deja constancia: el token con
     * ese jti se sigue aceptando aqui hasta su exp. Si se cierra el riesgo (consulta a ms-auth u opcion D),
     * esta prueba debe cambiar a 401.
     */
    @Test
    void elTokenRevocadoEnMsAuthSigueAceptandoseEnMsTributarioHastaQueVence() throws Exception {
        String revocadoEnMsAuth = tokenDeMsAuth("contador1", List.of("CONTADOR"), 60_000);

        assertThat(estado("Bearer " + revocadoEnMsAuth)).isEqualTo(200);
    }
}
