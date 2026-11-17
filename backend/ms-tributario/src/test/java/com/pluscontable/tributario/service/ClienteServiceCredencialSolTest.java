package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.config.AesUtil;
import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.dto.CredencialSolDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.CredencialSol;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.CredencialSolRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * HT-02 · La Clave SOL pasa por el cifrado antes de guardarse y solo se descifra en memoria al leerla (criterios 1 a 3 en
 * el servicio que la usa). AesUtil es el real, con llaves ficticias; los repositorios son simulados.
 */
class ClienteServiceCredencialSolTest {

    private static final long CLIENTE = 7L;
    private static final String USUARIO = "USUARIO-FICTICIO";
    private static final String CLAVE = "ClaveSol-Ficticia-9X";
    private static final String IV = "iv-ficticio-16by";

    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final CredencialSolRepository credenciales = mock(CredencialSolRepository.class);
    private final AesUtil aes = new AesUtil("clave-ficticia-de-prueba-32-byte", IV);
    private final ClienteService service = new ClienteService(clientes, credenciales, aes);

    @BeforeEach
    void preparar() {
        Cliente c = new Cliente();
        c.setId(CLIENTE);
        c.setRuc("20100070970");
        c.setRazonSocial("Acme SAC");
        when(clientes.findById(CLIENTE)).thenReturn(Optional.of(c));
        when(credenciales.findByClienteId(CLIENTE)).thenReturn(Optional.empty());
    }

    private CredencialSol guardada() {
        ArgumentCaptor<CredencialSol> g = ArgumentCaptor.forClass(CredencialSol.class);
        verify(credenciales).save(g.capture());
        return g.getValue();
    }

    @Test
    void criterio1_elUsuarioYLaClaveSeGuardanCifradosYNuncaEnClaro() {
        service.guardarCredencialSol(CLIENTE, new CredencialSolDto(USUARIO, CLAVE));

        CredencialSol c = guardada();
        assertThat(c.getClaveSolCifrada()).startsWith(AesUtil.PREFIJO_V2).doesNotContain(CLAVE);
        assertThat(c.getUsuarioSolCifrado()).startsWith(AesUtil.PREFIJO_V2).doesNotContain(USUARIO);
        assertThat(aes.decrypt(c.getClaveSolCifrada())).isEqualTo(CLAVE);
        assertThat(aes.decrypt(c.getUsuarioSolCifrado())).isEqualTo(USUARIO);
        assertThat(c.getClienteId()).isEqualTo(CLIENTE);
    }

    @Test
    void criterio1_alCambiarLaClaveSeVuelveACifrarYSeConservaLaMismaFila() {
        CredencialSol existente = new CredencialSol();
        existente.setClienteId(CLIENTE);
        existente.setUsuarioSolCifrado(aes.encrypt("USUARIO-VIEJO"));
        existente.setClaveSolCifrada(aes.encrypt("clave-vieja"));
        when(credenciales.findByClienteId(CLIENTE)).thenReturn(Optional.of(existente));

        service.guardarCredencialSol(CLIENTE, new CredencialSolDto(USUARIO, CLAVE));

        CredencialSol c = guardada();
        assertThat(c).isSameAs(existente);
        assertThat(aes.decrypt(c.getClaveSolCifrada())).isEqualTo(CLAVE);
        assertThat(c.getClaveSolCifrada()).doesNotContain(CLAVE).doesNotContain("clave-vieja");
    }

    @Test
    void criterio2_laLecturaDescifraSoloEnMemoriaYElClienteNuncaLaDevuelve() {
        service.guardarCredencialSol(CLIENTE, new CredencialSolDto(USUARIO, CLAVE));
        CredencialSol c = guardada();
        when(credenciales.findByClienteId(CLIENTE)).thenReturn(Optional.of(c));

        CredencialSolDto leida = service.obtenerCredencialSol(CLIENTE);

        assertThat(leida.claveSol()).isEqualTo(CLAVE);
        assertThat(c.getClaveSolCifrada()).doesNotContain(CLAVE); // lo guardado sigue cifrado después de leer
        // la ficha del cliente solo dice si hay Clave SOL; nunca el usuario ni la clave
        ClienteDto ficha = service.obtener(CLIENTE);
        assertThat(ficha.tieneClaveSol()).isTrue();
        assertThat(ficha.claveSol()).isNull();
        assertThat(ficha.usuarioSol()).isNull();
    }

