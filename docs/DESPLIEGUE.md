# Despliegue gratuito y público de Plus Contable

Arquitectura: **Neon** (PostgreSQL) + **Render** (ms-auth y ms-tributario, plan gratuito) + **Cloudflare Pages** (la web).
Nada de esto está creado todavía: son los pasos para que Julio lo haga con sus propias cuentas. **Ningún secreto va en el repositorio**
(ni en archivos, ni en el chat, ni en capturas): se generan en tu terminal y se pegan directo en el panel de cada servicio.

## 1. Variables de entorno (nombres; los valores no se versionan)

| Variable | Servicio | Qué es |
|---|---|---|
| `DB_URL` | ambos | `jdbc:postgresql://<host>/<base>?sslmode=require` (sin usuario ni clave en la URL) |
| `DB_USER`, `DB_PASSWORD` | ambos | usuario y clave de la base (de Neon) |
| `JWT_SECRET` | ambos | **el mismo valor en los dos**, mínimo 32 caracteres |
| `AES_SECRET_KEY` | ms-tributario | exactamente 32 caracteres |
| `AES_IV` | ms-tributario | exactamente 16 caracteres |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | ms-auth | administrador inicial (solo se crea si no existe) |
| `TRIBUTARIO_URL` | ms-auth | URL pública de ms-tributario, `https://….onrender.com` |
| `CORS_ORIGINS` | ambos | origen(es) de la web, sin barra final, separados por coma: `https://<proyecto>.pages.dev` |
| `PORT` | ambos | lo pone Render; sin ella: 8081 (auth) y 8082 (tributario) |
| `JAVA_OPTS` | ambos | memoria de la JVM (ver sección 3); ya viene en la imagen |
| `STORAGE_DIR` | ms-tributario | carpeta de comprobantes y documentos (`/data/uploads` en la imagen) |
| `DB_POOL_MAX`, `DB_MAX_LIFETIME_MS` | ambos | conexiones máximas (5) y vida de una conexión (240000 ms) |
| `VITE_AUTH_URL`, `VITE_TRIBUTARIO_URL` | web | **de compilación**: URL de cada API, con `https://` y sin barra final |

Plantillas sin valores: [`.env.example`](../.env.example) (backend) y [`frontend-web/.env.example`](../frontend-web/.env.example) (web).
`render.yaml` solo lista estos nombres con `sync: false`.

## 2. Crear la base en Neon

1. Entra a neon.tech con tu cuenta, crea un proyecto (plan Free) y elige la región más cercana a la de Render.
2. En **Connection details** usa el host **directo** (sin `-pooler`) y arma `DB_URL` así:
   `jdbc:postgresql://<host>/<base>?sslmode=require`. Usuario y clave van en `DB_USER` y `DB_PASSWORD`.
3. **Los esquemas y las tablas se crean solos**: cada servicio ejecuta su `schema.sql` en cada arranque
   (`CREATE SCHEMA IF NOT EXISTS auth_schema` / `tributario_schema`, y todo con `IF NOT EXISTS`; nunca borra datos).
   El usuario de Neon es dueño de la base, así que puede crearlos. Orden recomendado: arranca primero ms-auth y luego ms-tributario.
4. Si prefieres crearlos tú antes (opcional), desde tu terminal con `psql` y la cadena de conexión de Neon (pégala en la terminal, no en un archivo):
   ```powershell
   psql "<cadena de conexion de Neon>" -c "CREATE SCHEMA IF NOT EXISTS auth_schema" -c "CREATE SCHEMA IF NOT EXISTS tributario_schema"
   psql "<cadena de conexion de Neon>" -f backend/ms-auth/src/main/resources/schema.sql
   psql "<cadena de conexion de Neon>" -f backend/ms-tributario/src/main/resources/schema.sql
   ```
5. Los usuarios de base por servicio (HT-01, `db/roles-por-servicio.sql`) son opcionales y no se usan en este despliegue gratis:
   ambos servicios entran con `DB_USER`.

### Pool de conexiones y la suspensión de Neon

Neon suspende el cómputo tras ~5 minutos sin uso y corta las conexiones abiertas. El pool (HikariCP, `application.yml`) está ajustado así:
`max-lifetime` 240 s (menos que la suspensión), `idle-timeout` 120 s, `minimum-idle` 0 (el pool se vacía solo), `validation-timeout` 5 s y
`connection-timeout` 30 s (da tiempo a que la base despierte). Hikari valida cada conexión antes de entregarla y reemplaza las cortadas.

