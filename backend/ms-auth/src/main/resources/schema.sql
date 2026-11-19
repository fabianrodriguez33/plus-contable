CREATE SCHEMA IF NOT EXISTS auth_schema;

CREATE TABLE IF NOT EXISTS auth_schema.roles (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(50) NOT NULL UNIQUE,
    descripcion VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS auth_schema.usuarios (
    id                BIGSERIAL PRIMARY KEY,
    username          VARCHAR(60)  NOT NULL UNIQUE,
    password_hash     VARCHAR(100) NOT NULL,
    nombre_completo   VARCHAR(150) NOT NULL,
    email             VARCHAR(150),
    activo            BOOLEAN      NOT NULL DEFAULT TRUE,
    intentos_fallidos INTEGER      NOT NULL DEFAULT 0,
    bloqueado_hasta   TIMESTAMP,
    creado_en         TIMESTAMP    NOT NULL DEFAULT NOW(),
    cliente_id        BIGINT
);

-- HU-38: cuenta de un cliente MYPE; sin FK porque clientes vive en tributario_schema (otro servicio). Bases existentes:
ALTER TABLE auth_schema.usuarios ADD COLUMN IF NOT EXISTS cliente_id BIGINT;

-- HU-37: una sola cuenta por empresa; la contrasena temporal obliga a cambiarla en el primer ingreso y se gasta al cambiarla (no al entrar).
-- cliente_id solo se llena en las cuentas CLIENTE_MYPE (el estudio lo deja en NULL), por eso el indice unico es parcial.
ALTER TABLE auth_schema.usuarios ADD COLUMN IF NOT EXISTS debe_cambiar_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE auth_schema.usuarios ADD COLUMN IF NOT EXISTS temporal_usada_en TIMESTAMP;
ALTER TABLE auth_schema.usuarios ADD COLUMN IF NOT EXISTS temporal_vence_en TIMESTAMP;
-- HU-37: las cuentas creadas antes de este vencimiento quedaron en NULL (sin vencimiento). Si aun esperan el cambio de contrasena,
-- se les pone el vencimiento AHORA: no se sabe cuando se creo su temporal, asi que no se le da mas tiempo y el contador genera otra
-- con «Restablecer acceso». Idempotente: despues de correr, ya no hay filas en NULL con cambio pendiente.
UPDATE auth_schema.usuarios SET temporal_vence_en = NOW() WHERE debe_cambiar_password = TRUE AND temporal_vence_en IS NULL;
-- HU-37: sube cada vez que el contador restablece el acceso; los tokens con una version anterior dejan de valer en ms-auth.
ALTER TABLE auth_schema.usuarios ADD COLUMN IF NOT EXISTS sesion_version INTEGER NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX IF NOT EXISTS ux_usuarios_cliente_id ON auth_schema.usuarios(cliente_id) WHERE cliente_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS auth_schema.usuario_roles (
    usuario_id BIGINT NOT NULL REFERENCES auth_schema.usuarios(id) ON DELETE CASCADE,
    rol_id     BIGINT NOT NULL REFERENCES auth_schema.roles(id) ON DELETE CASCADE,
    PRIMARY KEY (usuario_id, rol_id)
);

CREATE TABLE IF NOT EXISTS auth_schema.bitacora_seguridad (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT,
    username   VARCHAR(60),
    evento     VARCHAR(40) NOT NULL,
    detalle    VARCHAR(300),
    ip         VARCHAR(60),
    fecha      TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- HU-36 (tarea 2): jti de los tokens cuya sesion se cerro; el filtro de ms-auth los rechaza con 401.
-- expira_en = exp del token: pasado ese momento el token ya no sirve y la fila se puede borrar.
CREATE TABLE IF NOT EXISTS auth_schema.tokens_revocados (
    jti         VARCHAR(64) PRIMARY KEY,
    username    VARCHAR(60) NOT NULL,
    expira_en   TIMESTAMP   NOT NULL,
    revocado_en TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tokens_revocados_expira ON auth_schema.tokens_revocados(expira_en);

-- HU-41: el cliente que olvido su contrasena pide restablecerla desde el login; el estudio la atiende con «Restablecer acceso»
-- (HU-37), que le genera otra temporal (decision del 11/11: el sistema no envia correos). A lo sumo una pendiente por cuenta.
CREATE TABLE IF NOT EXISTS auth_schema.solicitudes_restablecimiento (
    id            BIGSERIAL PRIMARY KEY,
    usuario_id    BIGINT      NOT NULL REFERENCES auth_schema.usuarios(id) ON DELETE CASCADE,
    solicitada_en TIMESTAMP   NOT NULL DEFAULT NOW(),
    ip            VARCHAR(60),
    atendida_en   TIMESTAMP,
    atendida_por  VARCHAR(60),
    CONSTRAINT ck_solicitudes_atendida CHECK ((atendida_en IS NULL) = (atendida_por IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_solicitudes_restablecimiento_pendiente
    ON auth_schema.solicitudes_restablecimiento(usuario_id) WHERE atendida_en IS NULL;

-- HU-41 (revision de Julio): cada pedido, exista o no la cuenta, deja un intento. Sirve para limitar los pedidos por IP y por
-- correo, y hace que la respuesta haga el mismo trabajo en los dos casos. Del correo solo se guarda su huella SHA-256 (no se
-- guardan correos que no son de clientes). Los intentos de mas de un dia se borran solos.
CREATE TABLE IF NOT EXISTS auth_schema.intentos_restablecimiento (
    id             BIGSERIAL PRIMARY KEY,
    correo_huella  CHAR(64)    NOT NULL,
    ip             VARCHAR(60) NOT NULL,
    fecha          TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_intentos_restablecimiento_ip ON auth_schema.intentos_restablecimiento(ip, fecha);
CREATE INDEX IF NOT EXISTS idx_intentos_restablecimiento_correo ON auth_schema.intentos_restablecimiento(correo_huella, fecha);

CREATE INDEX IF NOT EXISTS idx_bitacora_username ON auth_schema.bitacora_seguridad(username);
CREATE INDEX IF NOT EXISTS idx_bitacora_fecha ON auth_schema.bitacora_seguridad(fecha);

INSERT INTO auth_schema.roles (nombre, descripcion) VALUES
    ('ADMIN', 'Administrador del estudio'),
    ('CONTADOR', 'Contador / analista tributario'),
    ('CLIENTE_MYPE', 'Cliente MYPE: solo lectura de su propia empresa')
ON CONFLICT (nombre) DO NOTHING;
