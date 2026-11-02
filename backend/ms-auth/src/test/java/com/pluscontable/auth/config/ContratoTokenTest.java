package com.pluscontable.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/**
 * HT-03 (tarea 12): contrato del token que ms-auth emite y ms-tributario consume.
 *
 * <p>ms-tributario valida con {@code Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secreto UTF-8))} y toma
 * {@code roles} y el subject (ver {@code JwtFilter}). Si cambia algo de esto, esta prueba falla en ms-auth y
 * {@code TokenEntreServiciosTest} deja de ser representativa en ms-tributario: hay que actualizar las dos.
 * El secreto es ficticio; en los servicios es el mismo valor, que viene de la variable de entorno JWT_SECRET.
 */
class ContratoTokenTest {

    private static final String SECRETO = "secreto-ficticio-de-contrato-0123456789-abcdef";

    private final JwtProvider proveedor = new JwtProvider(SECRETO, 8);
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode parte(String token, int indice) throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(token.split("\\.")[indice]);
        return json.readTree(new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void elContenidoTieneExactamenteLosClaimsQueConsumeMsTributario() throws Exception {
        String token = proveedor.generate("contador1", List.of("CONTADOR", "ADMIN"));

        JsonNode contenido = parte(token, 1);
        Set<String> nombres = Set.copyOf(StreamSupport.stream(
                java.util.Spliterators.spliteratorUnknownSize(contenido.fieldNames(), 0), false).toList());

        assertThat(nombres).containsExactlyInAnyOrder("sub", "roles", "iat", "exp");
        assertThat(contenido.get("sub").asText()).isEqualTo("contador1");
        assertThat(contenido.get("roles").isArray()).isTrue();
        assertThat(StreamSupport.stream(contenido.get("roles").spliterator(), false).map(JsonNode::asText).toList())
                .containsExactly("CONTADOR", "ADMIN");
        assertThat(contenido.get("exp").asLong()).isGreaterThan(contenido.get("iat").asLong());
    }

    @Test
    void elAlgoritmoEsHs256() throws Exception {
        String token = proveedor.generate("contador1", List.of("CONTADOR"));

        assertThat(parte(token, 0).get("alg").asText()).isEqualTo("HS256");
    }

    /** Es la misma derivación de clave que usa JwtFilter de ms-tributario. */
    @Test
    void sePuedeVerificarConLaClaveDerivadaDelSecretoComoLoHaceMsTributario() {
        String token = proveedor.generate("contador1", List.of("CONTADOR"));

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo("contador1");
        assertThat(claims.get("roles", List.class)).containsExactly("CONTADOR");
    }
}
