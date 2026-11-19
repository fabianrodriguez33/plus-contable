package com.pluscontable.auth.service;

import java.security.SecureRandom;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * HU-37: contraseña temporal aleatoria. Se saca de un SecureRandom, sin caracteres que se confunden al leerla (0/O, 1/l/I) y
 * siempre con mayúscula, minúscula y número.
 */
@Component
public class GeneradorPassword {

    static final int LARGO = 12;
    static final String MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String MINUSCULAS = "abcdefghijkmnopqrstuvwxyz";
    static final String NUMEROS = "23456789";
    private static final String TODOS = MAYUSCULAS + MINUSCULAS + NUMEROS;

    private final Random azar;

    public GeneradorPassword() {
        this(new SecureRandom());
    }

    GeneradorPassword(Random azar) {
        this.azar = azar;
    }

    public String generar() {
        char[] c = new char[LARGO];
        c[0] = elegir(MAYUSCULAS);
        c[1] = elegir(MINUSCULAS);
        c[2] = elegir(NUMEROS);
        for (int i = 3; i < LARGO; i++) c[i] = elegir(TODOS);
        for (int i = LARGO - 1; i > 0; i--) { // mezcla para que las tres primeras no queden siempre en el mismo lugar
            int j = azar.nextInt(i + 1);
            char t = c[i];
            c[i] = c[j];
            c[j] = t;
        }
        return new String(c);
    }

    private char elegir(String alfabeto) {
        return alfabeto.charAt(azar.nextInt(alfabeto.length()));
    }
}
