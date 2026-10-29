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
}
