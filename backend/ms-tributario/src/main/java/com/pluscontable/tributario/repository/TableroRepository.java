package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.Obligacion;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/** HU-14: consulta de solo lectura de obligaciones con filtros opcionales (cliente, mes, estado). */
public interface TableroRepository extends Repository<Obligacion, Long>, JpaSpecificationExecutor<Obligacion> {
}
