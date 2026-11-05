package com.pluscontable.tributario.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Solo corre con app.aes.migrar=true (variable APP_AES_MIGRAR). Registra cantidades, nunca valores. */
@Component
@ConditionalOnProperty(name = "app.aes.migrar", havingValue = "true")
public class CredencialSolMigradorRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CredencialSolMigradorRunner.class);

    private final CredencialSolMigrador migrador;

    public CredencialSolMigradorRunner(CredencialSolMigrador migrador) {
        this.migrador = migrador;
    }

    @Override
    public void run(ApplicationArguments args) {
        int filas = migrador.migrar();
        log.info("Migracion de credenciales SOL a v2: {} fila(s) modificada(s)", filas);
    }
}