Verificación automática: `PoolConexionesPostgresTest` (en ms-auth y ms-tributario) carga el pool con la configuración real, corta la conexión
desde el servidor con `pg_terminate_backend` y comprueba que la siguiente consulta funciona con otra conexión. Verificación manual en Neon:
abre la web, espera más de 5 minutos sin usarla y vuelve a entrar; la primera consulta tarda unos segundos más y no debe dar error.

## 3. Imágenes Docker y memoria (512 MB)

`backend/ms-auth/Dockerfile` y `backend/ms-tributario/Dockerfile`: dos etapas (JDK 21 compila, JRE 21 Alpine ejecuta), usuario sin privilegios,
puerto desde `PORT`. Memoria elegida (`JAVA_OPTS`): `-Xms64m -Xmx280m -XX:MaxMetaspaceSize=110m -XX:ReservedCodeCacheSize=40m -Xss512k
-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError`. Suma ≈ 430 MB como techo y deja ~80 MB al sistema.

Medido en un PC con Docker, contenedor limitado a 512 MB (`--memory=512m`), base PostgreSQL local, sin carga:

| | ms-auth | ms-tributario |
|---|---|---|
| Arranque de Spring (`Started … in`) | 8,4 s | 9,8 s |
| Memoria del contenedor en reposo | ≈ 190 MB | ≈ 200 MB |
| Tamaño de la imagen | 385 MB | 389 MB |

En Render gratis la CPU es compartida y menor: espera **de 30 a 90 s** para el primer arranque, más el despertar de Neon. Estos valores no se midieron en Render.

Para construir y probar en tu PC (con Docker Desktop abierto):
```powershell
docker build -t pc-ms-auth backend/ms-auth
docker build -t pc-ms-tributario backend/ms-tributario
```

## 4. Crear los servicios en Render

1. Entra a render.com con tu cuenta y conecta tu GitHub.
2. **New → Blueprint**, elige el repositorio y la rama que quieras desplegar. Render lee `render.yaml` y propone los dos servicios gratuitos.
3. Render te pide los valores de las variables `sync: false`. Pégalos desde tu terminal (sección 6). Para `TRIBUTARIO_URL` y `CORS_ORIGINS` aún no
   tienes las URLs: pon un valor provisional y corrígelo después (paso 6 de esta lista).
4. Cuando ambos estén «Live», anota sus URLs públicas (`https://plus-contable-auth….onrender.com` y `https://plus-contable-tributario….onrender.com`).
5. En `plus-contable-auth` pon `TRIBUTARIO_URL` con la URL de tributario.
6. Tras publicar la web (sección 5), pon `CORS_ORIGINS` con su URL en **los dos** servicios y deja que se reinicien.
7. Prueba `https://<servicio>/api/auth/health` y `…/api/tributario/health` (deben responder 200).

## 5. Publicar la web en Cloudflare Pages

1. Entra a dash.cloudflare.com → **Workers & Pages → Create → Pages → Connect to Git** y elige el repositorio.
2. Configuración de compilación: *Root directory* `frontend-web`, *Build command* `npm ci && npm run build`, *Build output directory* `dist`.
3. Variables (Production): `VITE_AUTH_URL`, `VITE_TRIBUTARIO_URL` con las URLs de Render y `NODE_VERSION` = `20`.
   Si falta alguna, la compilación lo avisa (`[aviso] VITE_… no esta definida`) y la web publicada llamaría a localhost.
4. La URL será `https://<proyecto>.pages.dev`: úsala en `CORS_ORIGINS` (sección 4, paso 6).
5. Ya incluido en `frontend-web/public`: `_redirects` (toda ruta vuelve a `index.html`, necesario para las rutas de React Router) y `_headers`
   (`sw.js` e `index.html` sin caché; `/assets` con caché larga porque lleva hash en el nombre).
6. El service worker (HU-34) sigue versionado: cada compilación registra `/sw.js?v=<id de compilación>` y borra los cachés de versiones anteriores.
   Solo se registra en producción.

## 6. Generar los secretos en tu terminal sin guardarlos

Abre PowerShell. Cada comando deja el valor **en el portapapeles**; pégalo en el campo del panel (Render) y ejecuta el siguiente.
Al terminar, el último comando vacía el portapapeles. No los pegues en archivos, chats ni capturas.

