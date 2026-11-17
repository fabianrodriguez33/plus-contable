# Matriz de permisos por ruta (HU-38, tarea 91)

Estado de `develop` al 03/11/2026 (commit `fc1a225`). Cubre **todas** las rutas de `ms-auth` y de `ms-tributario`.
Amplía la matriz de [`sprint1-jeanpierre/HU-38.md`](sprint1-jeanpierre/HU-38.md), que solo lista las opciones de
lectura y alta de cada historia; las que aparecen en las dos coinciden.

## Cómo se obtuvo

1. **Reglas:** `SecurityConfig` de [`ms-tributario`](../backend/ms-tributario/src/main/java/com/pluscontable/tributario/config/SecurityConfig.java)
   y de [`ms-auth`](../backend/ms-auth/src/main/java/com/pluscontable/auth/config/SecurityConfig.java), y el listado de rutas de
   cada controlador.
2. **Pruebas:** se contrastó cada ruta con las pruebas existentes (`PermisosPorRolTest` y las demás de la última columna).
3. **Medición:** para no fiarse solo de la lectura, se midió con una prueba temporal (no versionada) el estado HTTP real de cada ruta
   para cada situación, con la configuración de seguridad real, JWT firmados como `ms-auth` y los servicios simulados:
   las 30 rutas de `ms-tributario` más una que no existe (con `GET` y `POST`), y las 3 de `ms-auth` más dos que no existen (`logout` y una
   cualquiera). El resultado coincide con las tablas. No es un arranque completo de los servicios.

## Roles y situaciones

| Situación | Qué es |
|---|---|
| Sin token | Petición sin `Authorization`, o con token vencido o falsificado: **401** |
| Token sin rol | JWT válido sin ningún rol: **403** en toda ruta protegida de `ms-tributario` |
| `CLIENTE_MYPE` | Se siembra en `ms-auth`; las cuentas de cliente se crean en HU-37 (todavía no existe el alta). Su token lleva el claim `clienteId` y `ms-tributario` solo le deja ver su empresa (`AccesoCliente`). Sin ese claim, 403 |
| `CONTADOR` y `ADMIN` | El estudio. Tienen exactamente los mismos permisos |

Reglas generales de `ms-tributario`: las rutas que `SecurityConfig` no lista son **solo del estudio** (`anyRequest().hasAnyRole(ESTUDIO)`);
las respuestas 401 y 403 van sin cuerpo; `OPTIONS` (CORS) es público; el reenvío interno de errores (`/error`) no exige token, pero `GET /error`
directo sí.

## `ms-auth`

| Método y ruta | Sin token | Token sin rol | `CLIENTE_MYPE` | `CONTADOR` y `ADMIN` | Prueba que la respalda |
|---|---|---|---|---|---|
| `POST /api/auth/login` | pública (400 con cuerpo vacío) | pública | pública | pública | `AuthControllerTest` |
| `GET /api/auth/health` | 200 | 200 | 200 | 200 | `HealthControllerTest` |
| `GET /api/auth/me` | 401 | 200 | 200 | 200 | `AuthControllerTest`: token vigente, sin token, vencido, de otra clave y alterado |
| `POST /api/auth/logout` | 401 | 404 | 404 | 404 | **No existe la ruta** |
| Cualquier otra ruta | 401 | 404 | 404 | 404 | Solo la medición (sin prueba propia) |

`GET /api/auth/me` no depende del rol: devuelve los datos del usuario del token (`sub`), así que distingue por usuario, no por cliente.
`ms-auth` no tiene rutas para crear usuarios ni para administrar roles.

## `ms-tributario`

Leyenda de la columna **¿Distingue entre clientes?**

- **Solo su cliente (`AccesoCliente`):** un `CLIENTE_MYPE` entra a la ruta, pero `AccesoCliente.exigir` compara el `clienteId` del token con el cliente que se pide.
  Si es otro, o el token no trae `clienteId`, responde **403** (igual que el resto de la matriz). Es la tarea 94.
  **Pendiente de revisión de Beto:** hoy al cliente ajeno se le responde 403, y eso le revela que ese cliente existe. Lo ideal es que un cliente MYPE solo "vea" sus propios clientes, es decir, responder 404 como si el otro no existiera. Se propone cambiar a 404 (lanzar `EntityNotFoundException` en `AccesoCliente`) o dejarlo como mejora futura si Beto lo acepta así.
