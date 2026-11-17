package com.pluscontable.tributario.empresa;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.model.DocumentoLegal.Tipo;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** HU-35 con PostgreSQL desechable: lo que se ve de una empresa con datos reales y sin mezclarse con otra. */
@SpringBootTest
class DatosEmpresaPostgresTest {
    @Autowired DatosEmpresaService service;
    @Autowired JdbcTemplate jdbc;
    private final List<Long> clientes = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (Long id : clientes) jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", id);
    }

    private long cliente(String direccion) {
        String ruc = "20" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
        Long id = jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes(ruc, razon_social, regimen_tributario, direccion, activo)
                VALUES (?, 'Cliente de prueba', 'RER', ?, true) RETURNING id
                """, Long.class, ruc, direccion);
        clientes.add(id);
        return id;
    }

    private void socio(long clienteId, String nombre, String porcentaje) {
        jdbc.update("""
                INSERT INTO tributario_schema.socios(cliente_id, nombre_completo, numero_documento, aporte, porcentaje)
                VALUES (?, ?, '12345678', 1000, ?::numeric)
                """, clienteId, nombre, porcentaje);
    }

    private void documento(long clienteId, String tipo, int version) {
        jdbc.update("""
                INSERT INTO tributario_schema.documentos_legales(cliente_id, tipo, version, fecha_emision, nombre_archivo,
                    content_type, tamano_bytes, ruta_almacenamiento)
                VALUES (?, ?, ?, '2026-10-01', 'a.pdf', 'application/pdf', 10, 'x.pdf')
                """, clienteId, tipo, version);
    }

    @Test
    void muestraRegimenSociosYLaVersionVigenteDeCadaDocumento() {
        long c = cliente("Av. Lima 123");
        socio(c, "Ana Perez", "60.00");
        socio(c, "Luis Diaz", "40.00");
        documento(c, "FICHA_RUC", 1);
        documento(c, "FICHA_RUC", 2);

        var datos = service.obtener(c);

        assertThat(datos.regimenTributario()).isEqualTo("RER");
        assertThat(datos.direccion()).isEqualTo("Av. Lima 123");
        assertThat(datos.socios()).extracting(DatosEmpresaDto.SocioVista::nombreCompleto).containsExactly("Ana Perez", "Luis Diaz");
        assertThat(datos.documentos()).filteredOn(d -> d.tipo() == Tipo.FICHA_RUC).singleElement()
                .satisfies(d -> assertThat(d.version()).isEqualTo(2));
        assertThat(datos.documentos()).filteredOn(d -> d.tipo() == Tipo.MINUTA).singleElement()
                .satisfies(d -> assertThat(d.id()).isNull());
    }

    @Test
    void unaEmpresaNoVeLosSociosNiDocumentosDeOtra() {
        long a = cliente(null);
        long b = cliente(null);
        socio(a, "Ana Perez", "100.00");
        documento(a, "MINUTA", 1);

        var deB = service.obtener(b);

        assertThat(deB.direccion()).isNull();
        assertThat(deB.socios()).isEmpty();
        assertThat(deB.documentos()).allSatisfy(d -> assertThat(d.id()).isNull());
    }
}
