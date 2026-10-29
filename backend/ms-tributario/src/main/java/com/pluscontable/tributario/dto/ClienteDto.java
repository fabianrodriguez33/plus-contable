package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.Cliente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** usuarioSol/claveSol son opcionales y solo de entrada; nunca se devuelven en este DTO. */
public record ClienteDto(
        Long id,
        @NotBlank(message = "El RUC es obligatorio")
        @Pattern(regexp = "^[0-9]{11}$", message = "El RUC debe tener exactamente 11 dígitos numéricos") String ruc,
        @NotBlank(message = "La razón social es obligatoria") @Size(max = 200) String razonSocial,
        @NotNull(message = "El régimen tributario es obligatorio") Cliente.Regimen regimenTributario,
        Cliente.CondicionHabido condicionHabido,
        @Size(max = 250) String direccion,
        @Size(max = 20) String telefono,
        @Email(message = "El email no tiene un formato válido") @Size(max = 150) String email,
        String usuarioSol,
        String claveSol,
        Boolean tieneClaveSol) {

    public static ClienteDto from(Cliente c, boolean tieneClaveSol) {
        return new ClienteDto(c.getId(), c.getRuc(), c.getRazonSocial(), c.getRegimenTributario(),
                c.getCondicionHabido(), c.getDireccion(), c.getTelefono(), c.getEmail(), null, null, tieneClaveSol);
    }
}
