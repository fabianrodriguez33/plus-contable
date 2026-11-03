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
| `CLIENTE_MYPE` | **Hoy no existe en `ms-auth`**: solo se siembran `ADMIN` y `CONTADOR`, y no hay forma de crear cuentas de cliente (HU-37). `ms-tributario` sí lo reconoce si llega en un token firmado con el secreto compartido |
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

- **⚠ No distingue:** un `CLIENTE_MYPE` puede llamar la ruta (200) y la ruta **no compara** el cliente del token con el cliente que se pide.
  Cualquier `CLIENTE_MYPE` leería los datos de cualquier cliente. Es la causa de la tarea 94.
- **Estudio: todos:** la ruta es solo del estudio, y el estudio ve todos los clientes. No hay fuga hacia un cliente MYPE.
- **No aplica:** la ruta no trabaja con los datos de un cliente concreto.

### Abiertas a `CLIENTE_MYPE` (solo lectura)

| Método y ruta | Sin token | Token sin rol | `CLIENTE_MYPE` | `CONTADOR` y `ADMIN` | ¿Distingue entre clientes? | Prueba que la respalda |
|---|---|---|---|---|---|---|
| `GET /api/tributario/clientes/{id}/trabajadores` | 401 | 403 | Sí | Sí | **⚠ No distingue** | `PermisosPorRolTest` (todas las situaciones); `ManejoDeErroresHttpTest` (401) |
| `GET /api/tributario/clientes/{id}/documentos` | 401 | 403 | Sí | Sí | **⚠ No distingue** | `PermisosPorRolTest` |
| `GET /api/tributario/clientes/{id}/documentos/{doc}/archivo` | 401 | 403 | Sí | Sí | **⚠ No distingue** (el servicio comprueba que el documento sea del cliente de la ruta, no que ese cliente sea el del usuario) | `PermisosPorRolTest` |
| `GET /api/tributario/clientes/{id}/acuerdo` | 401 | 403 | Sí | Sí | **⚠ No distingue** | `PermisosPorRolTest` |
| `GET /api/tributario/obligaciones?clienteId=` | 401 | 403 | Sí | Sí | **⚠ No distingue** | `PermisosPorRolTest` |
| `GET /api/tributario/obligaciones/{id}` | 401 | 403 | Sí | Sí | **⚠ No distingue** (la obligación se identifica solo por su id) | `PermisosPorRolTest` |

### Públicas

| Método y ruta | Sin token | Token sin rol | `CLIENTE_MYPE` | `CONTADOR` y `ADMIN` | ¿Distingue entre clientes? | Prueba que la respalda |
|---|---|---|---|---|---|---|
| `GET /api/tributario/health` | 200 | 200 | 200 | 200 | No aplica | `HealthControllerTest` |

### Solo del estudio

| Método y ruta | Sin token | Token sin rol | `CLIENTE_MYPE` | `CONTADOR` y `ADMIN` | ¿Distingue entre clientes? | Prueba que la respalda |
|---|---|---|---|---|---|---|
| `GET /api/tributario/clientes` | 401 | 403 | 403 | Sí | Estudio: todos (lista todos los clientes) | `ClienteControllerTest`; `TokenEntreServiciosTest` |
| `POST /api/tributario/clientes` | 401 | 403 | 403 | Sí | Estudio: todos | `ClienteControllerTest` (contador, admin, `CLIENTE_MYPE`, sin rol y sin token); `ManejoDeErroresHttpTest` |
| `GET /api/tributario/clientes/{id}` | 401 | 403 | 403 | Sí | Estudio: todos | **Sin prueba de rol propia** (regla general) |
| `PUT /api/tributario/clientes/{id}` | 401 | 403 | 403 | Sí | Estudio: todos | **Sin prueba de rol propia** |
| `GET /api/tributario/clientes/{id}/sol` | 401 | 403 | 403 | Sí | Estudio: todos (devuelve usuario y clave SOL descifrados) | **Sin prueba de rol propia** |
| `PUT /api/tributario/clientes/{id}/sol` | 401 | 403 | 403 | Sí | Estudio: todos | **Sin prueba de rol propia** |
| `POST /api/tributario/clientes/{id}/trabajadores` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosPorRolTest` |
| `POST /api/tributario/clientes/{id}/documentos` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosPorRolTest` |
| `PUT /api/tributario/clientes/{id}/acuerdo` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosPorRolTest` |
| `GET /api/tributario/clientes/{id}/socios` | 401 | 403 | 403 | Sí | Estudio: todos | `SocioControllerTest.soloElEstudioAccedeALosSocios` |
| `GET /api/tributario/clientes/{id}/socios/resumen` | 401 | 403 | 403 | Sí | Estudio: todos | `SocioControllerTest.soloElEstudioAccedeALosSocios` |
| `PUT /api/tributario/clientes/{id}/socios` | 401 | 403 | 403 | Sí | Estudio: todos | **Sin prueba de rol propia** |
| `POST /api/tributario/clientes/{id}/socios` | 401 | 403 | 403 | Sí | Estudio: todos | **Sin prueba de rol propia** |
| `PATCH /api/tributario/clientes/{id}/socios/{socioId}/aporte` | 401 | 403 | 403 | Sí | Estudio: todos | `SocioControllerTest.soloElEstudioAccedeALosSocios` |
| `GET /api/tributario/clientes/{id}/activos` | 401 | 403 | 403 | Sí | Estudio: todos | **Sin prueba de rol propia** |
| `POST /api/tributario/clientes/{id}/activos` | 401 | 403 | 403 | Sí | Estudio: todos | **Sin prueba de rol propia** |
| `DELETE /api/tributario/clientes/{id}/activos/{id}` | 401 | 403 | 403 | Sí | Estudio: todos | **Sin prueba de rol propia** |
| `POST /api/tributario/obligaciones` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosPorRolTest`; `altaDeObligacionRespetaRolesExistentes` |
| `POST /api/tributario/obligaciones/programacion` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosPorRolTest`; `ProgramacionMensualControllerTest` (401) |
| `PATCH /api/tributario/obligaciones/{id}/declaracion` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosPorRolTest` |
| `PATCH /api/tributario/obligaciones/vencimiento` | 401 | 403 | 403 | Sí | Estudio: todos | `PermisosPorRolTest` |
| `GET /api/tributario/calendario` | 401 | 403 | 403 | Sí | Estudio: todos (se puede pedir por cliente) | `CalendarioPermisosTest` |
| `GET /api/tributario/ruc/{ruc}/consulta` | 401 | 403 | 403 | Sí | No aplica (consulta datos públicos del RUC) | `PermisosPorRolTest`; `ConsultaRucControllerTest` |
| Cualquier ruta no listada, por ejemplo `/api/tributario/no-existe` | 401 | 403 | 403 | 404 | No aplica | `ManejoDeErroresHttpTest` (404 con token del estudio) |

