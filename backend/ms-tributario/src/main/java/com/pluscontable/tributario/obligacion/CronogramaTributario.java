package com.pluscontable.tributario.obligacion;

import java.time.LocalDate;
import java.time.YearMonth;

/** Una fecha publicada para un periodo y un grupo de último dígito de RUC. */
public record CronogramaTributario(YearMonth periodoTributario, String grupoRuc,
                                  LocalDate fechaVencimiento) {
    public CronogramaTributario {
        if (periodoTributario == null || grupoRuc == null || grupoRuc.isBlank()
                || fechaVencimiento == null) {
            throw new IllegalArgumentException("periodo, grupo RUC y fecha de vencimiento son obligatorios");
        }
    }
}
