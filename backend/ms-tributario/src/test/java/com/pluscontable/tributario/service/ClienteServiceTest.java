package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.config.AesUtil;
import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.CredencialSolRepository;
import jakarta.validation.ValidationException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** HU-01: el RUC se valida (estructura y duplicado) antes de guardar el cliente. */
@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock ClienteRepository clientes;
    @Mock CredencialSolRepository credenciales;
    @Mock AesUtil aes;
    ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(clientes, credenciales, aes);
    }

    private static ClienteDto dto(String ruc) {
        return new ClienteDto(null, ruc, "Empresa SAC", Cliente.Regimen.MYPE, Cliente.CondicionHabido.HABIDO,
                null, null, null, null, null, null);
    }

    @Test
    void rucConDigitoVerificadorIncorrectoNoSeRegistra() {
        assertThatThrownBy(() -> service.crear(dto("20100070971")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("El dígito verificador del RUC no es válido");
        verify(clientes, never()).save(any());
    }

    @Test
    void rucDeOnceUnosNoSeRegistra() {
        assertThatThrownBy(() -> service.crear(dto("11111111111"))).isInstanceOf(ValidationException.class);
        verify(clientes, never()).save(any());
    }

    @Test
    void rucRepetidoSeRechazaConMensajeClaro() {
        when(clientes.existsByRuc("20100070970")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(dto("20100070970")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Ya existe un cliente registrado con el RUC 20100070970");
        verify(clientes, never()).save(any());
    }

    @Test
    void rucValidoYNuevoSeGuarda() {
        when(clientes.existsByRuc("20100070970")).thenReturn(false);
        when(clientes.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });
        when(clientes.findById(5L)).thenAnswer(inv -> {
            Cliente c = new Cliente();
            c.setId(5L);
            c.setRuc("20100070970");
            c.setRazonSocial("Empresa SAC");
            c.setRegimenTributario(Cliente.Regimen.MYPE);
            c.setCondicionHabido(Cliente.CondicionHabido.HABIDO);
            return Optional.of(c);
        });

        ClienteDto creado = service.crear(dto("20100070970"));

        assertThat(creado.id()).isEqualTo(5L);
        assertThat(creado.ruc()).isEqualTo("20100070970");
    }

    private static ClienteDto dtoConMarca(Boolean buenContribuyente) {
        return new ClienteDto(null, "20100070970", "Empresa SAC", Cliente.Regimen.MYPE, Cliente.CondicionHabido.HABIDO,
                null, null, null, null, null, null, buenContribuyente);
    }

    @Test
    void elAltaSinLaMarcaDeBuenContribuyenteLaDejaEnNo() {
        when(clientes.existsByRuc("20100070970")).thenReturn(false);
        when(clientes.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });
        when(clientes.findById(5L)).thenAnswer(inv -> Optional.of(new Cliente()));

        service.crear(dtoConMarca(null));

        var guardado = org.mockito.ArgumentCaptor.forClass(Cliente.class);
        verify(clientes).save(guardado.capture());
        assertThat(guardado.getValue().isBuenContribuyente()).isFalse();
    }

    @Test
    void laMarcaDeBuenContribuyenteSeGuardaYSeDevuelve() {
        when(clientes.existsByRuc("20100070970")).thenReturn(false);
        when(clientes.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });
        when(clientes.findById(5L)).thenAnswer(inv -> {
            Cliente c = new Cliente();
            c.setId(5L);
            c.setBuenContribuyente(true);
            return Optional.of(c);
        });

        ClienteDto creado = service.crear(dtoConMarca(true));

        var guardado = org.mockito.ArgumentCaptor.forClass(Cliente.class);
        verify(clientes).save(guardado.capture());
        assertThat(guardado.getValue().isBuenContribuyente()).isTrue();
        assertThat(creado.buenContribuyente()).isTrue();
    }

    @Test
    void alActualizarSinLaMarcaSeConservaLaGuardada() {
        Cliente existente = new Cliente();
        existente.setId(5L);
        existente.setRuc("20100070970");
        existente.setBuenContribuyente(true);
        when(clientes.findById(5L)).thenReturn(Optional.of(existente));

        service.actualizar(5L, dtoConMarca(null));

        assertThat(existente.isBuenContribuyente()).isTrue();
    }

    @Test
    void alActualizarConLaMarcaEnNoSeQuita() {
        Cliente existente = new Cliente();
        existente.setId(5L);
        existente.setRuc("20100070970");
        existente.setBuenContribuyente(true);
        when(clientes.findById(5L)).thenReturn(Optional.of(existente));

        service.actualizar(5L, dtoConMarca(false));

        assertThat(existente.isBuenContribuyente()).isFalse();
    }

    @Test
    void alActualizarTambienSeValidaElDigitoVerificador() {
        assertThatThrownBy(() -> service.actualizar(5L, dto("20100070979")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("El dígito verificador del RUC no es válido");
        verify(clientes, never()).save(any());
    }
}
