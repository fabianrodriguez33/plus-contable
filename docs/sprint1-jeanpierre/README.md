# Sprint 1 · Jeanpierre Miranda — rama `feature/jeanpierre-sprint1`

Trabajo de Jeanpierre Miranda en el Sprint 1 de Plus Contable: HU-03, HU-04, HU-05, HU-09, HU-10 y HU-38 (28 pts).

| | |
|---|---|
| Rama | `feature/jeanpierre-sprint1` |
| Creada desde | `main` en `cc48860` ("estructura inicial"), como pide la guía del sprint |
| Estado | Solo local: sin push ni Pull Request |
| Pruebas | 97 unitarias en verde, 1 desactivada (tarea 85) |

## ⚠️ Compatibilidad con las otras ramas

Esta rama **no es compatible directamente** con `develop` ni con `feature/seguridad-jwt`. Las tres parten de
`cc48860`, pero el equipo y esta rama crearon por separado los mismos archivos de `ms-tributario` y del frontend con
contenido distinto. Git los marca como conflicto *add/add*: no los puede combinar solo.

Simulación de merge (`git merge-tree`, 2026-10-22):

| Contra | Archivos en conflicto |
|---|---|
| `origin/develop` | **16** |
| `origin/feature/seguridad-jwt` | **11** |

Archivos en conflicto con ambas ramas:

- `config/SecurityConfig.java` — aquí está el control por rol de HU-38
- `controller/TrabajadorController.java`, `dto/TrabajadorDto.java`, `model/Trabajador.java` — HU-03
- `controller/DocumentoController.java`, `dto/DocumentoDto.java`, `model/DocumentoLegal.java`,
  `repository/DocumentoRepository.java`, `service/ExpedienteService.java` — HU-04
- `resources/schema.sql`
- `frontend-web/src/App.tsx`

Solo con `develop`: `pom.xml`, `model/Cliente.java`, `package.json`, `package-lock.json` e `index.css`. Esta rama
copió esos archivos de `feature/seguridad-jwt`, que ya los cambió respecto de `develop`.

**Diferencias de fondo** (no solo de texto) que hay que decidir al integrar:

| Tema | Equipo (`develop` / `seguridad-jwt`) | Esta rama |
|---|---|---|
| Tabla `trabajadores` | `regimen_laboral` obligatorio, SUNAFIL/MTPE/EsSalud | Datos de contrato del extranjero; tipo de documento DNI/CE/PASAPORTE/PTP |
| Tabla `documentos_legales` | Sin fecha de emisión ni versión; admite `COPIA_LITERAL` | `fecha_emision` y `version` obligatorias (historial) |
| Descarga de documentos | `GET /api/tributario/documentos/{id}/descargar` | `GET /api/tributario/clientes/{id}/documentos/{doc}/archivo` (valida que el documento sea del cliente) |
| Validación de trabajador | Anotaciones en el DTO, un error a la vez | En el servicio; lista todos los campos faltantes juntos |
| Seguridad | Cualquier usuario autenticado puede todo | Matriz por rol (HU-38) |
| `App.tsx` | Login, dashboard, ficha maestra | Solo las rutas de estas historias |

`schema.sql` usa `CREATE TABLE IF NOT EXISTS`: **si la base ya tiene las tablas del equipo, las de esta rama no se
crean.** Mientras se trabaje aquí, usar una base limpia (`docker compose down -v`).

**Ramas por HU en el remoto:** existen `feature/HU-03-nomina`, `HU-04-documentos`, `HU-05-acuerdo-honorarios`,
`HU-09-declaracion`, `HU-10-variar-fecha` y `HU-38-permisos`, vacías y creadas desde `develop`. Si el equipo
trabaja con una rama por HU, la integración natural es llevar los commits de cada HU de esta rama a su rama
(son commits separados por tarea, con el ID de la HU en el mensaje) y resolver ahí los conflictos sobre el código de
`develop`. **Decisión pendiente con el equipo; no se ha subido nada.**

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
- **Falta la tarea 85**, que un cliente no vea datos de otro. Está propuesta en HU-38.md (claim `clienteId` en el
  JWT) y pendiente de acordar con Ricardo.

### Dependencias provisionales
- `Cliente` y `ClienteRepository` (HU-01, Julio) están copiados tal cual de `feature/seguridad-jwt`.
- `Obligacion` y `GET /api/tributario/obligaciones?clienteId=` son **PROVISIONALES** hasta integrar HU-07 (Ricardo).

## Estado en el Excel

| Estado | Tareas |
|---|---|
| Hecho | 33, 34, 35, 36 · 39, 40, 41, 42 · 45, 46, 47, 48 · 62, 63, 64 · 67, 68, 69, 70 · 83, 84 |
| Diseño en borrador, falta validar | 32, 38, 44, 61, 66, 82 |
| Integración, necesita PostgreSQL | 37, 43, 49, 65 |
| Bloqueada por el acuerdo con Ricardo | 85 |

## Cómo levantar

```bash
docker compose up -d                                  # PostgreSQL 16 en :5432
cd backend/ms-tributario && ./mvnw spring-boot:run    # API en :8082
cd frontend-web && npm install && npm run dev         # Web en :5173
```

Rutas de la web: `/clientes/{id}/trabajadores`, `/documentos`, `/acuerdo`, `/vencimientos` y
`/obligaciones/{id}/declarar`.

Pruebas sin base de datos:

```bash
cd backend/ms-tributario && ./mvnw test -Dtest="*ServiceTest,PermisosPorRolTest"
```

`MsTributarioApplicationTests` (contextLoads) necesita PostgreSQL. Esta rama no incluye `ms-auth`; para obtener un
token real hay que levantarlo desde `feature/seguridad-jwt`.

Dependencias pendientes y decisiones abiertas: [NOTAS.md](NOTAS.md).
