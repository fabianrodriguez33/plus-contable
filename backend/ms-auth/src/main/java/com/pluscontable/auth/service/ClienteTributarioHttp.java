package com.pluscontable.auth.service;

import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** HU-37: GET /api/tributario/clientes/{id} con el mismo JWT del contador (mismo secreto en los dos servicios). */
@Component
public class ClienteTributarioHttp implements ClienteTributarioClient {

    private final RestClient http;

    public ClienteTributarioHttp(@Value("${app.tributario.url:http://localhost:8082}") String url) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(3));
        f.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().baseUrl(url).requestFactory(f).build();
    }

    @Override
    public Optional<ClienteResumen> buscar(Long clienteId, String autorizacion) {
        try {
            ClienteResumen r = http.get().uri("/api/tributario/clientes/{id}", clienteId)
                    .header(HttpHeaders.AUTHORIZATION, autorizacion)
                    .retrieve().body(ClienteResumen.class);
            return Optional.ofNullable(r);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo comprobar el cliente en el servicio tributario");
        }
    }
}
