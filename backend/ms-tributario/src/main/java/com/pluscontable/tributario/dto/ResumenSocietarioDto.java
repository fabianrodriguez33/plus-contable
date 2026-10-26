package com.pluscontable.tributario.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Estructura societaria del cliente.
 *
 * @param capitalSocialTotal suma de los aportes de los socios (S/)
 * @param porcentajeTotal    suma de los porcentajes de participación
 * @param diferencia         100 menos el porcentaje total: positivo = faltan puntos, negativo = sobran, 0 = completo
 * @param completo           true cuando los porcentajes suman exactamente 100.00 %
 */
public record ResumenSocietarioDto(
        List<SocioDto> socios,
        BigDecimal capitalSocialTotal,
        BigDecimal porcentajeTotal,
        BigDecimal diferencia,
        boolean completo) {
}
