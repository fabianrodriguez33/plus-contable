CREATE SCHEMA IF NOT EXISTS tributario_schema;

-- HU-01 (Julio): copiada de feature/seguridad-jwt solo para tener la FK. No modificar aqui.
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
ALTER TABLE tributario_schema.clientes ADD COLUMN IF NOT EXISTS telefono VARCHAR(20);
ALTER TABLE tributario_schema.clientes ADD COLUMN IF NOT EXISTS email VARCHAR(150);

-- HU-03 · Tarea 34: trabajadores del expediente laboral (nacional / extranjero)
CREATE TABLE IF NOT EXISTS tributario_schema.trabajadores (
    id                    BIGSERIAL PRIMARY KEY,
    cliente_id            BIGINT NOT NULL REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    nombre_completo       VARCHAR(200) NOT NULL,
    tipo_documento        VARCHAR(10)  NOT NULL CHECK (tipo_documento IN ('DNI', 'CE', 'PASAPORTE', 'PTP')),
    numero_documento      VARCHAR(20)  NOT NULL,
    extranjero            BOOLEAN NOT NULL DEFAULT FALSE,
    nacionalidad          VARCHAR(60),
    sistema_pensionario   VARCHAR(10) NOT NULL CHECK (sistema_pensionario IN ('ONP', 'AFP')),
    nombre_afp            VARCHAR(60),
    fecha_ingreso         DATE NOT NULL,
    remuneracion          NUMERIC(12,2) CHECK (remuneracion >= 0),
    -- datos de contrato del extranjero (obligatorios si extranjero = TRUE)
    tipo_contrato         VARCHAR(40),
    fecha_inicio_contrato DATE,
    fecha_fin_contrato    DATE,
    UNIQUE (cliente_id, tipo_documento, numero_documento)
);

-- HU-04 · Tarea 40: documentos del expediente; cada subida es una version nueva (historial)
CREATE TABLE IF NOT EXISTS tributario_schema.documentos_legales (
    id                  BIGSERIAL PRIMARY KEY,
    cliente_id          BIGINT NOT NULL REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    tipo                VARCHAR(30) NOT NULL CHECK (tipo IN ('MINUTA', 'VIGENCIA_PODERES', 'FICHA_RUC')),
    version             INT NOT NULL DEFAULT 1,
    fecha_emision       DATE NOT NULL,
    nombre_archivo      VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    tamano_bytes        BIGINT NOT NULL,
    ruta_almacenamiento VARCHAR(400) NOT NULL,
    subido_en           TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (cliente_id, tipo, version)
);

-- HU-05 · Tarea 46: acuerdo de servicio y su historial de honorarios
CREATE TABLE IF NOT EXISTS tributario_schema.acuerdos_servicio (
    id                    BIGSERIAL PRIMARY KEY,
    cliente_id            BIGINT NOT NULL UNIQUE REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    servicios_contratados VARCHAR(1000),
    honorario_mensual     NUMERIC(12,2) NOT NULL CHECK (honorario_mensual > 0),
    fecha_inicio          DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS tributario_schema.honorarios_historial (
    id             BIGSERIAL PRIMARY KEY,
    acuerdo_id     BIGINT NOT NULL REFERENCES tributario_schema.acuerdos_servicio(id) ON DELETE CASCADE,
    monto          NUMERIC(12,2) NOT NULL,
    vigente_desde  DATE NOT NULL,
    vigente_hasta  DATE NOT NULL
);

-- PROVISIONAL HU-07 (Ricardo): la tabla obligaciones es suya. Solo las columnas de declaracion
-- (fecha_declaracion, numero_formulario, monto_determinado) son de HU-09. Reemplazar al integrar HU-07.
CREATE TABLE IF NOT EXISTS tributario_schema.obligaciones (
    id                 BIGSERIAL PRIMARY KEY,
    cliente_id         BIGINT NOT NULL REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    periodo            DATE NOT NULL, -- primer dia del mes tributario
    fecha_vencimiento  DATE NOT NULL,
    estado             VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'DECLARADO')),
    fecha_declaracion  DATE,
    numero_formulario  VARCHAR(30),
    monto_determinado  NUMERIC(14,2)
);

-- HU-10 · Tarea 68: historial de fechas de vencimiento anteriores
CREATE TABLE IF NOT EXISTS tributario_schema.vencimientos_historial (
    id              BIGSERIAL PRIMARY KEY,
    obligacion_id   BIGINT NOT NULL REFERENCES tributario_schema.obligaciones(id) ON DELETE CASCADE,
    fecha_anterior  DATE NOT NULL,
    fecha_nueva     DATE NOT NULL,
    cambiado_en     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trabajadores_cliente ON tributario_schema.trabajadores(cliente_id);
CREATE INDEX IF NOT EXISTS idx_documentos_cliente ON tributario_schema.documentos_legales(cliente_id);
CREATE INDEX IF NOT EXISTS idx_obligaciones_cliente ON tributario_schema.obligaciones(cliente_id);
