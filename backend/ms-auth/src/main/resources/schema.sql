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

CREATE INDEX IF NOT EXISTS idx_bitacora_username ON auth_schema.bitacora_seguridad(username);
CREATE INDEX IF NOT EXISTS idx_bitacora_fecha ON auth_schema.bitacora_seguridad(fecha);

INSERT INTO auth_schema.roles (nombre, descripcion) VALUES
    ('ADMIN', 'Administrador del estudio'),
    ('CONTADOR', 'Contador / analista tributario'),
    ('CLIENTE_MYPE', 'Cliente MYPE: solo lectura de su propia empresa')
ON CONFLICT (nombre) DO NOTHING;
