# Trazabilidad de diagramas UML — Sprint 1

Todos los diagramas salen del código de `develop` (commit `6db0036`). Fuente `.puml` (PlantUML) en esta carpeta e imagen `.png` con el mismo nombre en `img/`. Para regenerarlas: `java -jar plantuml.jar -tpng -charset UTF-8 -o img *.puml` desde `docs/uml/`.

## Alcance

No se hacen diagramas de casos de uso (indicación del profesor: el proyecto se modela con historias de usuario). Las tareas 13, 22 y 28 que pedían caso de uso quedan cubiertas solo por los demás diagramas listados.

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

## Bloque 2 — tareas de Jeanpierre (35, 41, 48, 67, 73) y Ricardo (22, 54)

| Tarea | Historia | Diagrama | Clases y archivos que lo respaldan | Pruebas relacionadas (en `develop`) |
|---|---|---|---|---|
| 22 | HU-36 | [`T22-HU36-actividad`](T22-HU36-actividad.puml) · [png](img/T22-HU36-actividad.png) | `LoginPage.tsx`, `authService.ts`, `apiClient.ts`, `PrivateRoute.tsx`, `AuthController.login`, `AuthService.login`, `JwtProvider` | `AuthServiceTest`, `AuthControllerTest`, `JwtProviderTest` |
| 35 | HU-03 | [`T35-HU03-clases`](T35-HU03-clases.puml) · [png](img/T35-HU03-clases.png) | `TrabajadorController`, `TrabajadorService`, `Trabajador`, `TrabajadorDto`, `TrabajadorRepository` | `TrabajadorServiceTest`, `PermisosPorRolTest` |
| 41 | HU-04 | [`T41-HU04-secuencia`](T41-HU04-secuencia.puml) · [png](img/T41-HU04-secuencia.png) | `DocumentoController`, `ExpedienteService.archivar`, `DocumentoLegal`, `DocumentoRepository`, `GlobalExceptionHandler` | `ExpedienteServiceTest`, `PermisosPorRolTest` |
| 48 | HU-05 | [`T48-HU05-clases`](T48-HU05-clases.puml) · [png](img/T48-HU05-clases.png) | `AcuerdoController`, `AcuerdoService`, `AcuerdoServicio`, `HonorarioHistorial`, `AcuerdoDto`, `AcuerdoRepository`, `HonorarioHistorialRepository` | `AcuerdoServiceTest`, `PermisosPorRolTest` |
| 54 | HU-07 | [`T54-HU07-actividad`](T54-HU07-actividad.puml) · [png](img/T54-HU07-actividad.png) | `ObligacionController.crear`, `ObligacionProgramacionService`, `ObligacionInputValidator`, `CronogramaConsultaService`, `GrupoRucCronograma`. **Respaldo parcial**: ver nota abajo | `ObligacionProgramacionServiceTest`, `ObligacionInputValidatorTest`, `Hu07ComponentesTest`, `Hu07PostgresIntegrationTest`, `CronogramaYGeneradorTest`, `GrupoRucCronogramaTest`, `CronogramaSunatLoaderTest` |
| 67 | HU-09 | [`T67-HU09-estados`](T67-HU09-estados.puml) · [png](img/T67-HU09-estados.png) | `Obligacion.Estado`, `ObligacionService.declarar` y `cambiarVencimiento`, `DeclaracionDto` | `ObligacionServiceTest` |
| 73 | HU-10 | [`T73-HU10-secuencia`](T73-HU10-secuencia.puml) · [png](img/T73-HU10-secuencia.png) | `ObligacionController.cambiarVencimiento`, `ObligacionService.cambiarVencimiento`, `VencimientoHistorial`, `VencimientoHistorialRepository` | `ObligacionServiceTest` |

## Lo que el diseño no puede respaldar con código

- **Roles:** `CLIENTE_MYPE` aparece en `SecurityConfig` de ms-tributario, pero no se siembra en `auth_schema.roles`. Hoy no existe ninguna cuenta con ese rol.
- **Usuario de base de datos:** ambos servicios usan el mismo usuario `postgres`. No hay usuario por esquema (tarea 4 pendiente).
- **Cronograma SUNAT:** la tabla `cronograma_tributario` se carga al arrancar (84 filas), pero ningún flujo la consulta para calcular vencimientos.
- **`GET /api/auth/me`:** existe y exige token, pero el frontend no lo invoca.
- **Pruebas del contrato del token entre servicios:** `ContratoTokenTest` (ms-auth) y `TokenEntreServiciosTest` (ms-tributario) existen en el PR #7 (`feature/pruebas-julio-s1`), todavía sin fusionar. No se citan arriba porque no están en `develop`.
- **Tarea 54 (HU-07), el diagrama pedido régimen + último dígito del RUC → fecha límite no existe como flujo.** El alta recibe la fecha en la solicitud. `CronogramaConsultaService.buscar` y `GeneradorObligaciones.preparar` existen, pero solo los usan las pruebas, y el régimen tributario no interviene en ningún cálculo. El diagrama muestra el alta real y, en un carril aparte, la consulta existente sin conectar.
- **Tareas 62 (HU-08), 79 (HU-13) y 85 (HU-37):** no hay código en `develop`, así que no se diagraman.
