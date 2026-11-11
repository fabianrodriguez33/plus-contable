# Contrato de la API para la app de clientes

Versión: borrador · Fecha: 2026-11-11 · Autor: Jeanpierre

Describe lo que el backend **hace hoy** para un cliente `CLIENTE_MYPE`. Fuente: el código de `origin/develop` (que incluye HU-18, HU-23 y HU-28)
y los controladores, DTO y `SecurityConfig` de `ms-auth` y `ms-tributario`. La rama `feature/api-cliente` aún no trae el cobro (HU-28), por eso se leyó `develop`.
Lo que no existe está marcado como **propuesta**; lo que no se pudo confirmar leyendo código, como `VERIFICAR:`.

## Reglas generales

- **Dos servicios, dos puertos** (por defecto): `ms-auth` en `:8081` (`/api/auth/**`) y `ms-tributario` en `:8082` (`/api/tributario/**`). No hay puerta de enlace en el repositorio.
  URL base: `<variable por entorno; no poner la real>`. Emulador de Android: `http://10.0.2.2:8081` y `http://10.0.2.2:8082`. VERIFICAR: en un entorno desplegado puede haber un solo host con proxy.
- **Autenticación:** `Authorization: Bearer <token>` en todas las rutas salvo `POST /api/auth/login` y los `health`. El mismo token vale en los dos servicios (mismo `JWT_SECRET`).
- **Token (JWT HS256):** vive **8 horas** (`expiraEnSegundos` = 28800). Claims: `sub` (username), `roles` (lista, p. ej. `["CLIENTE_MYPE"]`, sin prefijo `ROLE_`), `iat`, `exp`, `jti` y, solo en cuentas de cliente, **`clienteId`** (número).
  No hay refresco de token: al vencer hay que volver a iniciar sesión.
- **`GET /api/auth/me` NO devuelve `clienteId`.** La app debe leerlo del claim `clienteId` del JWT (decodificar la parte central, base64url).
- **Fechas:** Spring Boot 3.3.5 con Jackson por defecto, sin configuración propia → texto ISO, nunca arreglo ni número.
  `LocalDate` → `"2026-11-13"`; `LocalDateTime` → `"2026-11-11T10:15:30.123456"` (sin zona; los decimales pueden variar o faltar).
  Los `periodo` son una fecha completa con día 1 (`"2026-10-01"`), **no** `"2026-10"`.
- **Dinero:** `BigDecimal` → número JSON (`1250.50`; puede llegar `1250.5` o `1250`). Sin símbolo ni moneda (soles). En la app, leer como `Double`/`BigDecimal`.
- **Enums:** texto en mayúsculas, tal cual el nombre del enum.
- **Campos nulos:** se envían como `null` (no se omiten), salvo `montoAcumuladoPagado`, `saldo` y `numeroPagos` de la obligación, que **se omiten** cuando no aplican.
- **Error con cuerpo:** `{"mensaje": "texto"}` en `ms-auth` (login) y en `ms-tributario` (400, 404, 409, 413). Ver el detalle por código abajo.
- **Error sin cuerpo:** **401** (sin token, vencido, alterado o revocado) y **403** (rol insuficiente o cliente ajeno) llegan **vacíos**, sin JSON. La app no debe intentar leer `mensaje` en ellos.
- **Cliente ajeno:** el `clienteId` que se pide se compara con el del token (`AccesoCliente`). Si es otro, o el token no trae `clienteId`: **403** sin cuerpo.
  Beto aún no decide si pasará a 404; la app debe tratar 403 y 404 igual.
- **Rutas del estudio:** un `CLIENTE_MYPE` recibe **403** en toda ruta que no esté abierta (ver «Estado»).

### Códigos de error (`ms-tributario`)

| Código | Cuándo | Cuerpo |
|---|---|---|
| 400 | Parámetro o cuerpo inválido (`ValidationException`, validación de campos, JSON ilegible, enum inválido) | `{"mensaje": "..."}` |
| 401 | Sin token / token inválido | vacío |
| 403 | Rol sin permiso o cliente ajeno | vacío |
| 404 | Recurso inexistente (`EntityNotFoundException`) | `{"mensaje": "..."}` |
| 409 | Restricción de datos | `{"mensaje": "..."}` |
| 413 | Archivo mayor de 10 MB | `{"mensaje": "El archivo supera 10 MB"}` |

