# Trazabilidad de diagramas UML — Sprint 1

Los diagramas originales se contrastaron con versiones anteriores de `develop`. T22 y T54 se revisaron de nuevo contra `origin/develop` `fc1a225582b620c6cf46c36695190549ace1f9b2` (03/11/2026). Fuente `.puml` (PlantUML) en esta carpeta e imagen `.png` con el mismo nombre en `img/`. Para regenerarlas: `java -jar plantuml.jar -tpng -charset UTF-8 -o img *.puml` desde `docs/uml/`.

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
| 22 | HU-36 | [`T22-HU36-actividad`](T22-HU36-actividad.puml) · [png](img/T22-HU36-actividad.png) | `LoginPage.tsx`, `authService.ts`, `jwtSession.ts`, `apiClient.ts`, `PrivateRoute.tsx`, `AuthController.login`, `AuthService.login`, `JwtProvider`. Contraste: `fc1a225` (merge del PR #16, 02/11/2026 23:34) | `AuthServiceTest`, `AuthControllerTest`, `JwtProviderTest`, `authService.test.tsx` |
| 35 | HU-03 | [`T35-HU03-clases`](T35-HU03-clases.puml) · [png](img/T35-HU03-clases.png) | `TrabajadorController`, `TrabajadorService`, `Trabajador`, `TrabajadorDto`, `TrabajadorRepository` | `TrabajadorServiceTest`, `PermisosPorRolTest` |
| 41 | HU-04 | [`T41-HU04-secuencia`](T41-HU04-secuencia.puml) · [png](img/T41-HU04-secuencia.png) | `DocumentoController`, `ExpedienteService.archivar`, `DocumentoLegal`, `DocumentoRepository`, `GlobalExceptionHandler` | `ExpedienteServiceTest`, `PermisosPorRolTest` |
| 48 | HU-05 | [`T48-HU05-clases`](T48-HU05-clases.puml) · [png](img/T48-HU05-clases.png) | `AcuerdoController`, `AcuerdoService`, `AcuerdoServicio`, `HonorarioHistorial`, `AcuerdoDto`, `AcuerdoRepository`, `HonorarioHistorialRepository` | `AcuerdoServiceTest`, `PermisosPorRolTest` |
| 54 | HU-07 | [`T54-HU07-actividad`](T54-HU07-actividad.puml) · [png](img/T54-HU07-actividad.png) | Alta manual: `ObligacionController.crear`, `ObligacionProgramacionService`. Programación: `ProgramacionMensualController`, `ProgramacionMensualService`, `CronogramaConsultaService`, `GrupoRucCronograma` (incluye el cronograma de buen contribuyente, `Cliente.buenContribuyente`), `GeneradorObligaciones`, `ReglaQuinta`, `ParametrosTributarios`. Contraste: `fc1a225` | `ObligacionProgramacionServiceTest`, `ProgramacionMensualServiceTest`, `ProgramacionMensualControllerTest`, `ReglaQuintaTest` |
| 67 | HU-09 | [`T67-HU09-estados`](T67-HU09-estados.puml) · [png](img/T67-HU09-estados.png) | `Obligacion.Estado`, `ObligacionService.declarar` y `cambiarVencimiento`, `DeclaracionDto` | `ObligacionServiceTest` |
| 73 | HU-10 | [`T73-HU10-secuencia`](T73-HU10-secuencia.puml) · [png](img/T73-HU10-secuencia.png) | `ObligacionController.cambiarVencimiento`, `ObligacionService.cambiarVencimiento`, `VencimientoHistorial`, `VencimientoHistorialRepository` | `ObligacionServiceTest` |
| 62 | HU-08 | [`T62-HU08-secuencia-calendario`](T62-HU08-secuencia-calendario.puml) · [png](img/T62-HU08-secuencia-calendario.png) | `CalendarioController`, `CalendarioService`, `CalendarioObligacionDto`, `VencimientoTributario`, `ObligacionService.listar`, `SecurityConfig`. Código de `develop` en el commit `5adfb88` (PR #5 fusionado) | `CalendarioServiceTest`, `CalendarioPermisosTest`, `VencimientoTributarioTest` |

## Lo que el diseño no puede respaldar con código

- **Roles:** `CLIENTE_MYPE` aparece en `SecurityConfig` de ms-tributario, pero no se siembra en `auth_schema.roles`. Hoy no existe ninguna cuenta con ese rol.
- **Usuarios de base de datos (PR #10 fusionado):** el código y `docker-compose.yml` admiten `AUTH_DB_USER`/`AUTH_DB_PASSWORD` y `TRIBUTARIO_DB_USER`/`TRIBUTARIO_DB_PASSWORD`. El respaldo `DB_USER`/`DB_PASSWORD` continúa disponible. El README indica que los usuarios por servicio son opcionales; no se ha verificado que cada desarrollador haya migrado su base local.
- **Cronograma SUNAT:** la tabla `cronograma_tributario` se carga al arrancar (84 filas) y `ProgramacionMensualService` la consulta para programar obligaciones. El alta manual sigue recibiendo una fecha explícita.
- **`GET /api/auth/me`:** existe y exige token, pero el frontend no lo invoca.
- **Pruebas del contrato del token entre servicios (PR #7 fusionado):** `ContratoTokenTest` (ms-auth) y `TokenEntreServiciosTest` (ms-tributario) ya están en `develop`.
- **Tarea 54 (HU-07):** la programación mensual usa el último dígito del RUC para seleccionar la fecha del cronograma. IGV y RENTA se generan; PLAME requiere trabajadores ingresados al cierre del mes. El régimen no cambia la selección de fecha. ESSALUD y quinta están desactivadas por defecto, aunque existen parámetros para activarlas; AFP se omite siempre porque falta su cronograma propio. El alta manual sigue siendo un flujo separado.
- **Tarea 79 (HU-13):** no hay código en `develop`, así que no se diagrama. La tarea 62 (HU-08) se diagramó después de fusionar el PR #5 (ver su fila en el bloque 2).
- **Tarea 85 (HU-37):** tampoco hay código; se hizo como diseño previo (ver abajo).

## Diseños previos (no derivados del código)

Diagramas de funcionalidad que **no existe en `develop`**. Muestran lo que piden las tareas, no lo que hace el código, y no se pueden contrastar con él hasta que se implemente.

| Tarea | Historia | Diagrama | Fuente | Pendiente de decisión |
|---|---|---|---|---|
| 85 | HU-37 | [`T85-HU37-actividad-crear-acceso-cliente`](T85-HU37-actividad-crear-acceso-cliente.puml) · [png](img/T85-HU37-actividad-crear-acceso-cliente.png) | Tareas 85 a 90 de `tareas_sprint1.csv`; `docs/sprint1-jeanpierre/HU-38.md` | Cómo se enlaza usuario y cliente; entrega de la contraseña inicial (tarea 88, con Beto); códigos de rechazo y servicio responsable |