- **Estudio: todos:** la ruta es solo del estudio, y el estudio ve todos los clientes. No hay fuga hacia un cliente MYPE.
- **No aplica:** la ruta no trabaja con los datos de un cliente concreto.

### Abiertas a `CLIENTE_MYPE` (solo lectura)

| Método y ruta | Sin token | Token sin rol | `CLIENTE_MYPE` | `CONTADOR` y `ADMIN` | ¿Distingue entre clientes? | Prueba que la respalda |
|---|---|---|---|---|---|---|
| `GET /api/tributario/clientes/{id}/trabajadores` | 401 | 403 | Sí | Sí | Solo su cliente (`AccesoCliente`) | `PermisosPorRolTest` (todas las situaciones); `ManejoDeErroresHttpTest` (401) |
| `GET /api/tributario/clientes/{id}/documentos` | 401 | 403 | Sí | Sí | Solo su cliente (`AccesoCliente`) | `PermisosPorRolTest` |
| `GET /api/tributario/clientes/{id}/documentos/{doc}/archivo` | 401 | 403 | Sí | Sí | Solo su cliente (`AccesoCliente`); el servicio además comprueba que el documento sea del cliente de la ruta | `PermisosPorRolTest` |
| `GET /api/tributario/clientes/{id}/acuerdo` | 401 | 403 | Sí | Sí | Solo su cliente (`AccesoCliente`) | `PermisosPorRolTest` |
| `GET /api/tributario/obligaciones?clienteId=` | 401 | 403 | Sí | Sí | Solo su cliente (`AccesoCliente`) | `PermisosPorRolTest` |
| `GET /api/tributario/obligaciones/{id}` | 401 | 403 | Sí | Sí | Solo su cliente (`AccesoCliente`, con el `clienteId` de la obligación ya cargada; un id inexistente sigue dando 404 al estudio) | `PermisosPorRolTest` |

### Públicas

| Método y ruta | Sin token | Token sin rol | `CLIENTE_MYPE` | `CONTADOR` y `ADMIN` | ¿Distingue entre clientes? | Prueba que la respalda |
|---|---|---|---|---|---|---|
| `GET /api/tributario/health` | 200 | 200 | 200 | 200 | No aplica | `HealthControllerTest` |

### Solo del estudio