## Resumen de rutas

| # | Ruta | Pantalla | Estado hoy |
|---|---|---|---|
| 1 | `POST /api/auth/login` | Login | Existe, pública |
| 2 | `GET /api/auth/me` | Sesión | Existe |
| 3 | `POST /api/auth/logout` | Cerrar sesión | **Existe** (la matriz de HU-38 está desactualizada) |
| 4 | `GET /api/tributario/obligaciones?clienteId=` y `/{id}` | Obligaciones | Abierta a `CLIENTE_MYPE` |
| 5 | `GET /api/tributario/calendario?clienteId=` | Semáforo | Existe, **solo estudio** → se abre en A2 |
| 6 | `GET /api/tributario/clientes/{id}/cobros` | Cobros | Existe en `develop` (HU-28), **solo estudio** → se abre en A2 |
| 7 | `GET /api/tributario/clientes/{id}/fraccionamientos` | Cuotas | Existe, **solo estudio** → se abre en A2 |
| 8 | `POST /api/tributario/incidencias`, `GET /api/tributario/clientes/{id}/incidencias` | Incidencias | Existe, **solo estudio** → se abre en A2 |
| 9 | `GET /api/tributario/clientes/{id}/notificaciones` | Avisos | **Existe en `develop`** (HU-18, lo plantea el plan como inexistente), **solo estudio** → falta abrirla y llamar a `AccesoCliente` (A2/A3) |
| 10 | `GET /api/tributario/clientes/{id}/documentos` y `/{doc}/archivo` | Documentos | Abierta a `CLIENTE_MYPE` |

Las rutas 5–9 hoy dan 403 a un `CLIENTE_MYPE`; **el contrato siguiente describe la respuesta que recibirá cuando se abran** (el JSON ya es el real que arma el controlador).
Además, ninguna de ellas llama todavía a `AccesoCliente.exigir`: al abrirlas hay que añadirlo, o un cliente vería datos de otro.

---

## POST /api/auth/login

**Pantalla:** Login · **Quién puede:** pública · **Estado:** existe
**Cuerpo:** `{"username": string, "password": string}`, ambos obligatorios y no vacíos.

