# Puntos abiertos (sección 6 de la guía)

## Dependencias de otros integrantes

| Depende de | Estado en el repo (2026-10-21) | Solución provisional |
|---|---|---|
| HU-01 ficha del cliente (Julio) | Existe en `feature/seguridad-jwt`, no en `main` | `Cliente`, `ClienteRepository` y la tabla `clientes` copiados **tal cual** de esa rama. No modificarlos aquí. |
| HU-07 obligaciones (Ricardo) | No existe en ninguna rama | `Obligacion`, `ObligacionRepository` y tabla `obligaciones` marcados **PROVISIONAL**. Solo las columnas de declaración son de HU-09. Reemplazar al integrar HU-07. |
| HU-36 login (Ricardo) | JWT con roles en `feature/seguridad-jwt` | `JwtFilter` y `SecurityConfig` copiados tal cual. El filtro ya convierte `roles` del token en `ROLE_*`. |

## Choques previsibles al integrar con `develop` / `feature/seguridad-jwt`

Esta rama sale de `main` (vacío). En esas ramas ya existen versiones distintas de:
`Trabajador`, `TrabajadorDto`, `TrabajadorController`, `DocumentoLegal`, `DocumentoDto`, `DocumentoController`,
`ExpedienteService`, `schema.sql`, `App.tsx`. Al integrar habrá que resolver a mano.

- Allá `trabajadores` tiene `regimen_laboral NOT NULL` y SUNAFIL/MTPE/EsSalud; aquí no. Como `schema.sql` usa
  `CREATE TABLE IF NOT EXISTS`, **si la BD ya tiene la tabla de ellos, la mía no se crea**: usar una BD limpia
  (`docker compose down -v`) mientras se trabaja en esta rama.
- Allá `documentos_legales` no tiene `fecha_emision` ni `version` y admite `COPIA_LITERAL`.
- Allá las rutas de documentos son `/api/tributario/clientes/{id}/documentos` y `/api/tributario/documentos/{id}/descargar`;
  aquí la descarga es `/api/tributario/clientes/{id}/documentos/{docId}/archivo` (valida que el documento sea del cliente).

## Decisiones pendientes

- **HU-04 almacenamiento (decidido 2026-10-22):** disco local en `app.storage.dir` (`./uploads`, ignorado en git),
  máximo 10 MB (`spring.servlet.multipart` y `ExpedienteService.TAMANO_MAXIMO`). Si se despliega en varias
  instancias o contenedores, pasar a un volumen compartido o almacenamiento de objetos.
- **HU-38:** para que "un cliente nunca vea datos de otro" hace falta saber qué `cliente_id` tiene el usuario
  `CLIENTE_MYPE`. Propuesta en [HU-38.md](HU-38.md): claim `clienteId` en el JWT, llenado desde
  `auth_schema.usuarios.cliente_id`. Acordar con Ricardo (HU-36/HU-37). ms-auth aún no tiene el rol `CLIENTE_MYPE`.
- **Ramas del remoto (vistas 2026-10-22):** aparecieron `feature/HU-03-nomina`, `HU-04-documentos`,
  `HU-05-acuerdo-honorarios`, `HU-09-declaracion`, `HU-10-variar-fecha`, `HU-38-permisos` (y otras), todas vacías
  sobre `develop`. Parece que el equipo usa una rama por HU desde `develop`, distinto a esta guía (una rama desde
  `main`). Confirmar con el equipo antes de subir nada.
- **HU-03 prioridad:** tarjeta Alta vs Excel Media; se usa Media hasta que Beto confirme.
- HU-05, HU-09, HU-10 y HU-38: criterios tomados de la hoja de tareas; contrastar con las tarjetas DoR.
