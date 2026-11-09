# Plan de PlusContable para la app móvil de clientes (HU-A)

App: [`plus-contable-cliente-app`](https://github.com/JeanpierreSam/plus-contable-cliente-app) (KMP, solo Android). Entrega: **mié 18/11/2026 ~12:00**.
Aquí solo se toca el backend. Todo lo visual vive en el otro repo (su `PLAN.md`).

## Principio: no se crea un `/api/cliente/**` nuevo

La HU-38 ya dejó lo difícil: rol `CLIENTE_MYPE`, claim `clienteId` en el JWT y `AccesoCliente.exigir`. Lo más corto es **abrir las rutas
existentes** al rol `CLIENTE_MYPE` y llamar a `AccesoCliente.exigir(clienteId)` al inicio de cada una, igual que las seis de la matriz
([`HU-38-matriz-de-permisos.md`](HU-38-matriz-de-permisos.md)). La app usa las mismas rutas que el frontend web.

## Qué ya existe y qué falta

| Pantalla de la app | Ruta | Estado hoy | Trabajo |
|---|---|---|---|
| Login | `POST /api/auth/login` | Existe, pública | Ninguno (confirmar tiempo de vida del token) |
| Sesión | `GET /api/auth/me` | Existe | Confirmar si devuelve `clienteId`; si no, la app lo lee del JWT |
| Obligaciones | `GET /api/tributario/obligaciones?clienteId=` y `/{id}` | **Abierta** a `CLIENTE_MYPE` | Ninguno |
| Documentos | `GET /clientes/{id}/documentos` y `/{doc}/archivo` | **Abierta** | Ninguno |
| Semáforo / próximo vencimiento | `GET /calendario?clienteId=` | Solo estudio | **Abrir** con `AccesoCliente` |
| Honorarios | `GET /clientes/{id}/cobros` | Solo estudio | **Abrir** con `AccesoCliente` |
| Cuotas de fraccionamiento | `GET /clientes/{id}/fraccionamientos` | Solo estudio | **Abrir** con `AccesoCliente` |
| Reportar incidencia | `POST /incidencias`, `GET /clientes/{id}/incidencias` | Solo estudio | **Abrir**; en el `POST`, el `clienteId` del cuerpo debe coincidir con el del token |
| Avisos | `GET /clientes/{id}/notificaciones` | **No existe** (solo `POST` y `PATCH`) | **Crear** el `GET` (de solo lectura) |
| Cuentas de cliente | HU-37 (alta de usuarios `CLIENTE_MYPE`) | **No existe** | **Bloqueante**: sin cuentas reales no hay con qué entrar (ver A1) |

## HU-A: «Como cliente, consulto mis datos desde la app y solo veo lo mío» (5 pts)

| Tarea | Detalle | Hecho cuando |
|---|---|---|
| A1 | **Cuentas de cliente.** Confirmar con Fabian si HU-37 ya la tiene alguien. Mínimo para el 18/11: script SQL de siembra con 2 clientes de demostración (usuario, contraseña, `cliente_id`); el alta por endpoint queda para HU-37 | Se puede iniciar sesión como `CLIENTE_MYPE` con un `clienteId` real |
| A2 | Abrir al rol `CLIENTE_MYPE` en `SecurityConfig` las rutas de calendario, cobros, fraccionamientos e incidencias (solo `GET`, más el `POST` de incidencias), y llamar a `AccesoCliente.exigir` en cada una | Un cliente recibe 200 en lo suyo y 403 en lo ajeno |
| A3 | `GET /clientes/{id}/notificaciones`, de solo lectura, con `AccesoCliente` | Devuelve las notificaciones SOL del cliente |
| A4 | Pruebas en `PermisosPorRolTest`: sin token 401, sin claim 403, cliente propio 200, cliente ajeno 403 (o 404 si Beto lo acepta), para cada ruta abierta | Pruebas verdes |
| A5 | Actualizar la matriz [`HU-38-matriz-de-permisos.md`](HU-38-matriz-de-permisos.md) y escribir `docs/contrato-api-cliente.md` con las rutas y un JSON de ejemplo de cada una | La app puede escribir sus DTO sin leer el backend |
| A6 | Probar contra un celular: exponer el backend (docker-compose) y documentar la URL base | La guía de «cómo apuntar la app al backend» está escrita |

Orden: **A5 primero** (el contrato desbloquea a la app el día 1), luego A1, A2, A3, A4 y A6.

## Calendario del backend

| Día | Meta |
|---|---|
| Lun 9 | A5: contrato en borrador, enviado a Fabian y a la app |
| Mar 10 | A1 y A2 en una rama `feature/api-cliente`, PR a `develop` |
| Mié 11 | A3 y A4; la app ya puede iniciar sesión contra la API real |
| Jue 12 – Lun 16 | Correcciones que pida la app (solo ajustes de JSON o permisos) |
| Mar 17 | Congelar el backend; sin cambios de contrato |

## Fuera de alcance (a propósito)

- **Notificaciones push (FCM):** requiere el SDK de Firebase Admin y un job en `ms-tributario`. Queda como mejora posterior; la app muestra los avisos con `GET /notificaciones`.
- **Subir comprobantes desde la app**, pasarela de pago y cierre de sesión en el servidor (`/logout` no existe).
- **404 en vez de 403 para un cliente ajeno:** sigue pendiente de la decisión de Beto (ver la matriz de HU-38).

## Riesgos

- **Fabian lleva el backend:** hay que acordar con Fabian quién abre cada ruta, para no pisar la misma rama.
- **Sin HU-37 no hay login de cliente:** A1 es el camino crítico.
- **Rutas del estudio ya cubiertas por pruebas:** abrir una ruta cambia su fila en `PermisosPorRolTest`; si se olvida la prueba, la matriz queda falsa.
