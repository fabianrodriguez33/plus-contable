package com.pluscontable.tributario.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.controller.AcuerdoController;
import com.pluscontable.tributario.controller.DocumentoController;
import com.pluscontable.tributario.controller.ObligacionController;
import com.pluscontable.tributario.controller.ProgramacionMensualController;
import com.pluscontable.tributario.controller.TrabajadorController;
import com.pluscontable.tributario.model.DocumentoLegal;
import com.pluscontable.tributario.dto.ObligacionDto;
import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import com.pluscontable.tributario.service.AcuerdoService;
import com.pluscontable.tributario.ruc.ConsultaRucController;
import com.pluscontable.tributario.ruc.ConsultaRucService;
import com.pluscontable.tributario.service.ExpedienteService;
import com.pluscontable.tributario.service.ObligacionProgramacionService;
import com.pluscontable.tributario.service.ObligacionService;
import com.pluscontable.tributario.service.ProgramacionMensualService;
import com.pluscontable.tributario.service.TrabajadorService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** HU-38 · Tarea 84: cada rol contra cada opción de la matriz (con JWT reales firmados como ms-auth). */
@WebMvcTest(controllers = {TrabajadorController.class, DocumentoController.class, AcuerdoController.class,
        ObligacionController.class, ConsultaRucController.class, ProgramacionMensualController.class})
@Import(SecurityConfig.class)
class PermisosPorRolTest {

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;

    @MockBean TrabajadorService trabajadorService;
    @MockBean ExpedienteService expedienteService;
    @MockBean AcuerdoService acuerdoService;
    @MockBean ObligacionService obligacionService;
    @MockBean ObligacionProgramacionService programacionService;
    @MockBean ConsultaRucService consultaRucService;
    @MockBean ProgramacionMensualService programacionMensualService;