**Respuesta 200:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.<payload>.<firma>",
  "tipo": "Bearer",
  "expiraEnSegundos": 28800,
  "usuario": {
    "id": 12,
    "username": "cliente.demo",
    "nombreCompleto": "María Quispe Demo",
    "roles": ["CLIENTE_MYPE"]
  }
}
```

| Campo | Tipo | Notas |
|---|---|---|
| `token` | texto | JWT; ver «Reglas generales» |
| `tipo` | texto | siempre `"Bearer"` |
| `expiraEnSegundos` | número entero | 28800 (8 h) |
| `usuario.id` | número entero | id en `ms-auth`, **no** es el `clienteId` |
| `usuario.username` | texto | |
| `usuario.nombreCompleto` | texto | puede ser `null` |
| `usuario.roles` | lista de texto | `CLIENTE_MYPE`, `CONTADOR`, `ADMIN` |

**Errores** (todos con `{"mensaje": "..."}`):
- 400 — usuario o contraseña vacíos: `"Usuario y contraseña son obligatorios"`.
- 401 — credenciales inválidas (mismo mensaje para usuario inexistente y contraseña incorrecta).
- 403 — `"Usuario inactivo"`.
- **423** (Locked) — cuenta bloqueada: `"Cuenta bloqueada por intentos fallidos. Intente nuevamente en N minuto(s)"`. Se bloquea 15 min tras 5 intentos fallidos.

## GET /api/auth/me

**Pantalla:** Sesión · **Quién puede:** cualquier token válido · **Estado:** existe
**Respuesta 200:** el mismo objeto que `usuario` del login.
```json
{ "id": 12, "username": "cliente.demo", "nombreCompleto": "María Quispe Demo", "roles": ["CLIENTE_MYPE"] }
```
**No trae `clienteId`** (ver reglas generales). **Errores:** 401 sin token/inválido/revocado (vacío); 404 `{"mensaje":"Usuario no encontrado"}` si el usuario del token ya no existe.

## POST /api/auth/logout

**Pantalla:** Cerrar sesión · **Quién puede:** cualquier token válido · **Estado:** existe (HU-36); revoca el `jti` del token en el servidor.
Sin cuerpo. **Respuesta 204** sin contenido; repetirla es inocua. **Errores:** 401 sin token/inválido (vacío). Tras el cierre, ese token da 401 en las dos APIs. La app puede llamarla y, aun si falla, borrar su sesión local.

---

## GET /api/tributario/obligaciones?clienteId={id}

**Pantalla:** Obligaciones · **Quién puede:** `CLIENTE_MYPE` (solo su cliente), `CONTADOR`, `ADMIN` · **Estado:** existe, abierta
**Parámetros:** `clienteId` (query, número, obligatorio).

**Respuesta 200:** lista de obligaciones (puede ser `[]`). El orden lo da el servicio; la app debe ordenar ella.
```json
[
  {
    "id": 301,
    "clienteId": 7,
    "tipoObligacion": "IGV",
    "periodo": "2026-10-01",
    "fechaVencimiento": "2026-11-13",
    "estado": "PENDIENTE",
    "fechaDeclaracion": null,
    "numeroFormulario": null,
    "montoDeterminado": null
  }
]
```

| Campo | Tipo | Notas |
|---|---|---|
| `id` | entero | |
| `clienteId` | entero | |
| `tipoObligacion` | texto | `IGV`, `RENTA`, `PLAME`, `ESSALUD`, `RETENCION_QUINTA`, `AFP` |
| `periodo` | `LocalDate` | primer día del mes que se declara |
| `fechaVencimiento` | `LocalDate` | |
| `estado` | texto | `PENDIENTE`, `DECLARADO`, `PAGO_PARCIAL`, `PAGADO`. **No existe `VENCIDA`**: se deduce con `fechaVencimiento` < hoy y estado distinto de `PAGADO`/`DECLARADO` |
| `fechaDeclaracion` | `LocalDate` | `null` si no se declaró |
| `numeroFormulario` | texto | `null` si no se declaró |
| `montoDeterminado` | número | `null` si no se declaró |
| `montoAcumuladoPagado`, `saldo`, `numeroPagos` | número, número, entero | **se omiten** en el listado; solo salen al registrar un pago |

**Errores:** 400 si falta `clienteId`; 401; 403 cliente ajeno.

## GET /api/tributario/obligaciones/{id}

Misma estructura que un elemento de la lista. **Errores:** 404 `{"mensaje": "..."}` si no existe (al cliente, cuando la obligación es de otro, le llega 403 porque primero se carga y luego se compara); 401; 403.

---

## GET /api/tributario/calendario?clienteId={id}

**Pantalla:** Semáforo · **Quién puede:** hoy solo `CONTADOR`/`ADMIN`; **se abre a `CLIENTE_MYPE` en A2** · **Estado:** existe
**Parámetros (query):** `clienteId` (obligatorio, positivo); opcionales: `desde`, `hasta`, `periodo` (`AAAA-MM-DD`; `periodo` debe ser día 1), `estado` (estado de obligación), `situacion` (`PROXIMO` o `VENCIDO`; `PROXIMO` exige `hasta` ≥ hoy).

**Respuesta 200:** obligaciones con `fechaVencimiento` no nula, **ordenadas por fecha de vencimiento** y luego por id.
```json
[
  {
    "obligacion": {
      "id": 301, "clienteId": 7, "tipoObligacion": "IGV",
      "periodo": "2026-10-01", "fechaVencimiento": "2026-11-13", "estado": "PENDIENTE",
      "fechaDeclaracion": null, "numeroFormulario": null, "montoDeterminado": null
    },
    "diasRestantes": 2,
    "vencimientoPasado": false
  }
]
```

| Campo | Tipo | Notas |
|---|---|---|
| `obligacion` | objeto | igual que en `/obligaciones` |
| `diasRestantes` | entero | positivo si falta tiempo, 0 vence hoy, negativo si ya pasó |
| `vencimientoPasado` | booleano | fecha anterior a hoy, **sin importar el estado** (una obligación pagada también sale `true`) |

**El servidor no manda un «semáforo»** (verde/amarillo/rojo): da `diasRestantes` y `vencimientoPasado`. La app define los umbrales y debe ignorar las obligaciones `PAGADO`/`DECLARADO` al calcularlo.
**Errores:** 400 `{"mensaje": ...}` (`clienteId` no positivo, `desde` > `hasta`, `periodo` no es día 1, `PROXIMO` sin `hasta` válido); 401; 403.

---

## GET /api/tributario/clientes/{id}/cobros

**Pantalla:** Cobros (honorarios) · **Quién puede:** hoy solo estudio; **se abre en A2** · **Estado:** existe en `develop` (HU-28), no en `feature/api-cliente`
**Respuesta 200:** solo los cobros **pendientes** del cliente, el más próximo primero.
```json
[ { "id": 55, "fechaCobro": "2026-11-15", "monto": 350.00, "estado": "PENDIENTE" } ]
```

| Campo | Tipo | Notas |
|---|---|---|
| `id` | entero | |
| `fechaCobro` | `LocalDate` | |
| `monto` | número | |
| `estado` | texto | hoy el enum solo tiene `PENDIENTE` (HU-29 añadirá el pago) |

**No trae** `concepto` ni `periodo`: la app debe mostrar «Honorarios» como texto fijo. **Errores:** 404 si el cliente no existe; 401; 403.
(`POST .../cobros/programacion` también existe pero es del estudio; la app no lo usa.)

---

## GET /api/tributario/clientes/{id}/fraccionamientos

**Pantalla:** Cuotas · **Quién puede:** hoy solo estudio; **se abre en A2** · **Estado:** existe
**Respuesta 200:** lista de fraccionamientos del cliente, cada uno con sus cuotas.
```json
[
  {
    "id": 9, "clienteId": 7, "tributo": "IGV", "montoTotal": 3000.00, "numeroCuotas": 3,
    "fechaPrimeraCuota": "2026-11-20",
    "cuotas": [
      { "id": 90, "numero": 1, "fechaVencimiento": "2026-11-20", "monto": 1000.00,
        "estado": "PENDIENTE", "fechaPago": null, "enRiesgo": false }
    ]
  }
]
```

| Campo | Tipo | Notas |
|---|---|---|
| `tributo` | texto | valores de `tipoObligacion` |
| `montoTotal`, `monto` | número | |
| `numeroCuotas` | entero | |
| `cuotas[].estado` | texto | `PENDIENTE`, `PAGADA` (**no** `PAGADO`). «Vencida» no se guarda |
| `cuotas[].fechaPago` | `LocalDate` | `null` si no se pagó |
| `cuotas[].enRiesgo` | booleano | `true` si está `PENDIENTE` y su fecha ya pasó |

**También:** `GET .../clientes/{id}/fraccionamientos/calendario` (opcionales `desde`, `hasta`, `estado`, `situacion`) → lista de `{"cuota": {...igual...}, "fraccionamientoId": 9, "diasRestantes": 9, "vencida": false}`. Útil para el semáforo de cuotas.
**Errores:** 400 (filtros inválidos); 401; 403.

---

## POST /api/tributario/incidencias

**Pantalla:** Incidencias · **Quién puede:** hoy solo estudio; **se abre en A2**, y en la implementación el `clienteId` del cuerpo debe coincidir con el del token · **Estado:** existe
**Cuerpo:**
```json
{ "tipo": "CLIENTE_NO_ENVIA_DOCUMENTOS", "clienteId": 7, "fecha": "2026-11-11", "descripcion": "Texto de ejemplo" }
```
| Campo | Tipo | Notas |
|---|---|---|
| `tipo` | texto | `CAIDA_PORTAL_SOL` (general, **sin** `clienteId`), `CLIENTE_NO_ENVIA_DOCUMENTOS`, `RECHAZO_FRACCIONAMIENTO`, `OTRO` |
| `clienteId` | entero | obligatorio y positivo salvo en `CAIDA_PORTAL_SOL` (ahí debe omitirse) |
| `fecha` | `LocalDate` | obligatoria, no futura |
| `descripcion` | texto | obligatoria, con máximo de caracteres (VERIFICAR: constante `MAX_DESCRIPCION` en `IncidenciaService`) |

El usuario que registra sale del token (no se envía). **Respuesta 201:**
```json
{ "id": 40, "tipo": "CLIENTE_NO_ENVIA_DOCUMENTOS", "fecha": "2026-11-11", "descripcion": "Texto de ejemplo",
  "registradaPor": "cliente.demo", "registradaEn": "2026-11-11T10:15:30.123456", "clientesAsociados": 1 }
