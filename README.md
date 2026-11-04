# PLUS CONTABLE

Sistema Web y Móvil para la Centralización y Control de Obligaciones Tributarias en Estudios Contables.

## Equipo
- Alva Pardave Pedro Ricardo
- Miranda Gamarra Jeanpierre Samuel
- Rodríguez Bazán Julio Fabián

## Stack Tecnológico
- **Backend:** Spring Boot (ms-auth, ms-tributario)
- **Frontend Web:** React + TypeScript + Vite
- **Frontend Móvil:** Kotlin Multiplatform + Compose
- **Base de Datos:** PostgreSQL
- **RPA:** Python + Selenium/Puppeteer

## Cliente
V&F Asesores Empresarial - Sr. Villegas Calizaya Juvencio Ausberto

## Cómo levantar el entorno de desarrollo

Pasos para dejar funcionando la base de datos, `ms-auth`, `ms-tributario` y la web en tu equipo, sin ayuda de nadie. Los comandos funcionan en Git Bash o en PowerShell; donde cambian, se indican los dos.

### Qué necesitas instalado

| Programa | Para qué | Cómo comprobarlo |
|---|---|---|
| Git | Descargar el proyecto | `git --version` |
| JDK 21 | `ms-auth` y `ms-tributario` | `java -version` debe decir 21 |
| Node.js 20 o superior y npm | La web (probado con Node 22) | `node -v` |
| Docker Desktop (con Docker Compose) | PostgreSQL | `docker --version` y Docker Desktop **abierto** |

Los puertos **5432** (base de datos), **8081** (`ms-auth`), **8082** (`ms-tributario`) y **5173** (web) deben estar libres.

### 1. Descargar el proyecto

```bash
git clone https://github.com/fabianrodriguez33/plus-contable.git
cd plus-contable
git switch develop
```

### 2. Crear tu archivo `.env`

El `.env` guarda las contraseñas y las claves de **tu** equipo. Está ignorado por Git: **nunca lo subas ni lo pegues en chats o PR**.

```bash
cp .env.example .env          # Git Bash
copy .env.example .env        # PowerShell
```

Abre `.env` con un editor y completa los valores, **uno por línea, sin comillas y sin comentarios al final de la línea**:

| Variable | Qué poner |
|---|---|
| `POSTGRES_USER`, `POSTGRES_DB` | Déjalos como están (`postgres` y `plus_contable_db`). |
| `POSTGRES_PASSWORD` | Una contraseña tuya para la base de datos. |
| `DB_URL`, `DB_USER` | Déjalos como están. |
| `DB_PASSWORD` | **La misma** que `POSTGRES_PASSWORD`. |
| `JWT_SECRET` | Al menos 32 caracteres. Debe ser el mismo para `ms-auth` y `ms-tributario` (lo es, porque ambos leen el mismo `.env`). |
| `AES_SECRET_KEY` | **Exactamente 32** caracteres. |
| `AES_IV` | **Exactamente 16** caracteres. |
| `ADMIN_USERNAME` | El usuario con el que entrarás a la web (por ejemplo `admin`). |
| `ADMIN_PASSWORD` | La contraseña de ese usuario. Es obligatoria la primera vez. |

Para generar valores aleatorios con el largo correcto (en Git Bash, que incluye `openssl`):

```bash
openssl rand -hex 24    # 48 caracteres: sirve para JWT_SECRET
openssl rand -hex 16    # 32 caracteres: sirve para AES_SECRET_KEY
openssl rand -hex 8     # 16 caracteres: sirve para AES_IV
```

Ejecuta cada comando, copia el resultado en la variable que corresponde y no lo compartas.

> `POSTGRES_USER`, `POSTGRES_PASSWORD` y `POSTGRES_DB` solo se aplican **cuando se crea la base de datos por primera vez**. Si luego los cambias en el `.env`, la base ya creada no se entera.

### 3. Levantar la base de datos

Con Docker Desktop abierto:

```bash
docker compose up -d
docker compose ps
```