| Método y ruta | Sin token | Token sin rol | `CLIENTE_MYPE` | `CONTADOR` y `ADMIN` | ¿Distingue entre clientes? | Prueba que la respalda |
|---|---|---|---|---|---|---|
| `GET /api/tributario/clientes` | 401 | 403 | 403 | Sí | Estudio: todos (lista todos los clientes) | `ClienteControllerTest`; `TokenEntreServiciosTest` |
| `POST /api/tributario/clientes` | 401 | 403 | 403 | Sí | Estudio: todos | `ClienteControllerTest` (contador, admin, `CLIENTE_MYPE`, sin rol y sin token); `ManejoDeErroresHttpTest` |
| `GET /api/tributario/clientes/{id}` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosRutasDelEstudioTest` |
| `PUT /api/tributario/clientes/{id}` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosRutasDelEstudioTest` |
| `GET /api/tributario/clientes/{id}/sol` | 401 | 403 | 403 | Sí | Estudio: todos (devuelve usuario y clave SOL descifrados) | `PermisosRutasDelEstudioTest` |
| `PUT /api/tributario/clientes/{id}/sol` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosRutasDelEstudioTest` |
| `POST /api/tributario/clientes/{id}/trabajadores` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosPorRolTest` |
| `POST /api/tributario/clientes/{id}/documentos` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosPorRolTest` |
| `PUT /api/tributario/clientes/{id}/acuerdo` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosPorRolTest` |
| `GET /api/tributario/clientes/{id}/socios` | 401 | 403 | 403 | Sí | Estudio: todos | `SocioControllerTest.soloElEstudioAccedeALosSocios` |
| `GET /api/tributario/clientes/{id}/socios/resumen` | 401 | 403 | 403 | Sí | Estudio: todos | `SocioControllerTest.soloElEstudioAccedeALosSocios` |
| `PUT /api/tributario/clientes/{id}/socios` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosRutasDelEstudioTest` |
| `POST /api/tributario/clientes/{id}/socios` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosRutasDelEstudioTest` |
| `PATCH /api/tributario/clientes/{id}/socios/{socioId}/aporte` | 401 | 403 | 403 | Sí | Estudio: todos | `SocioControllerTest.soloElEstudioAccedeALosSocios` |
| `GET /api/tributario/clientes/{id}/activos` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosRutasDelEstudioTest` |
| `POST /api/tributario/clientes/{id}/activos` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosRutasDelEstudioTest` |
| `DELETE /api/tributario/clientes/{id}/activos/{id}` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosRutasDelEstudioTest` |
| `POST /api/tributario/obligaciones` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosPorRolTest`; `altaDeObligacionRespetaRolesExistentes` |
| `POST /api/tributario/obligaciones/programacion` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosPorRolTest`; `ProgramacionMensualControllerTest` (401) |
| `PATCH /api/tributario/obligaciones/{id}/declaracion` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosPorRolTest` |
| `PATCH /api/tributario/obligaciones/vencimiento` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosPorRolTest` |
| `GET /api/tributario/calendario` | 401 | 403 | 403 | Sí | Estudio: todos (se puede pedir por cliente) | `CalendarioPermisosTest` |
| `GET /api/tributario/ruc/{ruc}/consulta` | 401 | 403 | 403 | Sí | No aplica (consulta datos públicos del RUC) | `PermisosPorRolTest`; `ConsultaRucControllerTest` |
| Cualquier ruta no listada, por ejemplo `/api/tributario/no-existe` | 401 | 403 | 403 | 404 | No aplica | `ManejoDeErroresHttpTest` (404 con token del estudio) |

«Sí» significa que la seguridad deja pasar la petición; lo que responda después (200, 201, 400, 415…) es del controlador y no es una prueba
del contrato de cada ruta. En la ruta no listada, el 404 también viene después de pasar la seguridad.

## Tarea 94: un cliente nunca ve datos de otro

Resuelta en `ms-tributario` con el claim `clienteId` del JWT:

1. `auth_schema.usuarios.cliente_id` (BIGINT, sin FK porque la tabla `clientes` está en `tributario_schema`) y el rol `CLIENTE_MYPE` se
   siembran en `ms-auth`, en bases nuevas y existentes. `JwtProvider.generate` agrega `clienteId` solo cuando la cuenta lo tiene.
2. `JwtFilter` guarda el `clienteId` en la autenticación y `AccesoCliente.exigir` se llama al inicio de las seis rutas de lectura del
   cliente. El estudio (`CONTADOR`, `ADMIN`) ve todos los clientes.
3. Se cierra por defecto: un `CLIENTE_MYPE` sin claim `clienteId` recibe 403 en las seis rutas.

Pruebas: `PermisosPorRolTest.clienteNuncaVeDatosDeOtroCliente` y las de sin claim, `AccesoClienteTest`, `JwtProviderTest`, `ContratoTokenTest`,
`AuthServiceTest` y `ClienteMypeEsquemaPostgresTest`. **Falta HU-37:** el alta de cuentas de cliente; hasta entonces no hay cuentas reales con ese rol.

## Otras observaciones

- **El estudio ve todos los clientes.** No hay cartera por contador: cualquier `CONTADOR` o `ADMIN` lee y modifica cualquier cliente
  (`ClienteService` lista todos). Es el diseño actual; si cada contador debe ver solo su cartera, es otra decisión y otra tarea.
- **Rutas del estudio con prueba de rol:** `GET` y `PUT /clientes/{id}`, las dos de `/sol`, `PUT` y `POST` de `/socios`, y las tres
  de `/activos` las cubre `PermisosRutasDelEstudioTest` (tarea 93): sin token 401, sin rol y `CLIENTE_MYPE` 403, `CONTADOR` y `ADMIN` pasan.
- **`GET /clientes/{id}/sol` devuelve la clave SOL descifrada** al estudio. Es solo del estudio y es la ruta más sensible; su rol lo fija
  `PermisosRutasDelEstudioTest`.
- **No existe cierre de sesión en el servidor** (`POST /api/auth/logout` responde 404): hoy el token se descarta solo en el navegador.

## Qué falta para cerrar la tarea 91

El documento de Jeanpierre deja la matriz «pendiente de validar». Esta tabla es la matriz completa y medida; falta que Beto y el equipo la
validen (en especial, que `CLIENTE_MYPE` solo lea las seis rutas marcadas y que el estudio ve todos los clientes).
