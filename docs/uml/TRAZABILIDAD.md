# Trazabilidad de diagramas UML — Sprint 1

Todos los diagramas salen del código de `develop` (commit `6db0036`). Fuente `.puml` (PlantUML) en esta carpeta e imagen `.png` con el mismo nombre en `img/`. Para regenerarlas: `java -jar plantuml.jar -tpng -charset UTF-8 -o img *.puml` desde `docs/uml/`.

## Alcance

No se hacen diagramas de casos de uso (indicación del profesor: el proyecto se modela con historias de usuario). Las tareas 13 y 28 que pedían caso de uso quedan cubiertas solo por los demás diagramas listados.

## Bloque 1 — tareas de Julio

| Tarea | Historia | Diagrama | Clases y archivos que lo respaldan | Pruebas relacionadas (en `develop`) |
|---|---|---|---|---|
| 1 | HT-01 | [`T1-HT01-componentes`](T1-HT01-componentes.puml) · [png](img/T1-HT01-componentes.png) | `ms-auth` y `ms-tributario` (`*Application`, `config/SecurityConfig`, `config/JwtFilter`), `AuthController`, `AuthService`, `JwtProvider`, `DataSeeder`, `AesUtil`, `CronogramaSunatLoader`, `docker-compose.yml`, `frontend-web` (`apiClient.ts`) | `MsAuthApplicationTests`, `MsTributarioApplicationTests`, `HealthControllerTest` (ambos servicios) |
| 1 | HT-01 | [`T1-HT01-er-auth_schema`](T1-HT01-er-auth_schema.puml) · [png](img/T1-HT01-er-auth_schema.png) | `ms-auth/src/main/resources/schema.sql`, `AuthService` (eventos de la bitácora) | `AuthServiceTest`, `DataSeederTest` |
| 1 | HT-01 | [`T1-HT01-er-tributario_schema`](T1-HT01-er-tributario_schema.puml) · [png](img/T1-HT01-er-tributario_schema.png) | `ms-tributario/src/main/resources/schema.sql` y las entidades de `model/` | `Hu07PostgresIntegrationTest`, `ClienteServiceTest`, `SocioServiceTest`, `ExpedienteServiceTest`, `AcuerdoServiceTest` |
| 7 | HT-03 | [`T7-HT03-secuencia-login-token`](T7-HT03-secuencia-login-token.puml) · [png](img/T7-HT03-secuencia-login-token.png) | `AuthController.login`, `AuthService.login`, `JwtProvider.generate`, `ms-tributario` `JwtFilter` y `SecurityConfig`, `LoginPage`, `authService.ts`, `apiClient.ts` | `AuthControllerTest`, `AuthServiceTest`, `JwtProviderTest`, `PermisosPorRolTest`, `ManejoDeErroresHttpTest` |
| 13 | HU-01 | [`T13-HU01-clases`](T13-HU01-clases.puml) · [png](img/T13-HU01-clases.png) | `ClienteController`, `ClienteService`, `RucValidator`, `AesUtil`, `Cliente`, `CredencialSol`, `ClienteDto`, `CredencialSolDto`, `ClienteRepository`, `CredencialSolRepository` | `ClienteServiceTest`, `RucValidatorTest`, `AesUtilTest` |
| 13 | HU-01 | [`T13-HU01-secuencia-registrar-cliente`](T13-HU01-secuencia-registrar-cliente.puml) · [png](img/T13-HU01-secuencia-registrar-cliente.png) | `ClienteController.crear`, `ClienteService.crear`, `RucValidator.motivoInvalido`, `AesUtil.encrypt` | `ClienteServiceTest`, `ClienteControllerTest` |
| 13 | HU-01 | [`T13-HU01-actividad`](T13-HU01-actividad.puml) · [png](img/T13-HU01-actividad.png) (extra) | `ClienteService.crear`, `RucValidator`, `GlobalExceptionHandler`, `FichaMaestraPage` | `ClienteServiceTest`, `RucValidatorTest` |
| 28 | HU-02 | [`T28-HU02-clases`](T28-HU02-clases.puml) · [png](img/T28-HU02-clases.png) | `SocioController`, `SocioService`, `Socio`, `SocioDto`, `AporteDto`, `ResumenSocietarioDto`, `SocioRepository` | `SocioServiceTest`, `SocioControllerTest` |

## Lo que el diseño no puede respaldar con código

- **Roles:** `CLIENTE_MYPE` aparece en `SecurityConfig` de ms-tributario, pero no se siembra en `auth_schema.roles`. Hoy no existe ninguna cuenta con ese rol.
- **Usuario de base de datos:** ambos servicios usan el mismo usuario `postgres`. No hay usuario por esquema (tarea 4 pendiente).
- **Cronograma SUNAT:** la tabla `cronograma_tributario` se carga al arrancar (84 filas), pero ningún flujo la consulta para calcular vencimientos.
- **`GET /api/auth/me`:** existe y exige token, pero el frontend no lo invoca.
- **Pruebas del contrato del token entre servicios:** `ContratoTokenTest` (ms-auth) y `TokenEntreServiciosTest` (ms-tributario) existen en el PR #7 (`feature/pruebas-julio-s1`), todavía sin fusionar. No se citan arriba porque no están en `develop`.