El estado de `plus-contable-db` debe decir `healthy` (puede tardar unos segundos; repite `docker compose ps`).

### 4. Levantar `ms-auth` (puerto 8081)

En una terminal nueva:

```bash
cd backend/ms-auth
./mvnw spring-boot:run          # Git Bash
.\mvnw.cmd spring-boot:run      # PowerShell
```

La primera vez descarga dependencias y tarda unos minutos. Comprueba http://localhost:8081/api/auth/health: debe responder `{"servicio":"ms-auth","status":"UP"}`.

### 5. Levantar `ms-tributario` (puerto 8082)

En **otra** terminal:

```bash
cd backend/ms-tributario
./mvnw spring-boot:run          # Git Bash
.\mvnw.cmd spring-boot:run      # PowerShell
```

Comprueba http://localhost:8082/api/tributario/health: debe responder `{"servicio":"ms-tributario","status":"UP"}`.

> Los dos servicios leen tu `.env` solos porque se arrancan desde `backend/ms-auth` o `backend/ms-tributario`. Una variable definida en el sistema operativo tiene prioridad sobre el `.env`.

### 6. Levantar la web (puerto 5173)

En una tercera terminal:

```bash
cd frontend-web
npm ci
npm run dev
```

Abre http://localhost:5173 e inicia sesión con `ADMIN_USERNAME` y `ADMIN_PASSWORD` de tu `.env`. Si entras a la pantalla principal, los tres servicios y la base están funcionando.

### 7. Apagar todo

Cierra cada terminal con `Ctrl + C` y luego:

```bash
docker compose stop
```

`docker compose stop` apaga la base **sin borrar tus datos**. No uses `docker compose down -v`: borra el volumen con todos los datos.

### Correr las pruebas

Las pruebas usan una base de datos propia y desechable. **Nunca** las corras contra `plus_contable_db`. Ejemplo con una base temporal en otro puerto:

```bash
docker run -d --name pc-test-pg -e POSTGRES_PASSWORD=prueba_desechable -e POSTGRES_DB=pc_test -p 55432:5432 postgres:16

cd backend/ms-tributario
./mvnw test -Dtest.db.url=jdbc:postgresql://localhost:55432/pc_test -Dtest.db.password=prueba_desechable
cd ../ms-auth
./mvnw test -Dtest.db.url=jdbc:postgresql://localhost:55432/pc_test -Dtest.db.password=prueba_desechable

docker rm -f pc-test-pg
```

Si no usas esas opciones, Maven busca una base llamada `plus_contable_test` en el puerto 5432 con usuario y contraseña `postgres`.

### Cobertura de pruebas

`./mvnw verify` (en `backend/ms-auth` o en `backend/ms-tributario`) corre las pruebas y genera el reporte de JaCoCo. Ábrelo en `target/site/jacoco/index.html`. La definición de terminado pide al menos 70 % de cobertura.

> Si definiste `AUTH_DB_USER` o `TRIBUTARIO_DB_USER` en tu `.env`, las pruebas también los leen. Con una base desechable, pon esas variables y `DB_USER`/`DB_PASSWORD` apuntando a ella al correr `./mvnw verify`.

### Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `Port 8081 was already in use` (o 8082, o 5173 ocupado) | Quedó una copia anterior en marcha. Cerrar la terminal a veces no cierra el proceso Java o Node: ciérralo desde el Administrador de tareas y vuelve a arrancar. |
| `ADMIN_PASSWORD es obligatorio para crear el administrador inicial` | Falta `ADMIN_PASSWORD` en el `.env` (o está vacía) y el administrador aún no existe. |
| `app.aes.secret-key debe tener exactamente 32 bytes` o `app.aes.legacy-iv debe tener exactamente 16 bytes` | `AES_SECRET_KEY` o `AES_IV` no tienen el largo exacto. Revisa que no haya espacios ni comillas. |
| El servicio no conecta con la base | Docker Desktop apagado, la base no está `healthy`, o `DB_PASSWORD` distinta de `POSTGRES_PASSWORD`. Si cambiaste `POSTGRES_PASSWORD` después de crear la base, la base conserva la anterior. |
| La web dice "No se pudo conectar con el servidor" | `ms-auth` o `ms-tributario` no están en marcha: revisa sus terminales y las direcciones de salud de los pasos 4 y 5. |