```
**Errores:** 400 `{"mensaje": "Faltan o son inválidos: ..."}`; 401; 403; 404 si el cliente no existe.
Una app de cliente solo debería enviar tipos con cliente; `CAIDA_PORTAL_SOL` es del estudio.

## GET /api/tributario/clientes/{id}/incidencias

**Respuesta 200:** lista de incidencias del cliente con la misma estructura que la respuesta del `POST` (`id`, `tipo`, `fecha`, `descripcion`, `registradaPor`, `registradaEn`, `clientesAsociados`). Orden: VERIFICAR (no se confirmó en el repositorio). **Errores:** 401; 403.

---

## GET /api/tributario/clientes/{id}/notificaciones

**Pantalla:** Avisos · **Quién puede:** hoy solo estudio · **Estado:** **existe en `develop`** (HU-18); falta abrirla al rol y llamar a `AccesoCliente.exigir` (A3 solo sería esa parte, no crear la ruta)
**Parámetros:** `estado` (query, opcional): `PENDIENTE` o `ATENDIDA`. Con `PENDIENTE` devuelve los pendientes.
**Respuesta 200:** notificaciones del cliente por plazo, la más urgente primero.
```json
[ { "id": 12, "clienteId": 7, "tipo": "ESQUELA", "fechaRecepcion": "2026-11-05",
    "plazoRespuesta": "2026-11-20", "estado": "PENDIENTE", "fechaAtencion": null } ]