```powershell
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
function Nuevo-Texto([int]$n) { $a=[char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'; $b=New-Object byte[] $n; $rng.GetBytes($b); -join ($b | % { $a[$_ % $a.Length] }) }
```
```powershell
# JWT_SECRET (el mismo en los dos servicios: pégalo en ambos antes de generar otro valor)
$b = New-Object byte[] 48; $rng.GetBytes($b); Set-Clipboard -Value ([Convert]::ToBase64String($b))
```
```powershell
Set-Clipboard -Value (Nuevo-Texto 32)   # AES_SECRET_KEY (32 caracteres)
```
```powershell
Set-Clipboard -Value (Nuevo-Texto 16)   # AES_IV (16 caracteres)
```
```powershell
Set-Clipboard -Value (Nuevo-Texto 20)   # ADMIN_PASSWORD
```
```powershell
Set-Clipboard -Value ' '                # vaciar el portapapeles
```

**Guarda `AES_SECRET_KEY` y `AES_IV` en tu gestor de contraseñas** (no en un archivo del repositorio): si se pierden o cambian, las claves SOL ya cifradas
dejan de poder leerse. Si cambias `JWT_SECRET`, todas las sesiones abiertas se cierran. `DB_PASSWORD` te lo da Neon.

## 7. Datos de demostración

Después de que ms-auth y ms-tributario hayan arrancado una vez contra la base (crean las tablas):

```powershell
$env:DEMO_DATABASE_URL = Read-Host "Cadena de conexion de la base (postgresql://...)"
.\db\demo\cargar-demo.ps1
```
Crea el contador `contador.demo`, el cliente MYPE `cliente.demo` (ve solo «Mi empresa» de `(DEMO) Panaderia El Trigal SAC`) y cuatro clientes
ficticios más, con socios y obligaciones de fechas relativas a hoy. Todo es inventado (los RUC `20999…` tienen dígito verificador válido pero son
ficticios; podrían coincidir por casualidad con uno real y no sirven para trámites). **Las contraseñas se generan al ejecutar, se muestran una sola vez y no se
guardan**; si las pierdes, vuelve a ejecutar el script (pone otras nuevas). Es repetible: no duplica clientes ni obligaciones. Requiere `psql` y la extensión
`pgcrypto` (Neon la permite).
Para quitar la demo: borra los clientes con razón social que empiece por `(DEMO)` y los usuarios `contador.demo` y `cliente.demo`.

## 8. Límites del plan gratuito (léelos antes de presentar)

- **Render duerme el servicio tras 15 minutos sin tráfico.** La primera petición después de dormir tarda de ~30 s a más de un minuto (despertar de Render +
  arranque de la JVM + despertar de Neon). La web puede mostrar un error o tardar en la primera pantalla; recargar suele bastar.
  Antes de una demostración abre `…/api/auth/health` y `…/api/tributario/health` unos minutos antes.
- **Los trabajos programados de las 7:00 (hora de Lima) no corren con el servicio dormido.** Un trabajo `@Scheduled` solo se ejecuta si el proceso
  está despierto a esa hora; el plan gratis casi nunca lo está de madrugada. Hoy `develop` no tiene trabajos programados; los de los avisos de pago (HU-33, PR #68)
  y las alertas de carta (HU-19, PR #73) los añaden y, en este despliegue, **no se ejecutarían por sí solos**. Opciones, fuera de este PR: un servicio externo
  gratuito que llame al `health` a las 6:55, o un plan de pago. Un servicio despierto 24 h gasta las 750 horas gratuitas mensuales de Render (dos servicios las superan).
- **El disco de Render gratis no es persistente**: los comprobantes y documentos subidos a ms-tributario (`STORAGE_DIR`) se pierden al reiniciar o redesplegar.
  Los datos en la base (Neon) sí persisten. Para guardar archivos hace falta un disco de pago o un almacenamiento externo.
- **Neon Free** suspende el cómputo a los 5 minutos sin uso (el pool lo tolera, ver sección 2) y tiene límites de almacenamiento y horas de cómputo.
- **Memoria**: 512 MB por servicio. Una carga muy grande (por ejemplo un archivo de 10 MB junto a otras peticiones) puede reiniciar el servicio.
- **CORS**: si la web muestra «No se pudo conectar con el servidor» pero el `health` responde, revisa que `CORS_ORIGINS` sea exactamente el origen de la web.
- **HTTPS y datos reales**: este despliegue es para demostración con datos ficticios. No cargues claves SOL ni datos reales de clientes sin revisar seguridad y respaldos.