«Sí» significa que la seguridad deja pasar la petición; lo que responda después (200, 201, 400, 415…) es del controlador y no es una prueba
del contrato de cada ruta. En la ruta no listada, el 404 también viene después de pasar la seguridad.

## Por qué hay tarea 94

La prueba `PermisosPorRolTest.clienteNuncaVeDatosDeOtroCliente` está desactivada (`@Disabled`) y la tarea 94 sigue pendiente porque hoy
**no hay con qué comparar** el cliente del token con el cliente de la ruta:

1. **El JWT no lleva cliente.** `JwtProvider.generate(username, roles)` solo escribe `sub` y `roles`; `JwtFilter` de `ms-tributario` arma la
   autenticación con esos dos datos.
2. **Las cuentas no están ligadas a un cliente.** `auth_schema.usuarios` no tiene `cliente_id`, y cada servicio solo accede a su esquema,
   así que `ms-tributario` no puede preguntarle a `ms-auth` qué cliente corresponde a un usuario.
3. **El rol y las cuentas de cliente no existen.** `ms-auth` no siembra `CLIENTE_MYPE` ni tiene un endpoint para crear cuentas (HU-37).

Las seis rutas marcadas **⚠** son exactamente las que dejan entrar a `CLIENTE_MYPE`. **Hoy el riesgo es potencial:** no existen cuentas de ese rol.
Pero un token con rol `CLIENTE_MYPE` firmado con el secreto compartido sí pasa (`TokenEntreServiciosTest` lo usa), y en cuanto HU-37 cree cuentas
de cliente, cada una podría leer los datos de cualquier cliente. Por eso `HU-38.md` dice que no debe salir a producción sin la tarea 94.
La propuesta de solución (claim `clienteId` en el token) está en [`sprint1-jeanpierre/HU-38.md`](sprint1-jeanpierre/HU-38.md).

## Otras observaciones

- **El estudio ve todos los clientes.** No hay cartera por contador: cualquier `CONTADOR` o `ADMIN` lee y modifica cualquier cliente
  (`ClienteService` lista todos). Es el diseño actual; si cada contador debe ver solo su cartera, es otra decisión y otra tarea.
- **Rutas del estudio sin prueba de rol propia:** `GET` y `PUT /clientes/{id}`, las dos de `/sol`, `PUT` y `POST` de `/socios`, y las tres
  de `/activos`. Hoy las cubre la regla general, comprobada en la medición, pero ninguna prueba lo fija. Una prueba que recorra todas las rutas
  de los controladores contra los cuatro roles cerraría el hueco.
- **`GET /clientes/{id}/sol` devuelve la clave SOL descifrada** al estudio. Es solo del estudio, pero es la ruta más sensible y no tiene prueba
  de rol propia.
- **No existe cierre de sesión en el servidor** (`POST /api/auth/logout` responde 404): hoy el token se descarta solo en el navegador.

## Qué falta para cerrar la tarea 91

El documento de Jeanpierre deja la matriz «pendiente de validar». Esta tabla es la matriz completa y medida; falta que Beto y el equipo la
validen (en especial, que `CLIENTE_MYPE` solo lea las seis rutas marcadas y que el estudio ve todos los clientes).