    @BeforeEach
    void setUp() {
        DocumentoLegal d = new DocumentoLegal();
        d.setNombreArchivo("ficha.pdf");
        when(expedienteService.buscar(anyLong(), anyLong())).thenReturn(d);
        when(expedienteService.cargar(any())).thenReturn(new ByteArrayResource("%PDF-".getBytes()));
        when(programacionService.crear(any())).thenReturn(new ObligacionDto(1L, 1L, TipoObligacion.IGV,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 16), Obligacion.Estado.PENDIENTE,
                null, null, null));
        when(obligacionService.buscar(anyLong())).thenReturn(obligacionDe(1L));
    }

    private static ObligacionDto obligacionDe(long clienteId) {
        return new ObligacionDto(3L, clienteId, TipoObligacion.IGV, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 16), Obligacion.Estado.PENDIENTE, null, null, null);
    }

    /** Opción de la matriz: método, ruta y si el cliente MYPE puede usarla (el estudio puede todas). */
    static Stream<Arguments> matriz() {
        return Stream.of(
                Arguments.of("GET", "/api/tributario/clientes/1/trabajadores", true),
                Arguments.of("POST", "/api/tributario/clientes/1/trabajadores", false),
                Arguments.of("GET", "/api/tributario/clientes/1/documentos", true),
                Arguments.of("GET", "/api/tributario/clientes/1/documentos/5/archivo", true),
                Arguments.of("POST", "/api/tributario/clientes/1/documentos", false),
                Arguments.of("GET", "/api/tributario/clientes/1/acuerdo", true),
                Arguments.of("PUT", "/api/tributario/clientes/1/acuerdo", false),
                Arguments.of("GET", "/api/tributario/obligaciones?clienteId=1", true),
                Arguments.of("GET", "/api/tributario/obligaciones/3", true),
                Arguments.of("POST", "/api/tributario/obligaciones", false),
                Arguments.of("POST", "/api/tributario/obligaciones/programacion", false),
                Arguments.of("PATCH", "/api/tributario/obligaciones/3/declaracion", false),
                Arguments.of("PATCH", "/api/tributario/obligaciones/vencimiento", false),
                Arguments.of("GET", "/api/tributario/ruc/20123456786/consulta", false));
    }

    private String token(List<String> roles, long msHastaExpirar) {
        return token(roles, msHastaExpirar, null);
    }

    /** Como ms-auth: el claim clienteId solo va en el token de una cuenta de cliente. */
    private String token(List<String> roles, long msHastaExpirar, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var b = Jwts.builder().subject("usuario").claim("roles", roles);
        if (clienteId != null) b.claim("clienteId", clienteId);
        return b.expiration(new Date(System.currentTimeMillis() + msHastaExpirar)).signWith(key).compact();
    }

    private int estado(String metodo, String ruta, String token) throws Exception {
        MockHttpServletRequestBuilder r = request(HttpMethod.valueOf(metodo), ruta)
                .contentType(MediaType.APPLICATION_JSON).content("{}");
        if (token != null) r.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mvc.perform(r).andReturn().getResponse().getStatus();
    }

    @ParameterizedTest(name = "CONTADOR {0} {1}")
    @MethodSource("matriz")
    void contadorAccedeATodasLasOpcionesDeLaMatriz(String metodo, String ruta, boolean ignorado) throws Exception {
        // pasa la seguridad: lo que responda después (200, 400, 415…) ya es del controlador
        assertThat(estado(metodo, ruta, token(List.of("CONTADOR"), 60_000))).isNotIn(401, 403);
    }

    @ParameterizedTest(name = "ADMIN {0} {1}")
    @MethodSource("matriz")
    void adminTieneLosPermisosDelContador(String metodo, String ruta, boolean ignorado) throws Exception {
        assertThat(estado(metodo, ruta, token(List.of("ADMIN"), 60_000))).isNotIn(401, 403);
    }

    @ParameterizedTest(name = "CLIENTE_MYPE {0} {1} permitido={2}")
    @MethodSource("matriz")
    void clienteMypeNoEntraALasOpcionesDelContador(String metodo, String ruta, boolean permitido) throws Exception {
        // las rutas de la matriz son del cliente 1: una cuenta de ese cliente entra a las suyas
        int s = estado(metodo, ruta, token(List.of("CLIENTE_MYPE"), 60_000, 1L));
        if (permitido) assertThat(s).isNotIn(401, 403);
        else assertThat(s).isEqualTo(403);
    }

    @ParameterizedTest(name = "sin rol {0} {1}")
    @MethodSource("matriz")
    void tokenSinRolNoAccedeANada(String metodo, String ruta, boolean ignorado) throws Exception {
        assertThat(estado(metodo, ruta, token(List.of(), 60_000))).isEqualTo(403);
    }

    @ParameterizedTest(name = "sin token {0} {1}")
    @MethodSource("matriz")
    void sinTokenResponde401(String metodo, String ruta, boolean ignorado) throws Exception {
        assertThat(estado(metodo, ruta, null)).isEqualTo(401);
    }

    @Test
    void tokenVencidoOFalsificadoResponde401() throws Exception {
        String ruta = "/api/tributario/clientes/1/trabajadores";
        assertThat(estado("GET", ruta, token(List.of("CONTADOR"), -1_000))).isEqualTo(401);

        String falso = Jwts.builder().subject("x").claim("roles", List.of("CONTADOR"))
                .signWith(Keys.hmacShaKeyFor("otra-clave-que-no-es-la-del-estudio-32b".getBytes())).compact();
        assertThat(estado("GET", ruta, falso)).isEqualTo(401);
    }

    @Test
    void altaDeObligacionRespetaRolesExistentes() throws Exception {
        String ruta = "/api/tributario/obligaciones";
        assertThat(estado("POST", ruta, token(List.of("CONTADOR"), 60_000))).isEqualTo(200);
        assertThat(estado("POST", ruta, token(List.of("ADMIN"), 60_000))).isEqualTo(200);
        assertThat(estado("POST", ruta, token(List.of("CLIENTE_MYPE"), 60_000))).isEqualTo(403);
        assertThat(estado("POST", ruta, token(List.of(), 60_000))).isEqualTo(403);
        assertThat(estado("POST", ruta, null)).isEqualTo(401);
    }

    /** Las seis rutas de lectura que el cliente MYPE puede usar; se prueban con el cliente 1 y el 2. */
    static Stream<String> rutasDelCliente() {
        return Stream.of(
                "/api/tributario/clientes/%d/trabajadores",
                "/api/tributario/clientes/%d/documentos",
                "/api/tributario/clientes/%d/documentos/5/archivo",
                "/api/tributario/clientes/%d/acuerdo",
                "/api/tributario/obligaciones?clienteId=%d");
    }

    @ParameterizedTest(name = "cliente 1 en {0}")
    @MethodSource("rutasDelCliente")
    void clienteNuncaVeDatosDeOtroCliente(String plantilla) throws Exception {
        String token = token(List.of("CLIENTE_MYPE"), 60_000, 1L);
        assertThat(estado("GET", plantilla.formatted(1), token)).isNotIn(401, 403);
        assertThat(estado("GET", plantilla.formatted(2), token)).isEqualTo(403);
    }

    @ParameterizedTest(name = "cliente 1000 en {0}")
    @MethodSource("rutasDelCliente")
    void clienteConIdMayorA127SoloVeSuEmpresa(String plantilla) throws Exception {
        String token = token(List.of("CLIENTE_MYPE"), 60_000, 1000L);
        assertThat(estado("GET", plantilla.formatted(1000), token)).isNotIn(401, 403);
        assertThat(estado("GET", plantilla.formatted(1001), token)).isEqualTo(403);
    }

    @Test
    void clienteConIdMayorA127SoloVeLasObligacionesDeSuEmpresa() throws Exception {
        String token = token(List.of("CLIENTE_MYPE"), 60_000, 1000L);
        when(obligacionService.buscar(3L)).thenReturn(obligacionDe(1000L));
        assertThat(estado("GET", "/api/tributario/obligaciones/3", token)).isNotIn(401, 403);
        when(obligacionService.buscar(3L)).thenReturn(obligacionDe(1001L));
        assertThat(estado("GET", "/api/tributario/obligaciones/3", token)).isEqualTo(403);
    }

    @Test
    void clienteNuncaVeUnaObligacionDeOtroCliente() throws Exception {
        String token = token(List.of("CLIENTE_MYPE"), 60_000, 1L);
        when(obligacionService.buscar(3L)).thenReturn(obligacionDe(1L));
        assertThat(estado("GET", "/api/tributario/obligaciones/3", token)).isNotIn(401, 403);
        when(obligacionService.buscar(3L)).thenReturn(obligacionDe(2L));
        assertThat(estado("GET", "/api/tributario/obligaciones/3", token)).isEqualTo(403);
    }

    @ParameterizedTest(name = "sin claim clienteId en {0}")
    @MethodSource("rutasDelCliente")
    void clienteSinClaimNoVeNingunCliente(String plantilla) throws Exception {
        assertThat(estado("GET", plantilla.formatted(1), token(List.of("CLIENTE_MYPE"), 60_000))).isEqualTo(403);
    }

    @Test
    void clienteSinClaimNoVeObligaciones() throws Exception {
        assertThat(estado("GET", "/api/tributario/obligaciones/3", token(List.of("CLIENTE_MYPE"), 60_000))).isEqualTo(403);
    }

    @Test
    void elEstudioSigueViendoTodosLosClientes() throws Exception {
        for (String rol : List.of("CONTADOR", "ADMIN")) {
            String token = token(List.of(rol), 60_000);
            assertThat(estado("GET", "/api/tributario/clientes/2/trabajadores", token)).isNotIn(401, 403);
            when(obligacionService.buscar(3L)).thenReturn(obligacionDe(2L));
            assertThat(estado("GET", "/api/tributario/obligaciones/3", token)).isNotIn(401, 403);
        }
    }
}
