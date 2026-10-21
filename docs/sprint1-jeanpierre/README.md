# Sprint 1 · Jeanpierre Miranda (28 pts)

Rama: `feature/jeanpierre-sprint1` (desde `main`). Sin push ni PR hasta que se pida.

| Orden | HU | Historia | Pts | Diseño | Backend | Front | Pruebas |
|---|---|---|---|---|---|---|---|
| 6 | HU-03 | Nómina de trabajadores | 5 | [HU-03](HU-03.md) | `TrabajadorService` | `TrabajadoresPage` | `TrabajadorServiceTest` |
| 7 | HU-04 | Documentos legales | 5 | [HU-04](HU-04.md) | `ExpedienteService` | `DocumentosPage` | `ExpedienteServiceTest` |
| 8 | HU-05 | Acuerdo y honorarios | 5 | [HU-05](HU-05.md) | `AcuerdoService` | `AcuerdoPage` | `AcuerdoServiceTest` |
| 11 | HU-09 | Declaración de un periodo | 5 | [HU-09](HU-09.md) | `ObligacionService.declarar` | `DeclaracionPage` | `ObligacionServiceTest` |
| 12 | HU-10 | Variar vencimiento | 3 | [HU-10](HU-10.md) | `ObligacionService.cambiarVencimiento` | `VencimientosPage` | `ObligacionServiceTest` |
| 15 | HU-38 | Permisos por perfil | 5 | [HU-38](HU-38.md) | `SecurityConfig` | — | `PermisosPorRolTest` |

Cada `TODO Tarea NN` del código corresponde al N° de la hoja Tareas del Excel. Las pruebas están como
`@Disabled("Tarea NN")`: se activan al implementar la tarea.

## Levantar

```bash
docker compose up -d                      # PostgreSQL 16 en :5432
cd backend/ms-tributario && ./mvnw spring-boot:run   # :8082
cd frontend-web && npm install && npm run dev        # :5173
```

Pruebas unitarias sin base de datos:

```bash
cd backend/ms-tributario && ./mvnw test -Dtest="*ServiceTest,PermisosPorRolTest"
```

`MsTributarioApplicationTests` (contextLoads) necesita PostgreSQL arriba.

Dependencias pendientes y decisiones abiertas: [NOTAS.md](NOTAS.md).
