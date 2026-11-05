# Sprint 1 · Jeanpierre Miranda

Trabajo de Jeanpierre Miranda en el Sprint 1 de Plus Contable: HU-03, HU-04, HU-05, HU-09, HU-10 y HU-38 (28 pts).

| | |
|---|---|
| Estado | **En `develop`**, fusionado por el PR #2 (28/10/2026). HU-03, 04, 05, 09 y 10 están **hechas**; HU-38 tiene **hecha** la tarea 94 (un cliente no ve datos de otro, claim `clienteId`), **pendiente de revisión de Beto** (403 o 404, ver abajo); falta validar la matriz con Beto y el alta de cuentas de cliente (HU-37) |
| Pruebas | Suite completa en verde (03/11/2026): `ms-tributario` 459 ejecutadas y ninguna omitida, `ms-auth` 40. La cobertura se mide con JaCoCo: `./mvnw verify` y abrir `target/site/jacoco/index.html` |

> **Numeración de tareas.** Los documentos de esta carpeta usan la numeración vieja: las tareas 82, 83, 84 y 85 de HU-38
> son las tareas **91, 92, 93 y 94** del Sprint Backlog.

## Integración con las otras ramas (nota histórica)

La rama de trabajo salía de `main` (`cc48860`) y no era compatible con `develop`. Al integrar el 22/10 se resolvieron los
conflictos a mano (commits `5b8bd74` y `08d9010`) y el trabajo llegó a `develop` por el PR #2. Ya no hay ramas
pendientes de integrar.

## Tecnologías

| Capa | Tecnología |
|---|---|
| Backend | Java 21 · Spring Boot 3.3.5 (Web, Data JPA, Security, Validation) · Lombok · Maven Wrapper |
| Seguridad | JWT HMAC con jjwt 0.12.6, emitido por `ms-auth` y validado en `ms-tributario` con el mismo secreto |
| Base de datos | PostgreSQL 16 (Docker Compose), esquema `tributario_schema`, script `schema.sql` |
| Pruebas | JUnit 5 · Mockito · AssertJ · Spring MockMvc (`@WebMvcTest`) |
| Frontend | React 18.3 · TypeScript 5.6 · Vite 5.4 · TailwindCSS 3.4 · React Router 6.27 · Axios 1.7 |
| Archivos (HU-04) | Disco local `./uploads/<clienteId>/<uuid>.pdf`, máximo 10 MB |

## Qué se implementó

Cada `HU-XX: … (tarea NN)` es un commit. Los diseños de cada HU están en su archivo (`HU-03.md`, …).

### HU-03 · Nómina de trabajadores — [HU-03.md](HU-03.md)
- `GET/POST /api/tributario/clientes/{clienteId}/trabajadores`
- Nacional y extranjero en la misma tabla. El extranjero exige un documento que no sea DNI, nacionalidad, tipo y
  fecha de inicio de contrato. AFP exige el nombre de la AFP.
- Todos los campos faltantes se informan juntos y la pantalla los resalta.
- Archivos: `TrabajadorService`, `TrabajadoresPage.tsx`, `TrabajadorServiceTest` (7 pruebas).

### HU-04 · Documentos legales — [HU-04.md](HU-04.md)
- `GET/POST /api/tributario/clientes/{clienteId}/documentos`, `GET …/documentos/{id}/archivo`
- Solo PDF: el tipo debe ser `application/pdf` y el contenido debe empezar con `%PDF-`. Máximo 10 MB. Fecha de
  emisión obligatoria y no futura.
- Cada subida crea una versión nueva; las anteriores se conservan. La descarga devuelve el archivo tal cual.
- Archivos: `ExpedienteService`, `DocumentosPage.tsx`, `ExpedienteServiceTest` (11 pruebas).

### HU-05 · Acuerdo y honorarios — [HU-05.md](HU-05.md)
- `GET/PUT /api/tributario/clientes/{clienteId}/acuerdo`
- Honorario mensual (> 0) y fecha de inicio obligatorios. Al cambiar el monto, el anterior pasa a
  `honorarios_historial` con su vigencia.
- Archivos: `AcuerdoService`, `AcuerdoPage.tsx`, `AcuerdoServiceTest` (8 pruebas).

### HU-09 · Declaración de un periodo — [HU-09.md](HU-09.md)
- `PATCH /api/tributario/obligaciones/{id}/declaracion`, `GET /api/tributario/obligaciones/{id}`
- `PENDIENTE` → `DECLARADO` con fecha, número de formulario (sin él no se acepta) y monto determinado.
- Archivos: `ObligacionService.declarar`, `DeclaracionPage.tsx`, `ObligacionServiceTest` (8 pruebas).

### HU-10 · Variar vencimiento — [HU-10.md](HU-10.md)
- `PATCH /api/tributario/obligaciones/vencimiento` (una o varias obligaciones; todas o ninguna)
- Rechaza fechas anteriores al periodo tributario y obligaciones ya declaradas. Guarda la fecha anterior en
  `vencimientos_historial`.
- Archivos: `ObligacionService.cambiarVencimiento`, `VencimientosPage.tsx`, `ObligacionServiceTest` (7 pruebas).

### HU-38 · Permisos por perfil — [HU-38.md](HU-38.md)
- Matriz en `SecurityConfig`: `CLIENTE_MYPE` solo consulta; registrar, archivar, guardar, declarar y variar
  vencimiento son de `CONTADOR`/`ADMIN`. Sin token 401, sin permiso 403.
- `PermisosPorRolTest`: 57 casos con JWT reales.
- **Tarea 94** (antes 85), hecha: un cliente MYPE no ve datos de otro. El JWT lleva el claim `clienteId`, `ms-auth` siembra el
  rol `CLIENTE_MYPE` y `AccesoCliente` (ms-tributario) responde 403 en el cliente ajeno y sin claim. Detalle en HU-38.md.
  **Pendiente de revisión de Beto:** hoy al cliente ajeno se le responde 403, y eso le revela que ese cliente existe. Lo ideal es que un cliente MYPE solo "vea" sus propios clientes, es decir, responder 404 como si el otro no existiera. Se propone cambiar a 404 (lanzar `EntityNotFoundException` en `AccesoCliente`) o dejarlo como mejora futura si Beto lo acepta así.
- `PermisosRutasDelEstudioTest` (tarea 93) cubre las rutas del estudio que no tenían prueba de rol propia.

### Dependencias con otras historias
- `Cliente` (HU-01) y `Obligacion` (HU-07) ya vienen de `develop`: `ObligacionService` agrega las operaciones de
  HU-09 y HU-10 sobre la obligación que crea HU-07.

## Estado en el Sprint Backlog

| Estado | Tareas |
|---|---|
| Hecho | 33, 34, 35, 36 · 39, 40, 41, 42 · 45, 46, 47, 48 · 62, 63, 64 · 67, 68, 69, 70 · 92, 93 |
| Hecha, pendiente de revisión de Beto | 94 (403 o 404 ante el cliente ajeno) |
| Pendiente | Alta de cuentas de cliente (HU-37, de Julio) |
| Validación con Beto | Matriz de permisos (`docs/HU-38-matriz-de-permisos.md`) |

## Cómo levantar

Los pasos del entorno completo (base de datos, `ms-auth`, `ms-tributario` y la web) están en el
[README del proyecto](../../README.md#cómo-levantar-el-entorno-de-desarrollo). Las pruebas y la cobertura, en su
sección «Correr las pruebas» y «Cobertura de pruebas».

Dependencias pendientes y decisiones abiertas: [NOTAS.md](NOTAS.md).
