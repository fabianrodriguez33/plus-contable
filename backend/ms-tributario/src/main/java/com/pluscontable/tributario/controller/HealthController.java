package com.pluscontable.tributario.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint de salud público (HT-01): confirma que ms-tributario arrancó. */
@RestController
@RequestMapping("/api/tributario/health")
public class HealthController {

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP", "servicio", "ms-tributario");
    }
}
