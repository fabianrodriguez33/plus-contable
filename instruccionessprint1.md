# PROMPT MAESTRO DE GENERACIÓN DE CÓDIGO - SPRINT 1
# PROYECTO: PLUS CONTABLE - SISTEMA DE CONTROL TRIBUTARIO MYPE
# STACK: Spring Boot 3.2+ (Java 21) + React 18 (TypeScript + Vite) + PostgreSQL 16 + Docker Compose

---

> **INSTRUCCIÓN MAESTRA PARA CLAUDE (SONNET / OPUS):**
> Actúa como **Arquitecto de Software Lead y Desarrollador Senior Fullstack**. Tu tarea es generar la totalidad del código fuente ejecutable para el **Sprint 1 (39 Puntos de Historia)** del sistema **PLUS CONTABLE**.
> 
> **REGLAS DE GENERACIÓN OBLIGATORIAS:**
> 1. **CÓDIGO 100% COMPLETO Y FUNCIONAL:** Prohibido usar `// TODO`, pseudocódigo, arreglos incompletos o métodos stub vacíos. Toda la lógica de negocio, controladores, servicios, repositorios, seguridad JWT, cifrado AES-256 y formularios React debe estar implementada línea por línea.
> 2. **SIN PREÁMBULOS NI RELLENO:** Comienza directamente generando los archivos de código en el orden estructurado.
> 3. **RESPONDE EN FORMATO DE ARCHIVOS:** Presenta cada archivo con su ruta exacta en el monorepo (ejemplo: ````yaml path=docker-compose.yml`).

---

## 1. REQUERIMIENTOS TÉCNICOS Y FUNCIONALES DEL SPRINT 1 (39 PUNTOS)

El Sprint 1 implementa 6 Historias de Usuario / Técnicas con valor de negocio real para el estudio contable *V&F Asesores Empresarial*:

1. **ST-01: Infraestructura de Seguridad JWT + AES-256 y `auth_schema` (8 pts):**
   * Esquema PostgreSQL `auth_schema` con tablas `usuarios`, `roles`, `usuario_roles` y `bitacora_seguridad`.
   * Cifrado simétrico AES-256 para Claves SOL en reposo.
   * Filtro de autenticación JWT y configuración de Spring Security 3.x.
2. **US-01: Inicio de Sesión Seguro con Control de Roles (5 pts):**
   * Endpoint `/api/auth/login` con generación de JWT (vigencia 8 horas).
   * Control de intentos fallidos (bloqueo automático al 5to intento por 15 min).
3. **US-02: Custodia Cifrada de Claves SOL (5 pts):**
   * Guardado y recuperación desencriptada en memoria de credenciales SUNAT (Usuario SOL y Clave SOL).
4. **US-03: Alta de Cliente con Datos RUC y Régimen (5 pts):**
   * Esquema PostgreSQL `tributario_schema` con tabla `clientes` (RUC 11 dígitos, Razón Social, Régimen Tributario: MYPE/REMIPE/General, Condición Habido).
   * Validación backend de RUC único de 11 dígitos numéricos.
5. **US-04: Expediente Documental Legal del Cliente (8 pts):**
   * Tabla `documentos_legales` (asociación de Minuta, Copia Literal, Vigencia de Poderes y Ficha RUC en PDF/archivos).
6. **US-05: Registro Societario, Patrimonial y Laboral (8 pts):**
   * Tablas `socios` (validación estricta de que la suma de `%` de participación sea exactamente 100.00%), `activos_fijos` y `trabajadores` (soporte para régimen MYPE/REMIPE, trabajadores extranjeros, AFP/ONP y registro SUNAFIL/MTPE).

---

## 2. ESTRUCTURA DEL MONOREPO A GENERAR

```text
plus-contable/
├── docker-compose.yml
├── backend/
│   ├── ms-auth/
│   │   ├── pom.xml
│   │   └── src/main/
│   │       ├── resources/
│   │       │   ├── application.yml
│   │       │   └── schema.sql
│   │       └── java/com/pluscontable/auth/
│   │           ├── MsAuthApplication.java
│   │           ├── config/ (SecurityConfig, JwtProvider, JwtFilter, AesUtil)
│   │           ├── controller/ (AuthController)
│   │           ├── dto/ (LoginRequest, LoginResponse, UserDto)
│   │           ├── model/ (Usuario, Rol, BitacoraSeguridad)
│   │           ├── repository/ (UsuarioRepository, BitacoraRepository)
│   │           └── service/ (AuthService, EncryptionService)
│   └── ms-tributario/
│       ├── pom.xml
│       └── src/main/
│           ├── resources/
│           │   ├── application.yml
│           │   └── schema.sql
│           └── java/com/pluscontable/tributario/
│               ├── MsTributarioApplication.java
│               ├── config/ (SecurityConfig, JwtFilter)
│               ├── controller/ (ClienteController, DocumentoController, SocioController, TrabajadorController)
│               ├── dto/ (ClienteDto, SocioDto, TrabajadorDto, DocumentoDto)
│               ├── model/ (Cliente, Socio, ActivoFijo, Trabajador, DocumentoLegal)
│               ├── repository/ (ClienteRepository, SocioRepository, TrabajadorRepository, DocumentoRepository)
│               └── service/ (ClienteService, ExpedienteService)
└── frontend-web/
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    ├── index.html
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── services/ (apiClient.ts, authService.ts, clienteService.ts)
        ├── components/ (Navbar.tsx, PrivateRoute.tsx)
        └── pages/
            ├── LoginPage.tsx
            ├── DashboardPage.tsx
            └── FichaMaestraPage.tsx (Formulario multi-pestaña MYPE)
```

---

## 3. ESPECIFICACIÓN DETALLADA DE IMPLEMENTACIÓN

### A. Base de Datos (PostgreSQL 16)
Genera en `docker-compose.yml` la instancia con credenciales `POSTGRES_USER=postgres`, `POSTGRES_PASSWORD=postgres`, `POSTGRES_DB=plus_contable_db` en el puerto `5432`.

### B. Microservicio `ms-auth` (Puerto 8081)
* **`schema.sql`:** Crea `auth_schema` con sus secuencias, tablas de usuarios, roles e índices.
* **Seguridad:** `SecurityConfig` con BCrypt, Stateless Session, soporte CORS amplio para `localhost:5173`.
* **Cifrado AES-256:** `AesUtil` utilizando algoritmo `AES/CBC/PKCS5Padding` con SecretKey e IV parametrizados en `application.yml`.

### C. Microservicio `ms-tributario` (Puerto 8082)
* **`schema.sql`:** Crea `tributario_schema` con tablas de clientes, credenciales SOL cifradas, socios, activos, trabajadores y expediente digital.
* **Validación Societaria:** En `SocioService`, verifica que al registrar/actualizar socios de un cliente, la suma de porcentajes sea exactamente `100.00%`. Si no, arroja `ValidationException`.
* **Validación RUC:** Regex de 11 dígitos numéricos (`^[0-9]{11}$`).

### D. Frontend Web (React + TypeScript + Vite)
* **Axios Interceptor (`apiClient.ts`):** Extrae automáticamente el token JWT de `localStorage` y lo adjunta en los headers `Authorization: Bearer <token>`.
* **UI Funcional Conectada:**
  * `LoginPage.tsx`: Formulario de login con manejo de errores y redirección.
  * `FichaMaestraPage.tsx`: Vista detallada con pestañas:
    1. **Datos Generales:** RUC, Razón Social, Régimen Tributario, Dirección, Clave SOL (campo Password cifrado).
    2. **Expediente Legal:** Carga de Minuta, Copia Literal, Vigencia de Poderes.
    3. **Estructura Societaria y Laboral:** Tablas interactivas para agregar Socios (% participación) y Trabajadores (nacionales/extranjeros, régimen MYPE/REMIPE, AFP/ONP).

---

## 4. INSTRUCCIONES DE EJECUCIÓN
Genera el código archivo por archivo en orden secuencial. Si el volumen de código sobrepasa el límite de respuesta, completa el archivo actual de forma limpia para continuar en el siguiente turno.

**¡COMIENZA AHORA MISMO CON EL ARCHIVO `docker-compose.yml`!**
