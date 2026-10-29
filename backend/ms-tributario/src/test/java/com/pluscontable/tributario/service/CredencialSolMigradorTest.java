package com.pluscontable.tributario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.config.AesUtil;
import com.pluscontable.tributario.model.CredencialSol;
import com.pluscontable.tributario.repository.CredencialSolRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/** Claves ficticias. Solo se comprueba que el texto no cambia; nunca se imprimen valores. */
class CredencialSolMigradorTest {

    private static final String CLAVE = "clave-ficticia-de-prueba-32-byte";
    private static final String IV_LEGADO = "iv-ficticio-16by";

    private final AesUtil aes = new AesUtil(CLAVE, IV_LEGADO);
    private final CredencialSolRepository repo = mock(CredencialSolRepository.class);
    private final CredencialSolMigrador migrador = new CredencialSolMigrador(repo, aes);

    private static String cbc(String texto) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(CLAVE.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(IV_LEGADO.getBytes(StandardCharsets.UTF_8)));
        return Base64.getEncoder().encodeToString(cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
    }

    private static CredencialSol fila(long clienteId, String usuario, String clave) {
        CredencialSol c = new CredencialSol();
        c.setClienteId(clienteId);
        c.setUsuarioSolCifrado(usuario);
        c.setClaveSolCifrada(clave);
        return c;
    }

    @Test
    void pasaLasFilasCbcAV2ConservandoElTexto() throws Exception {
        CredencialSol c = fila(1, cbc("usuario-ficticio"), cbc("clave-ficticia"));
        when(repo.findAll()).thenReturn(List.of(c));

        assertEquals(1, migrador.migrar());

        assertTrue(c.getUsuarioSolCifrado().startsWith("v2:"));
        assertTrue(c.getClaveSolCifrada().startsWith("v2:"));
        assertEquals("usuario-ficticio", aes.decrypt(c.getUsuarioSolCifrado()));
        assertEquals("clave-ficticia", aes.decrypt(c.getClaveSolCifrada()));
        verify(repo).saveAll(List.of(c));
    }

    @Test
    void siSeEjecutaDosVecesLaSegundaNoTocaNada() throws Exception {
        CredencialSol c = fila(1, cbc("usuario-ficticio"), cbc("clave-ficticia"));
        when(repo.findAll()).thenReturn(List.of(c));
        migrador.migrar();
        String usuarioV2 = c.getUsuarioSolCifrado();
        String claveV2 = c.getClaveSolCifrada();

        assertEquals(0, migrador.migrar());

        assertEquals(usuarioV2, c.getUsuarioSolCifrado());
        assertEquals(claveV2, c.getClaveSolCifrada());
    }

    @Test
    void laFilaQueYaEstaEnV2NoSeModifica() {
        String usuario = aes.encrypt("usuario-ficticio");
        String clave = aes.encrypt("clave-ficticia");
        CredencialSol c = fila(1, usuario, clave);
        when(repo.findAll()).thenReturn(List.of(c));

        assertEquals(0, migrador.migrar());

        assertEquals(usuario, c.getUsuarioSolCifrado());
        assertEquals(clave, c.getClaveSolCifrada());
    }

    @Test
    void siUnaColumnaYaEstaEnV2SoloSeMigraLaOtra() throws Exception {
        String usuario = aes.encrypt("usuario-ficticio");
        CredencialSol c = fila(1, usuario, cbc("clave-ficticia"));
        when(repo.findAll()).thenReturn(List.of(c));

        assertEquals(1, migrador.migrar());

        assertEquals(usuario, c.getUsuarioSolCifrado());
        assertEquals("clave-ficticia", aes.decrypt(c.getClaveSolCifrada()));
    }

    @Test
    void siUnaFilaFallaNoSeGuardaNinguna() throws Exception {
        CredencialSol buena = fila(1, cbc("usuario-ficticio"), cbc("clave-ficticia"));
        CredencialSol corrupta = fila(2, "no-es-base64-valido!!", cbc("clave-ficticia"));
        when(repo.findAll()).thenReturn(List.of(buena, corrupta));

        assertThrows(IllegalStateException.class, migrador::migrar);

        verify(repo, never()).saveAll(anyList());
    }

    @Test
    void laMigracionCorreDentroDeUnaTransaccion() throws Exception {
        assertNotNull(CredencialSolMigrador.class.getMethod("migrar").getAnnotation(Transactional.class));
    }
}
