package com.pluscontable.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

/** HT-03: el token vigente se acepta; el vencido, el alterado, el de otra clave y el malformado se rechazan. */
class JwtProviderTest {

    private static final String SECRETO = "secreto-de-prueba-con-al-menos-32-bytes-ok";

    private final JwtProvider proveedor = new JwtProvider(SECRETO, 8);

    @Test
    void tokenVigenteTraeUsuarioRolesYVencimiento() {
        String token = proveedor.generate("contador1", List.of("CONTADOR", "ADMIN"));

        Claims claims = proveedor.parse(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("contador1");
        assertThat(claims.get("roles", List.class)).containsExactly("CONTADOR", "ADMIN");
        long horas = Duration.between(new Date().toInstant(), claims.getExpiration().toInstant()).toHours();
        assertThat(horas).isBetween(7L, 8L);
    }

    @Test
    void laDuracionConfiguradaEsLaQueSeExpone() {
        assertThat(proveedor.getExpiration()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void elTokenRestringidoDelPrimerIngresoDuraQuinceMinutosPorDefectoYElNormalSigueEnOchoHoras() {
        Claims restringido = proveedor.parse(proveedor.generateCambioPassword("cliente1"));

        long minutos = Duration.between(new Date().toInstant(), restringido.getExpiration().toInstant()).toMinutes();
        assertThat(minutos).isBetween(14L, 15L);
        assertThat(proveedor.getCambioPasswordExpiration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(proveedor.getExpiration()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void laVigenciaDelTokenRestringidoSeConfiguraAparteDeLaDeLaSesion() {
        JwtProvider corto = new JwtProvider(SECRETO, 8, 5);

        long minutos = Duration.between(new Date().toInstant(), corto.parse(corto.generateCambioPassword("c")).getExpiration().toInstant()).toMinutes();
        assertThat(minutos).isBetween(4L, 5L);
        assertThat(corto.getCambioPasswordExpiration()).isEqualTo(Duration.ofMinutes(5));
        long horas = Duration.between(new Date().toInstant(), corto.parse(corto.generate("c", List.of("CONTADOR"))).getExpiration().toInstant()).toHours();
        assertThat(horas).isBetween(7L, 8L);
    }

    @Test
    void tokenVencidoSeRechaza() {
        JwtProvider yaVencido = new JwtProvider(SECRETO, -1);

        assertThat(proveedor.parse(yaVencido.generate("u", List.of("CONTADOR")))).isNull();
    }

    @Test
    void tokenConElContenidoAlteradoSeRechaza() {
        String[] partes = proveedor.generate("u", List.of("CONTADOR")).split("\\.");
        // se cambia un carácter del payload sin volver a firmar
        char c = partes[1].charAt(5);
        String payload = partes[1].substring(0, 5) + (c == 'A' ? 'B' : 'A') + partes[1].substring(6);

        assertThat(proveedor.parse(partes[0] + "." + payload + "." + partes[2])).isNull();
    }

    @Test
    void tokenFirmadoConOtraClaveSeRechaza() {
        JwtProvider ajeno = new JwtProvider("otra-clave-distinta-de-al-menos-32-bytes!!", 8);

        assertThat(proveedor.parse(ajeno.generate("u", List.of("ADMIN")))).isNull();
    }

    @Test
    void tokenAusenteOMalformadoSeRechaza() {
        assertThat(proveedor.parse(null)).isNull();
        assertThat(proveedor.parse("")).isNull();
        assertThat(proveedor.parse("no-es-un-jwt")).isNull();
        assertThat(proveedor.parse("a.b.c")).isNull();
    }

    @Test
    void tokenDeUnaCuentaDeClienteLlevaElClaimClienteId() {
        Claims claims = proveedor.parse(proveedor.generate("cliente1", List.of("CLIENTE_MYPE"), 7L));

        assertThat(claims.get("clienteId", Number.class).longValue()).isEqualTo(7L);
    }

    @Test
    void tokenSinClienteNoLlevaElClaimClienteId() {
        assertThat(proveedor.parse(proveedor.generate("contador1", List.of("CONTADOR"), null)).containsKey("clienteId")).isFalse();
        assertThat(proveedor.parse(proveedor.generate("contador1", List.of("CONTADOR"))).containsKey("clienteId")).isFalse();
    }

    /** HU-36 (tarea 2): el jti identifica al token para poder revocarlo al cerrar sesion. */
    @Test
    void cadaTokenLlevaUnJtiUnicoYNoVacio() {
        String a = proveedor.parse(proveedor.generate("contador1", List.of("CONTADOR"))).getId();
        String b = proveedor.parse(proveedor.generate("contador1", List.of("CONTADOR"))).getId();
        String c = proveedor.parse(proveedor.generate("cliente1", List.of("CLIENTE_MYPE"), 7L)).getId();

        assertThat(a).isNotBlank().hasSize(36);
        assertThat(java.util.Set.of(a, b, c)).hasSize(3);
    }
}
