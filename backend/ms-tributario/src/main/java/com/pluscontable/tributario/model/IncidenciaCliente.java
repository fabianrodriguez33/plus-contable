package com.pluscontable.tributario.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** HU-23: un cliente afectado por una incidencia (una fila por cliente asociado). */
@Entity
@Table(name = "incidencias_clientes", schema = "tributario_schema")
@IdClass(IncidenciaCliente.Clave.class)
@Getter
@Setter
@NoArgsConstructor
public class IncidenciaCliente {
    @Id
    @Column(name = "incidencia_id")
    private Long incidenciaId;

    @Id
    @Column(name = "cliente_id")
    private Long clienteId;

    public IncidenciaCliente(Long incidenciaId, Long clienteId) {
        this.incidenciaId = incidenciaId;
        this.clienteId = clienteId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long incidenciaId;
        private Long clienteId;
    }
}