## Usuarios de base de datos por servicio

Por defecto `ms-auth` y `ms-tributario` se conectan con el mismo usuario (`DB_USER`, normalmente `postgres`, que lo puede todo). Con los usuarios por servicio, cada uno se conecta con el suyo y la propia base de datos rechaza que `ms-tributario` consulte `auth_schema` (y al revés). Es **opcional**: si no defines las variables siguientes, nada cambia.

Variables del `.env`: descomenta las cuatro líneas de `.env.example` (`AUTH_DB_USER`, `AUTH_DB_PASSWORD`, `TRIBUTARIO_DB_USER`, `TRIBUTARIO_DB_PASSWORD`) y pon contraseñas propias, **no vacías**. Cada servicio usa las suyas y, si no están, vuelve a `DB_USER` y `DB_PASSWORD`.

### Base de datos nueva

Define las cuatro variables en el `.env` **antes** del primer `docker compose up -d`. El contenedor crea los usuarios y sus esquemas al crear el volumen (script `db/init/01-roles-por-servicio.sql`).

### Base de datos que ya existe

Hazlo cuando ninguno de los servicios esté en uso y con un respaldo a mano.

> Las rutas del contenedor llevan **doble barra inicial** (`//tmp/…`, `//docker-entrypoint-initdb.d/…`): en Git Bash, una sola barra se convierte en una ruta de Windows y el comando falla; con doble barra funciona igual en Git Bash y en PowerShell.

1. Respaldo, **fuera** de la carpeta del proyecto:
   ```bash
   docker exec plus-contable-db pg_dump -U postgres -d plus_contable_db -Fc -f //tmp/respaldo.dump
   docker cp plus-contable-db:/tmp/respaldo.dump ../respaldo-plus-contable.dump
   ```
2. Detén `ms-auth` y `ms-tributario` y agrega las cuatro variables al `.env`.
3. Recrea el contenedor para que reciba los scripts y las variables (los datos no se pierden: el volumen se conserva):
   ```bash
   docker compose up -d
   ```
4. Crea los usuarios y pasa a ellos la propiedad de los esquemas y las tablas:
   ```bash
   docker exec plus-contable-db psql -U postgres -d plus_contable_db -f //docker-entrypoint-initdb.d/01-roles-por-servicio.sql
   ```
   Debe terminar con `Usuarios por servicio creados`. Es repetible sin riesgo.
5. Arranca otra vez `ms-auth` y `ms-tributario` y comprueba las direcciones de salud y el inicio de sesión.

Si algo falla, quita las cuatro variables del `.env` y reinicia los servicios: vuelven a usar `DB_USER` y `DB_PASSWORD`. Los usuarios nuevos pueden quedarse en la base sin molestar.

### Comprobación automática

`AislamientoPorUsuarioPostgresTest` (en `ms-tributario`) crea una base temporal, le aplica los `schema.sql` reales de ambos servicios, ejecuta el script de usuarios y comprueba que `ms-tributario` recibe «permiso denegado» al consultar `auth_schema` y `ms-auth` al consultar `tributario_schema`, y que cada servicio puede ejecutar su propio `schema.sql` con su usuario nuevo. Elimina todo al terminar.

## Sprint 1 · Jeanpierre Miranda
HU-03, HU-04, HU-05, HU-09, HU-10 y HU-38 en `ms-tributario` y `frontend-web`, con sus pruebas. Ya están en `develop` (PR #2); de HU-38 falta la tarea 94, que un cliente no vea datos de otro.

Detalle, tecnologías y estado: [docs/sprint1-jeanpierre/README.md](docs/sprint1-jeanpierre/README.md).