    @Test
    void criterio3_unaClaveGuardadaConOtraLlaveNoSeLeeYNoSeMuestraNada() {
        AesUtil otraLlave = new AesUtil("otra-clave-ficticia-de-prueba-32", IV);
        CredencialSol c = new CredencialSol();
        c.setClienteId(CLIENTE);
        c.setUsuarioSolCifrado(otraLlave.encrypt(USUARIO));
        c.setClaveSolCifrada(otraLlave.encrypt(CLAVE));
        when(credenciales.findByClienteId(CLIENTE)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.obtenerCredencialSol(CLIENTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(CLAVE).hasMessageNotContaining(USUARIO);
    }

    @Test
    void sinClienteNoSeCifraNiSeGuardaNada() {
        when(clientes.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.guardarCredencialSol(99L, new CredencialSolDto(USUARIO, CLAVE)))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        verify(credenciales, never()).save(any());
    }

    @Test
    void largo_elMaximoQueCabeCifradoEnLaColumnaSeGuardaYUnByteMasDa400() {
        int max = ClienteService.MAX_SOL_BYTES;
        assertThat(max).isEqualTo(194); // 300 caracteres de columna: "v2:" + Base64(12 de IV + texto + 16 de etiqueta)

        service.guardarCredencialSol(CLIENTE, new CredencialSolDto("u".repeat(max), "c".repeat(max)));
        CredencialSol c = guardada();
        assertThat(c.getClaveSolCifrada().length()).isLessThanOrEqualTo(300);
        assertThat(c.getUsuarioSolCifrado().length()).isLessThanOrEqualTo(300);
    }

    @Test
    void largo_siUnoDeLosDosPasaDelMaximoSeRechazaConUnMensajeClaroSinGuardarNada() {
        int max = ClienteService.MAX_SOL_BYTES;

        assertThatThrownBy(() -> service.guardarCredencialSol(CLIENTE, new CredencialSolDto(USUARIO, "c".repeat(max + 1))))
                .isInstanceOf(jakarta.validation.ValidationException.class)
                .hasMessageContaining("claveSol").hasMessageNotContaining("usuarioSol").hasMessageContaining("194");
        assertThatThrownBy(() -> service.guardarCredencialSol(CLIENTE, new CredencialSolDto("u".repeat(max + 1), CLAVE)))
                .isInstanceOf(jakarta.validation.ValidationException.class)
                .hasMessageContaining("usuarioSol").hasMessageNotContaining("claveSol");
        assertThatThrownBy(() -> service.guardarCredencialSol(CLIENTE, new CredencialSolDto("u".repeat(max + 1), "c".repeat(max + 1))))
                .hasMessageContaining("usuarioSol").hasMessageContaining("claveSol");
        verify(credenciales, never()).save(any());
    }

    @Test
    void largo_seCuentaEnBytesYUnaEnieOcupaDos() {
        assertThat(ClienteService.MAX_SOL_BYTES / 2).isEqualTo(97);

        service.guardarCredencialSol(CLIENTE, new CredencialSolDto(USUARIO, "ñ".repeat(97)));
        assertThat(guardada().getClaveSolCifrada().length()).isLessThanOrEqualTo(300);

        assertThatThrownBy(() -> service.guardarCredencialSol(CLIENTE, new CredencialSolDto(USUARIO, "ñ".repeat(98))))
                .isInstanceOf(jakarta.validation.ValidationException.class).hasMessageContaining("claveSol");
    }
}
