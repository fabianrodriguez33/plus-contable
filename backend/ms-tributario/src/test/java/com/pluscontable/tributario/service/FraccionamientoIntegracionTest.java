package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.dto.CalendarioCuotaDto;
import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.dto.CuotaDto;
import com.pluscontable.tributario.dto.FraccionamientoDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.EstadoCuota;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-17 · Tarea 25: el fraccionamiento y sus cuotas de punta a punta (HTTP real, seguridad real y PostgreSQL de
 * pruebas): se registra por la API, se lee el listado y el calendario por la API, se paga una cuota y se comprueba
 * en las tablas. Usa clientes ficticios que se borran al terminar (las cuotas se borran en cascada). Solo debe
 * correr contra la base de pruebas indicada por el POM.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FraccionamientoIntegracionTest {

    private static final String BASE = "/api/tributario";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.jwt.secret}") String secreto;

    private final List<String> rucsCreados = new ArrayList<>();

    @BeforeEach
    void permitirPatch() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @AfterEach
    void borrarClientesFicticios() {
        for (String ruc : rucsCreados) {
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
        }
    }

    private HttpHeaders cabeceras(String rol) {
        HttpHeaders h = new HttpHeaders();
        if (rol != null) {
            var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
            h.setBearerAuth(Jwts.builder().subject("usuario1").claim("roles", List.of(rol))
                    .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(key).compact());
        }
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private long crearCliente() {
        String ruc = RucFicticio.nuevo();
        rucsCreados.add(ruc);
        var dto = new ClienteDto(null, ruc, "Cliente ficticio (fraccionamiento)", Cliente.Regimen.MYPE,
                Cliente.CondicionHabido.HABIDO, null, null, null, null, null, null);
        ResponseEntity<ClienteDto> r = http.exchange(BASE + "/clientes", HttpMethod.POST,
                new HttpEntity<>(dto, cabeceras("CONTADOR")), ClienteDto.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().id();
    }

    private static Map<String, Object> cuerpo(String tributo, Object monto, Object cuotas, Object primera) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (tributo != null) m.put("tributo", tributo);
        if (monto != null) m.put("montoTotal", monto);
        if (cuotas != null) m.put("numeroCuotas", cuotas);
        if (primera != null) m.put("fechaPrimeraCuota", primera.toString());
        return m;
    }

    private ResponseEntity<FraccionamientoDto> registrar(long cliente, Map<String, Object> cuerpo) {
        return http.exchange(BASE + "/clientes/" + cliente + "/fraccionamientos", HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras("CONTADOR")), FraccionamientoDto.class);
    }

    private List<CalendarioCuotaDto> calendario(long cliente, String consulta) {
        ResponseEntity<List<CalendarioCuotaDto>> r = http.exchange(
                BASE + "/clientes/" + cliente + "/fraccionamientos/calendario" + consulta, HttpMethod.GET,
                new HttpEntity<>(cabeceras("CONTADOR")), new ParameterizedTypeReference<List<CalendarioCuotaDto>>() { });
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private int contar(String tabla, String donde, Object... args) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema." + tabla + " WHERE " + donde, Integer.class, args);
    }

    @Test
    void registrarGuardaElFraccionamientoYGeneraLasCuotasEnLasTablas() {
        long cliente = crearCliente();
        LocalDate primera = LocalDate.now().plusDays(10);

        ResponseEntity<FraccionamientoDto> r = registrar(cliente, cuerpo("IGV", "1000.00", 3, primera));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        FraccionamientoDto f = r.getBody();
        assertThat(f.id()).isNotNull();
        assertThat(f.clienteId()).isEqualTo(cliente);
        assertThat(f.cuotas()).extracting(CuotaDto::numero).containsExactly(1, 2, 3);
        assertThat(f.cuotas()).extracting(CuotaDto::fechaVencimiento)
                .containsExactly(primera, primera.plusMonths(1), primera.plusMonths(2));
        assertThat(f.cuotas().stream().map(CuotaDto::monto).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("1000.00");
        assertThat(f.cuotas()).extracting(CuotaDto::estado).containsOnly(EstadoCuota.PENDIENTE);

        assertThat(contar("fraccionamientos", "cliente_id = ? AND tributo = 'IGV' AND numero_cuotas = 3", cliente)).isEqualTo(1);
        assertThat(contar("cuotas_fraccionamiento", "fraccionamiento_id = ?", f.id())).isEqualTo(3);
    }

    @Test
    void siFaltanMontoCuotasOFechaNoSeGuardaNada() {
        long cliente = crearCliente();
        LocalDate primera = LocalDate.now().plusDays(10);

        for (Map<String, Object> incompleto : List.of(
                cuerpo("IGV", null, 3, primera),
                cuerpo("IGV", "1000.00", null, primera),
                cuerpo("IGV", "1000.00", 3, null),
                cuerpo(null, "1000.00", 3, primera),
                cuerpo("IGV", "0", 3, primera),
                cuerpo("IGV", "1000.00", 1, primera))) {
            ResponseEntity<Map<String, String>> r = http.exchange(BASE + "/clientes/" + cliente + "/fraccionamientos",
                    HttpMethod.POST, new HttpEntity<>(incompleto, cabeceras("CONTADOR")),
                    new ParameterizedTypeReference<Map<String, String>>() { });
            assertThat(r.getStatusCode()).as(incompleto.toString()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(r.getBody().get("mensaje")).startsWith("Faltan o son inválidos");
        }

        assertThat(contar("fraccionamientos", "cliente_id = ?", cliente)).isZero();
    }

    @Test
    void elListadoDevuelveLosFraccionamientosDelClienteConSusCuotas() {
        long cliente = crearCliente();
        long otro = crearCliente();
        registrar(cliente, cuerpo("IGV", "600.00", 2, LocalDate.now().plusDays(5)));
        registrar(cliente, cuerpo("RENTA", "900.00", 3, LocalDate.now().plusDays(7)));
        registrar(otro, cuerpo("AFP", "300.00", 2, LocalDate.now().plusDays(7)));

        ResponseEntity<List<FraccionamientoDto>> r = http.exchange(BASE + "/clientes/" + cliente + "/fraccionamientos",
                HttpMethod.GET, new HttpEntity<>(cabeceras("CONTADOR")),
                new ParameterizedTypeReference<List<FraccionamientoDto>>() { });

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).extracting(FraccionamientoDto::numeroCuotas).containsExactlyInAnyOrder(2, 3);
        assertThat(r.getBody()).allSatisfy(f -> assertThat(f.cuotas()).hasSize(f.numeroCuotas()));
    }

    @Test
    void elCalendarioMarcaEnRiesgoLaCuotaPendienteVencidaYPagarlaLaQuitaDelRiesgo() {
        long cliente = crearCliente();
        // Primera cuota hace 40 días (vencida), la segunda hace unos 10 días (vencida) y la tercera en el futuro.
        LocalDate primera = LocalDate.now().minusDays(40);
        FraccionamientoDto f = registrar(cliente, cuerpo("PLAME", "900.00", 3, primera)).getBody();

        List<CalendarioCuotaDto> antes = calendario(cliente, "");
        assertThat(antes).hasSize(3);
        assertThat(antes).extracting(c -> c.cuota().numero()).containsExactly(1, 2, 3);
        assertThat(antes).extracting(c -> c.cuota().enRiesgo()).containsExactly(true, true, false);
        assertThat(antes).extracting(CalendarioCuotaDto::vencida).containsExactly(true, true, false);
        assertThat(antes.get(0).diasRestantes()).isEqualTo(-40);
        assertThat(antes).extracting(CalendarioCuotaDto::fraccionamientoId).containsOnly(f.id());

        long cuota1 = f.cuotas().get(0).id();
        ResponseEntity<CuotaDto> pago = http.exchange(BASE + "/cuotas/" + cuota1 + "/pago", HttpMethod.PATCH,
                new HttpEntity<>(cabeceras("CONTADOR")), CuotaDto.class);
        assertThat(pago.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pago.getBody().estado()).isEqualTo(EstadoCuota.PAGADA);
        assertThat(pago.getBody().fechaPago()).isEqualTo(LocalDate.now());
        assertThat(pago.getBody().enRiesgo()).isFalse();
        assertThat(contar("cuotas_fraccionamiento", "id = ? AND estado = 'PAGADA' AND fecha_pago = CURRENT_DATE", cuota1)).isEqualTo(1);

        assertThat(calendario(cliente, "")).extracting(c -> c.cuota().enRiesgo()).containsExactly(false, true, false);

        ResponseEntity<Map<String, String>> repetido = http.exchange(BASE + "/cuotas/" + cuota1 + "/pago",
                HttpMethod.PATCH, new HttpEntity<>(cabeceras("CONTADOR")),
                new ParameterizedTypeReference<Map<String, String>>() { });
        assertThat(repetido.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(repetido.getBody().get("mensaje")).isEqualTo("La cuota ya fue pagada");
    }

    @Test
    void elCalendarioSePuedeFiltrarPorFechasEstadoYSituacion() {
        long cliente = crearCliente();
        LocalDate primera = LocalDate.now().minusDays(40);
        FraccionamientoDto f = registrar(cliente, cuerpo("ESSALUD", "900.00", 3, primera)).getBody();
        http.exchange(BASE + "/cuotas/" + f.cuotas().get(0).id() + "/pago", HttpMethod.PATCH,
                new HttpEntity<>(cabeceras("CONTADOR")), CuotaDto.class);

        assertThat(calendario(cliente, "?estado=PAGADA")).extracting(c -> c.cuota().numero()).containsExactly(1);
        assertThat(calendario(cliente, "?estado=PENDIENTE")).extracting(c -> c.cuota().numero()).containsExactly(2, 3);
        assertThat(calendario(cliente, "?situacion=VENCIDO")).extracting(c -> c.cuota().numero()).containsExactly(1, 2);
        assertThat(calendario(cliente, "?estado=PENDIENTE&situacion=VENCIDO")).extracting(c -> c.cuota().numero()).containsExactly(2);
        assertThat(calendario(cliente, "?situacion=PROXIMO")).extracting(c -> c.cuota().numero()).containsExactly(3);
        assertThat(calendario(cliente, "?desde=" + primera + "&hasta=" + primera)).extracting(c -> c.cuota().numero()).containsExactly(1);
    }

    @Test
    void lasConsultasInvalidasYLosRecursosInexistentesDanElErrorCorrecto() {
        long cliente = crearCliente();
        HttpEntity<Void> contador = new HttpEntity<>(cabeceras("CONTADOR"));
        var mapa = new ParameterizedTypeReference<Map<String, String>>() { };

        ResponseEntity<Map<String, String>> fechas = http.exchange(
                BASE + "/clientes/" + cliente + "/fraccionamientos/calendario?desde=2026-12-01&hasta=2026-11-01",
                HttpMethod.GET, contador, mapa);
        assertThat(fechas.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fechas.getBody().get("mensaje")).isEqualTo("desde no puede superar hasta");

        assertThat(http.exchange(BASE + "/clientes/" + cliente + "/fraccionamientos/calendario?estado=ANULADA",
                HttpMethod.GET, contador, mapa).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(http.exchange(BASE + "/clientes/999999999/fraccionamientos", HttpMethod.GET, contador, mapa)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(registrar(999999999L, cuerpo("IGV", "100.00", 2, LocalDate.now())).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map<String, String>> sinCuota = http.exchange(BASE + "/cuotas/999999999/pago",
                HttpMethod.PATCH, contador, mapa);
        assertThat(sinCuota.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(sinCuota.getBody().get("mensaje")).isEqualTo("Cuota no encontrada");
    }

    @Test
    void sinSesionDa401YElClienteMypeNoPuedeUsarlo() {
        long cliente = crearCliente();
        String url = BASE + "/clientes/" + cliente + "/fraccionamientos";

        assertThat(http.exchange(url, HttpMethod.GET, new HttpEntity<>(cabeceras(null)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.exchange(url, HttpMethod.POST,
                new HttpEntity<>(cuerpo("IGV", "100.00", 2, LocalDate.now()), cabeceras(null)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpEntity<Object> mype = new HttpEntity<>(cuerpo("IGV", "100.00", 2, LocalDate.now()), cabeceras("CLIENTE_MYPE"));
        assertThat(http.exchange(url, HttpMethod.POST, mype, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.exchange(url, HttpMethod.GET, new HttpEntity<>(cabeceras("CLIENTE_MYPE")), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.exchange(url + "/calendario", HttpMethod.GET, new HttpEntity<>(cabeceras("CLIENTE_MYPE")), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.exchange(BASE + "/cuotas/1/pago", HttpMethod.PATCH, new HttpEntity<>(cabeceras("CLIENTE_MYPE")), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(contar("fraccionamientos", "cliente_id = ?", cliente)).isZero();
    }

    @Test
    void borrarAlClienteBorraSusFraccionamientosYCuotas() {
        long cliente = crearCliente();
        FraccionamientoDto f = registrar(cliente, cuerpo("RETENCION_QUINTA", "500.00", 2, LocalDate.now().plusDays(3))).getBody();
        assertThat(contar("cuotas_fraccionamiento", "fraccionamiento_id = ?", f.id())).isEqualTo(2);

        jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", cliente);

        assertThat(contar("fraccionamientos", "id = ?", f.id())).isZero();
        assertThat(contar("cuotas_fraccionamiento", "fraccionamiento_id = ?", f.id())).isZero();
    }
}
