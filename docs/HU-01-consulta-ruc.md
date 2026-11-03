# HU-01 · Consulta de RUC en el registro de cliente

Al registrar un cliente, el contador puede pulsar **Consultar RUC** para que el sistema sugiera razón social, dirección y condición a partir de los datos públicos de SUNAT, vía OpenRUC (`GET {base}/api/ruc/{ruc}`, sin API key).

## Cómo funciona

- **La llamada la hace `ms-tributario`**, nunca el navegador: `GET /api/tributario/ruc/{ruc}/consulta`. Pueden usarla `CONTADOR` y `ADMIN`; `CLIENTE_MYPE` recibe 403 y sin token 401 (regla general de `SecurityConfig`).
- **Validación previa:** el RUC se valida con `RucValidator` (11 dígitos, prefijo permitido y dígito verificador) antes de llamar. Si está mal formado responde 400 y no se llama al proveedor.
- **Cobertura:** OpenRUC solo consulta RUC que empiezan con 20. Para 10, 15 y 17 no se llama y la respuesta es `FUERA_DE_COBERTURA`: el contador ingresa los datos a mano.
- **Respuesta (200):** `resultado` = `ENCONTRADO`, `NO_ENCONTRADO`, `NO_DISPONIBLE` o `FUERA_DE_COBERTURA`, más `mensaje`, `datos` y `avisos`. Un 404 del proveedor es `NO_ENCONTRADO`; timeout, 429, 5xx, JSON ilegible o una respuesta de otro RUC son `NO_DISPONIBLE`. En ningún caso se bloquea el alta.
- **Qué se sugiere:** razón social, dirección (recortada a 250 caracteres con aviso) y condición (solo si es exactamente `HABIDO` o `NO HABIDO`). Régimen tributario, teléfono, email y credenciales SOL siguen siendo manuales. `estado` y `ubigeo` no tienen campo en `Cliente`.
- **Avisos:** un estado distinto de `ACTIVO` o una condición distinta de `HABIDO` muestra un aviso y no bloquea. La política está en un solo método: `ConsultaRucService.avisos`.
- **Nada se guarda como verdadero por sí solo:** los datos solo rellenan el formulario y se muestran con "Datos de SUNAT al <fecha>"; se guarda lo que el contador deja y registra. No se guarda `as_of`.

## Configuración

| Variable | Valor por defecto | Uso |
|---|---|---|
| `OPENRUC_BASE_URL` | `https://openruc.com` | URL base del proveedor |
| `OPENRUC_TIMEOUT_MS` | `3000` | Tiempo máximo de lectura (la conexión espera 2 s) |

Ninguna es un secreto. Para cambiar de proveedor: una nueva implementación de `ConsultaRuc` y `app.ruc.proveedor` en `application.yml`.

## Verificado contra el proveedor

Una consulta real de prueba con el RUC público de SUNAT devolvió los 8 campos documentados (`ruc`, `razon_social`, `estado`, `condicion`, `direccion`, `ubigeo`, `source`, `as_of`) y un bloque adicional `_more` con publicidad, que se ignora. Los valores posibles de `estado` y `condicion` no están documentados: por eso solo se mapea lo conocido.

## Pruebas

- **Backend:** servidor simulado en el equipo (`127.0.0.1`), nunca la red real. Cubre éxito (con `_more`), 404, 429, 5xx, timeout, servicio caído, JSON ilegible, RUC de otra empresa, redirección, RUC mal formado, prefijos fuera de cobertura, avisos y roles.
- **Resultado de la suite de `ms-tributario`** (Java 21, PostgreSQL desechable, nunca `plus_contable_db`): 266 ejecutadas, **265 aprobadas y 1 omitida**, 0 fallidas y 0 con error. Las 37 pruebas nuevas están incluidas.
- **Frontend:** **faltan pruebas automáticas.** `develop` aún no tiene vitest (llega con el PR #6). Hasta entonces el frontend solo se verifica con `npm run build`.
