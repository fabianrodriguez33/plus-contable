CREATE SCHEMA IF NOT EXISTS tributario_schema;

CREATE TABLE IF NOT EXISTS tributario_schema.clientes (
    id                 BIGSERIAL PRIMARY KEY,
    ruc                VARCHAR(11)  NOT NULL UNIQUE CHECK (ruc ~ '^[0-9]{11}$'),
    razon_social       VARCHAR(200) NOT NULL,
    regimen_tributario VARCHAR(20)  NOT NULL CHECK (regimen_tributario IN ('MYPE', 'REMIPE', 'GENERAL')),
    condicion_habido   VARCHAR(20)  NOT NULL DEFAULT 'HABIDO' CHECK (condicion_habido IN ('HABIDO', 'NO_HABIDO')),
    direccion          VARCHAR(250),
    activo             BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tributario_schema.credenciales_sol (
    id                  BIGSERIAL PRIMARY KEY,
    cliente_id          BIGINT NOT NULL UNIQUE REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    usuario_sol_cifrado VARCHAR(300) NOT NULL,
    clave_sol_cifrada   VARCHAR(300) NOT NULL,
    actualizado_en      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tributario_schema.documentos_legales (
    id                 BIGSERIAL PRIMARY KEY,
    cliente_id         BIGINT NOT NULL REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    tipo               VARCHAR(30) NOT NULL CHECK (tipo IN ('MINUTA', 'COPIA_LITERAL', 'VIGENCIA_PODERES', 'FICHA_RUC')),
    nombre_archivo     VARCHAR(255) NOT NULL,
    content_type       VARCHAR(100),
    tamano_bytes       BIGINT NOT NULL,
    ruta_almacenamiento VARCHAR(400) NOT NULL,
    subido_en          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tributario_schema.socios (
    id               BIGSERIAL PRIMARY KEY,
    cliente_id       BIGINT NOT NULL REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    nombre_completo  VARCHAR(200) NOT NULL,
    tipo_documento   VARCHAR(10)  NOT NULL DEFAULT 'DNI',
    numero_documento VARCHAR(20)  NOT NULL,
    porcentaje       NUMERIC(5,2) NOT NULL CHECK (porcentaje > 0 AND porcentaje <= 100),
    cargo            VARCHAR(80)
);

CREATE TABLE IF NOT EXISTS tributario_schema.activos_fijos (
    id                 BIGSERIAL PRIMARY KEY,
    cliente_id         BIGINT NOT NULL REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    descripcion        VARCHAR(250) NOT NULL,
    categoria          VARCHAR(80),
    valor_adquisicion  NUMERIC(14,2) NOT NULL CHECK (valor_adquisicion >= 0),
    fecha_adquisicion  DATE
);

CREATE TABLE IF NOT EXISTS tributario_schema.trabajadores (
    id                  BIGSERIAL PRIMARY KEY,
    cliente_id          BIGINT NOT NULL REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    nombre_completo     VARCHAR(200) NOT NULL,
    tipo_documento      VARCHAR(10)  NOT NULL DEFAULT 'DNI',
    numero_documento    VARCHAR(20)  NOT NULL,
    extranjero          BOOLEAN NOT NULL DEFAULT FALSE,
    nacionalidad        VARCHAR(60),
    regimen_laboral     VARCHAR(20) NOT NULL CHECK (regimen_laboral IN ('MYPE', 'REMIPE', 'GENERAL')),
    sistema_pensionario VARCHAR(10) NOT NULL CHECK (sistema_pensionario IN ('AFP', 'ONP')),
    nombre_afp          VARCHAR(60),
    fecha_ingreso       DATE,
    remuneracion        NUMERIC(12,2) CHECK (remuneracion >= 0),
    registro_sunafil    BOOLEAN NOT NULL DEFAULT FALSE,
    registro_mtpe       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_documentos_cliente ON tributario_schema.documentos_legales(cliente_id);
CREATE INDEX IF NOT EXISTS idx_socios_cliente ON tributario_schema.socios(cliente_id);
CREATE INDEX IF NOT EXISTS idx_activos_cliente ON tributario_schema.activos_fijos(cliente_id);
CREATE INDEX IF NOT EXISTS idx_trabajadores_cliente ON tributario_schema.trabajadores(cliente_id);
