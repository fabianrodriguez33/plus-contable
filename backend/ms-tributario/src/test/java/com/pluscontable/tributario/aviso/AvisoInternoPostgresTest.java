package com.pluscontable.tributario.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL desechable: prueba la unicidad real, la lectura y la asociación con clientes inactivos. */
@SpringBootTest
class AvisoInternoPostgresTest {
    @Autowired AvisoInternoService service;
    @Autowired JdbcTemplate jdbc;
    private final List<Long> avisos = new ArrayList<>();
    private final List<Long> clientes = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (Long id : avisos) jdbc.update("DELETE FROM tributario_schema.avisos WHERE id = ?", id);
        for (Long id : clientes) jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", id);
    }

    private long cliente(boolean activo) {
        String ruc = "20" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
        Long id = jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes(ruc, razon_social, regimen_tributario, activo)
                VALUES (?, 'Cliente de prueba', 'MYPE', ?) RETURNING id
                """, Long.class, ruc, activo);
        clientes.add(id);
        return id;
    }

    private AvisoInternoDto crear(String origen, String clave, List<Long> ids) {
        AvisoInternoDto aviso = service.crear(origen, clave, "Mensaje de prueba", ids);
        avisos.add(aviso.id());
        return aviso;
    }

    @Test
    void creaConsultaMarcaLeidoYFiltra() {
        var aviso = crear("HU-12", UUID.randomUUID().toString(), List.of());
        assertThat(aviso.creadoEn()).isNotNull();
        assertThat(aviso.leidoEn()).isNull();
        assertThat(service.buscar(aviso.id())).isEqualTo(aviso);
        assertThat(service.bandeja(false)).extracting(AvisoInternoDto::id).contains(aviso.id());
        var leido = service.marcarLeido(aviso.id());
        assertThat(leido.leidoEn()).isNotNull();
        assertThat(service.marcarLeido(aviso.id()).leidoEn()).isEqualTo(leido.leidoEn());
        assertThat(service.bandeja(false)).extracting(AvisoInternoDto::id).doesNotContain(aviso.id());
        assertThat(service.bandeja(true)).extracting(AvisoInternoDto::id).contains(aviso.id());
    }

    @Test
    void asociaVariosClientesYConservaElAvisoCuandoUnoQuedaInactivo() {
        long primero = cliente(true);
        long segundo = cliente(true);
        var aviso = crear("HU-11", UUID.randomUUID().toString(), List.of(segundo, primero, primero));
        assertThat(aviso.clienteIds()).containsExactly(primero, segundo);
        jdbc.update("UPDATE tributario_schema.clientes SET activo = false WHERE id = ?", primero);
        assertThat(service.buscar(aviso.id()).clienteIds()).containsExactly(primero, segundo);
    }

    @Test
    void reintentoIdenticoNoDuplicaYEventosDistintosSiSeCrean() {
        String clave = UUID.randomUUID().toString();
        var primero = crear("HU-12", clave, List.of());
        var repetido = service.crear("HU-12", clave, "Mensaje de prueba", List.of());
        assertThat(repetido.id()).isEqualTo(primero.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tributario_schema.avisos WHERE origen = ? AND clave_evento = ?",
                Integer.class, "HU-12", clave)).isEqualTo(1);
        var otraHistoria = crear("HU-22", clave, List.of());
        var otroEvento = crear("HU-12", clave + "-otro", List.of());
        assertThat(otraHistoria.id()).isNotEqualTo(primero.id());
        assertThat(otroEvento.id()).isNotEqualTo(primero.id());
    }

    @Test
    void claveReutilizadaConOtroContenidoSeRechaza() {
        String clave = UUID.randomUUID().toString();
        crear("HU-12", clave, List.of());
        assertThatThrownBy(() -> service.crear("HU-12", clave, "Otro mensaje", List.of()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validaDatosObligatoriosYClientesExistentes() {
        assertThatThrownBy(() -> service.crear(" ", "x", "mensaje", List.of())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.crear("HU-12", " ", "mensaje", List.of())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.crear("HU-12", "x", " ", List.of())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.crear("HU-12", "x", "mensaje", List.of(-1L))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.crear("HU-12", "x", "mensaje", List.of(Long.MAX_VALUE)))
                .isInstanceOf(ValidationException.class);
    }
}