```

| Campo | Tipo | Notas |
|---|---|---|
| `tipo` | texto | `ESQUELA`, `CARTA_INDUCTIVA`, `COMUNICADO` |
| `estado` | texto | `PENDIENTE`, `ATENDIDA` |
| `fechaAtencion` | `LocalDate` | `null` si está pendiente |

No hay notificaciones push (fuera de alcance). **Errores:** 400 si `estado` no es un valor del enum; 401; 403.

---

## GET /api/tributario/clientes/{id}/documentos

**Pantalla:** Documentos · **Quién puede:** `CLIENTE_MYPE` (solo su cliente), `CONTADOR`, `ADMIN` · **Estado:** abierta
**Respuesta 200:**
```json
[ { "id": 3, "tipo": "FICHA_RUC", "version": 2, "fechaEmision": "2026-10-01",
    "nombreArchivo": "ficha-ruc.pdf", "tamanoBytes": 120450, "subidoEn": "2026-10-02T09:00:00.123456" } ]
```
`tipo`: `MINUTA`, `VIGENCIA_PODERES`, `FICHA_RUC`. `fechaEmision` puede ser `null`. **Errores:** 401; 403.

## GET /api/tributario/clientes/{id}/documentos/{doc}/archivo

Descarga el PDF: `Content-Type: application/pdf`, `Content-Disposition: attachment; filename*=UTF-8''...`. Es binario, **no JSON**. Pide el mismo `Authorization`. **Errores:** 404 `{"mensaje": ...}` si el documento no existe o no es de ese cliente; 401; 403.

---

## Diferencias con la app

DTO provisionales de la app frente a lo real. Se ajusta la app, no el backend.

| App hoy | Backend real | Qué cambia en la app |
|---|---|---|
| `LoginResponse(token, tipo, expiraEnSegundos, usuario)` y `Usuario(id, username, nombreCompleto?, roles[])` | Igual | Nada |
| Falta el `clienteId` | Solo en el JWT, no en `/me` ni en la respuesta del login | Leer el claim `clienteId` del token |
| `Obligacion.tributo` | **`tipoObligacion`** (`IGV`, `RENTA`, `PLAME`, `ESSALUD`, `RETENCION_QUINTA`, `AFP`) | Renombrar |
| `Obligacion.periodo` `"AAAA-MM"` | **`"AAAA-MM-DD"`** (día 1) | Parsear como fecha y mostrar mes/año |
| `Obligacion.vence` | **`fechaVencimiento`** | Renombrar |
| `Obligacion.estado` `PENDIENTE`/`PRESENTADA`/`VENCIDA` | `PENDIENTE`/`DECLARADO`/`PAGO_PARCIAL`/`PAGADO`; no hay `VENCIDA` | Cambiar el enum y calcular «vencida» con la fecha |
| `Obligacion` sin más campos | Hay `clienteId`, `fechaDeclaracion`, `numeroFormulario`, `montoDeterminado` | Declararlos opcionales o ignorarlos (`ignoreUnknownKeys`) |
| `Cobro(id, concepto, periodo, monto, vence, estado PENDIENTE/PAGADO)` | `{id, fechaCobro, monto, estado}`; estado solo `PENDIENTE`; **sin `concepto` ni `periodo`** | Renombrar `vence`→`fechaCobro`, quitar `concepto` y `periodo`, mostrar texto fijo |
| `Cobro.estado` `PAGADO` | Aún no existe (HU-29) | Dejarlo tolerante a valores nuevos |
| `Cuota(numero, vence, monto, estado)` | `{id, numero, fechaVencimiento, monto, estado PENDIENTE/PAGADA, fechaPago, enRiesgo}` dentro de `fraccionamientos[].cuotas` | `vence`→`fechaVencimiento`, `PAGADO`→`PAGADA`, anidar en el fraccionamiento |
| `Error { mensaje }` | Igual **solo** en 400/404/409/413 y en el login | 401/403 vienen vacíos: no decodificar el cuerpo; usar un mensaje propio |
| Semáforo calculado por la app | Disponible `diasRestantes` y `vencimientoPasado` en `/calendario` (cuando se abra) | Opcional: usar `/calendario` en vez de calcular |
| `Dinero: Double` | Número (puede traer 0 a 2 decimales) | Compatible |
| Logout «ignora el error» | La ruta **existe** y responde 204 | Llamarla de verdad y limpiar la sesión local |
| Una sola URL base | Dos servicios (`8081` y `8082`) | Dos URL base o un proxy en el despliegue |

## Pendientes de verificar (`VERIFICAR:`)

1. URL base real y si habrá un solo host con proxy (hoy son dos puertos).
2. Orden del listado de `/incidencias` y valor de `MAX_DESCRIPCION`.
3. Orden de `GET /obligaciones` (se asume sin garantía).
4. JSON exacto de `LocalDateTime` (cantidad de decimales) llamando a un servicio levantado con Docker.
5. Que las rutas 5–9 respondan 200 a un `CLIENTE_MYPE` con su `clienteId` y 403 con otro: aún no pueden, hasta que se abran (A2/A3).
6. Cuentas de cliente de prueba (A1): no hay cómo iniciar sesión como `CLIENTE_MYPE` en un entorno nuevo hasta que existan.

## Para la matriz de HU-38

La matriz no está actualizada en `POST /api/auth/logout` (dice que no existe; existe y responde 204, 401 sin token) ni en `GET .../notificaciones`, `.../cobros` (existen, solo estudio). No se tocó la matriz en este documento; se actualizará junto con A2.
