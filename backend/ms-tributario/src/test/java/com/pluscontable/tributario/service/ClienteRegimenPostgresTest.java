package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.Cliente.CondicionHabido;
import com.pluscontable.tributario.model.Cliente.Regimen;
import com.pluscontable.tributario.repository.ClienteRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Régimen tributario (RER, decisión de Beto del 10/11): la lista de regímenes, que la condición (habido o no habido) es un dato
 * distinto del régimen y la actualización de una base anterior que tenía clientes en REMIPE. Prueba de integración con
 * PostgreSQL real; usa clientes ficticios que se borran al terminar. Solo debe correr contra la base de pruebas indicada por el POM.
 */
@SpringBootTest
class ClienteRegimenPostgresTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired ClienteRepository clientes;
    @Autowired ClienteService servicio;

    private final List<String> rucsCreados = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (String ruc : rucsCreados) {
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
        }
    }

    private long insertar(String regimen, String condicion, String razonSocial) {
        String ruc = RucFicticio.nuevo();
        rucsCreados.add(ruc);
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario, condicion_habido, direccion) VALUES (?, ?, ?, ?, 'Av. Prueba 123')",
                ruc, razonSocial, regimen, condicion);
        return jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
    }

    private String regimenGuardado(long id) {
        return jdbc.queryForObject("SELECT regimen_tributario FROM tributario_schema.clientes WHERE id = ?", String.class, id);
    }

    private String condicionGuardada(long id) {
        return jdbc.queryForObject("SELECT condicion_habido FROM tributario_schema.clientes WHERE id = ?", String.class, id);
    }

    private void volverACorrerElEsquema() throws Exception {
        try (java.sql.Connection c = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("schema.sql"));
        }
    }

    @Test
    void losRegimenesSonMypeRerYGeneralYRemipeYaNoExiste() {
        assertThat(Arrays.stream(Regimen.values()).map(Enum::name)).containsExactly("MYPE", "RER", "GENERAL");
        assertThat(Arrays.stream(Regimen.values()).map(Enum::name)).doesNotContain("REMIPE");
    }

    @Test
    void laCondicionEsOtroDatoConSusPropiosValores() {
        assertThat(Arrays.stream(CondicionHabido.values()).map(Enum::name)).containsExactly("HABIDO", "NO_HABIDO");
        assertThat(Arrays.stream(CondicionHabido.values()).map(Enum::name)).doesNotContain("MYPE", "RER", "GENERAL");
        assertThat(Arrays.stream(Regimen.values()).map(Enum::name)).doesNotContain("HABIDO", "NO_HABIDO");
    }

    @Test
    void laTablaGuardaRerPeroRechazaRemipeYCualquierOtroValor() {
        insertar("RER", "HABIDO", "Con RER");
        assertThatThrownBy(() -> insertar("REMIPE", "HABIDO", "Con REMIPE")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar("INVENTADO", "HABIDO", "Inventado")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar("RER", "RER", "Condición con un régimen")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar("HABIDO", "HABIDO", "Régimen con una condición")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cadaRegimenSeCombinaConCadaCondicionYSeLeeIgualPorJpa() {
        for (Regimen r : Regimen.values()) {
            for (CondicionHabido c : CondicionHabido.values()) {
                long id = insertar(r.name(), c.name(), "Cliente " + r + " " + c);
                Cliente leido = clientes.findById(id).orElseThrow();
                assertThat(leido.getRegimenTributario()).as("régimen de " + r + "/" + c).isEqualTo(r);
                assertThat(leido.getCondicionHabido()).as("condición de " + r + "/" + c).isEqualTo(c);
            }
        }
    }

    @Test
    void cambiarElRegimenNoCambiaLaCondicionYCambiarLaCondicionNoCambiaElRegimen() {
        long id = insertar("MYPE", "NO_HABIDO", "Cliente independiente");
        ClienteDto base = servicio.obtener(id);

        servicio.actualizar(id, new ClienteDto(id, base.ruc(), base.razonSocial(), Regimen.RER, CondicionHabido.NO_HABIDO,
                base.direccion(), null, null, null, null, null));
        assertThat(regimenGuardado(id)).isEqualTo("RER");
        assertThat(condicionGuardada(id)).isEqualTo("NO_HABIDO");

        servicio.actualizar(id, new ClienteDto(id, base.ruc(), base.razonSocial(), Regimen.RER, CondicionHabido.HABIDO,
                base.direccion(), null, null, null, null, null));
        assertThat(regimenGuardado(id)).isEqualTo("RER");
        assertThat(condicionGuardada(id)).isEqualTo("HABIDO");
    }

    @Test
    void sinCondicionElClienteQuedaHabidoYElRegimenSigueSiendoElIndicado() {
        String ruc = RucFicticio.nuevo();
        rucsCreados.add(ruc);

        ClienteDto creado = servicio.crear(new ClienteDto(null, ruc, "Sin condición", Regimen.RER, null, null, null, null, null, null, null));

        assertThat(creado.regimenTributario()).isEqualTo(Regimen.RER);
        assertThat(creado.condicionHabido()).isEqualTo(CondicionHabido.HABIDO);
    }

    /**
     * Una base creada antes de este cambio tiene clientes en REMIPE y la restricción vieja: al arrancar, schema.sql debe pasar esos
     * clientes a RER sin perder ningún otro dato y dejar la restricción nueva. Los clientes de MYPE y GENERAL y la condición no cambian.
     */
    @Test
    void unaBaseAnteriorPasaSusClientesDeRemipeARerSinPerderDatosYVuelveACorrerSinCambios() throws Exception {
        long remipe = insertar("MYPE", "NO_HABIDO", "Antes REMIPE");
        long mype = insertar("MYPE", "HABIDO", "Sigue MYPE");
        long general = insertar("GENERAL", "NO_HABIDO", "Sigue GENERAL");
        try {
            jdbc.execute("ALTER TABLE tributario_schema.clientes DROP CONSTRAINT IF EXISTS ck_clientes_regimen");
            jdbc.execute("ALTER TABLE tributario_schema.clientes ADD CONSTRAINT clientes_regimen_tributario_check "
                    + "CHECK (regimen_tributario IN ('MYPE', 'REMIPE', 'GENERAL'))");
            jdbc.update("UPDATE tributario_schema.clientes SET regimen_tributario = 'REMIPE' WHERE id = ?", remipe);
            String rucAntes = jdbc.queryForObject("SELECT ruc FROM tributario_schema.clientes WHERE id = ?", String.class, remipe);
            assertThat(regimenGuardado(remipe)).isEqualTo("REMIPE");
            assertThatThrownBy(() -> insertar("RER", "HABIDO", "Todavía no existe")).isInstanceOf(DataIntegrityViolationException.class);

            volverACorrerElEsquema();

            assertThat(regimenGuardado(remipe)).isEqualTo("RER");
            assertThat(condicionGuardada(remipe)).isEqualTo("NO_HABIDO");
            assertThat(jdbc.queryForMap("SELECT ruc, razon_social, direccion, activo FROM tributario_schema.clientes WHERE id = ?", remipe))
                    .containsEntry("ruc", rucAntes).containsEntry("razon_social", "Antes REMIPE")
                    .containsEntry("direccion", "Av. Prueba 123").containsEntry("activo", true);
            assertThat(regimenGuardado(mype)).isEqualTo("MYPE");
            assertThat(condicionGuardada(mype)).isEqualTo("HABIDO");
            assertThat(regimenGuardado(general)).isEqualTo("GENERAL");
            assertThat(condicionGuardada(general)).isEqualTo("NO_HABIDO");
            assertThat(clientes.findById(remipe).orElseThrow().getRegimenTributario()).isEqualTo(Regimen.RER);
            assertThatThrownBy(() -> insertar("REMIPE", "HABIDO", "Ya no se acepta")).isInstanceOf(DataIntegrityViolationException.class);
            insertar("RER", "HABIDO", "Ya se acepta");

            volverACorrerElEsquema(); // arrancar otra vez no cambia nada
            assertThat(regimenGuardado(remipe)).isEqualTo("RER");
            assertThat(regimenGuardado(mype)).isEqualTo("MYPE");
        } finally {
            volverACorrerElEsquema(); // deja la base como la dejó el arranque, pase lo que pase
        }
    }

    @Test
    void laColumnaDelRegimenLaboralDeLosTrabajadoresNoSeToca() {
        assertThat(jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'tributario_schema.trabajadores'::regclass AND pg_get_constraintdef(oid) LIKE '%regimen_laboral%'",
                String.class)).contains("MYPE").contains("REMIPE").contains("GENERAL");
    }
}
