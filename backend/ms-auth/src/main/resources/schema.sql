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
    creado_en         TIMESTAMP    NOT NULL DEFAULT NOW()
);

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

CREATE INDEX IF NOT EXISTS idx_bitacora_username ON auth_schema.bitacora_seguridad(username);
CREATE INDEX IF NOT EXISTS idx_bitacora_fecha ON auth_schema.bitacora_seguridad(fecha);

INSERT INTO auth_schema.roles (nombre, descripcion) VALUES
    ('ADMIN', 'Administrador del estudio'),
    ('CONTADOR', 'Contador / analista tributario')
ON CONFLICT (nombre) DO NOTHING;

CREATE TABLE IF NOT EXISTS auth_schema.claves_sol (
    id                  BIGSERIAL PRIMARY KEY,
    ruc                 VARCHAR(11)  NOT NULL UNIQUE,
    usuario_sol_cifrado VARCHAR(500) NOT NULL,
    clave_sol_cifrada   VARCHAR(500) NOT NULL,
    actualizado_en      TIMESTAMP    NOT NULL DEFAULT NOW()
);
