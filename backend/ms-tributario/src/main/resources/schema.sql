CREATE SCHEMA IF NOT EXISTS tributario_schema;

-- =====================================================================================
-- Este script es idempotente y se ejecuta en cada arranque. Cubre dos casos:
--   1) Base nueva: los CREATE TABLE crean todo.
--   2) Base existente (anterior a la integracion): los ALTER ... IF NOT EXISTS de la
--      seccion "ACTUALIZACION" agregan lo que falta. Nunca elimina tablas, columnas ni datos.
-- =====================================================================================

-- HU-01 (Julio)
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

-- HU-04 (Jeanpierre): cada subida es una version nueva (historial). Se conserva COPIA_LITERAL
-- en el CHECK porque la version anterior la admitia (el codigo de HU-04 solo usa los otros tres).
CREATE TABLE IF NOT EXISTS tributario_schema.documentos_legales (
    id                  BIGSERIAL PRIMARY KEY,
    cliente_id          BIGINT NOT NULL REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    tipo                VARCHAR(30) NOT NULL CHECK (tipo IN ('MINUTA', 'COPIA_LITERAL', 'VIGENCIA_PODERES', 'FICHA_RUC')),
    version             INT NOT NULL DEFAULT 1,
    fecha_emision       DATE NOT NULL,
    nombre_archivo      VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    tamano_bytes        BIGINT NOT NULL,
    ruta_almacenamiento VARCHAR(400) NOT NULL,
    subido_en           TIMESTAMP NOT NULL DEFAULT NOW()
);

-- HU-02 (Julio)
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
    codigo             VARCHAR(40),
    descripcion        VARCHAR(250) NOT NULL,
    categoria          VARCHAR(80),
    valor_adquisicion  NUMERIC(14,2) NOT NULL CHECK (valor_adquisicion >= 0),
    fecha_adquisicion  DATE
);

-- HU-03 (Jeanpierre) + columnas laborales de la version anterior (Julio). Las columnas
-- regimen_laboral, registro_sunafil, registro_mtpe, essalud y quinta_categoria se conservan
-- como opcionales: la entidad de HU-03 no las usa todavia.
CREATE TABLE IF NOT EXISTS tributario_schema.trabajadores (
    id                    BIGSERIAL PRIMARY KEY,
    cliente_id            BIGINT NOT NULL REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    nombre_completo       VARCHAR(200) NOT NULL,
    tipo_documento        VARCHAR(10)  NOT NULL CHECK (tipo_documento IN ('DNI', 'CE', 'PASAPORTE', 'PTP')),
    numero_documento      VARCHAR(20)  NOT NULL,
    extranjero            BOOLEAN NOT NULL DEFAULT FALSE,
    nacionalidad          VARCHAR(60),
    regimen_laboral       VARCHAR(20) CHECK (regimen_laboral IN ('MYPE', 'REMIPE', 'GENERAL')),
    sistema_pensionario   VARCHAR(10) NOT NULL CHECK (sistema_pensionario IN ('ONP', 'AFP')),
    nombre_afp            VARCHAR(60),
    fecha_ingreso         DATE NOT NULL,
    remuneracion          NUMERIC(12,2) CHECK (remuneracion >= 0),
    registro_sunafil      BOOLEAN NOT NULL DEFAULT FALSE,
    registro_mtpe         BOOLEAN NOT NULL DEFAULT FALSE,
    essalud               BOOLEAN NOT NULL DEFAULT FALSE,
    quinta_categoria      BOOLEAN NOT NULL DEFAULT FALSE,
    -- datos de contrato del extranjero (obligatorios si extranjero = TRUE)
    tipo_contrato         VARCHAR(40),
    fecha_inicio_contrato DATE,
    fecha_fin_contrato    DATE
);

-- HU-05 (Jeanpierre): acuerdo de servicio y su historial de honorarios
CREATE TABLE IF NOT EXISTS tributario_schema.acuerdos_servicio (
    id                    BIGSERIAL PRIMARY KEY,
    cliente_id            BIGINT NOT NULL UNIQUE REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    servicios_contratados VARCHAR(1000),
    honorario_mensual     NUMERIC(12,2) NOT NULL CHECK (honorario_mensual > 0),
    honorario_desde       DATE NOT NULL, -- desde cuándo rige el honorario actual
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

-- HU-10 (Jeanpierre): historial de fechas de vencimiento anteriores
CREATE TABLE IF NOT EXISTS tributario_schema.vencimientos_historial (
    id              BIGSERIAL PRIMARY KEY,
    obligacion_id   BIGINT NOT NULL REFERENCES tributario_schema.obligaciones(id) ON DELETE CASCADE,
    fecha_anterior  DATE NOT NULL,
    fecha_nueva     DATE NOT NULL,
    cambiado_en     TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================== ACTUALIZACION DE BASES EXISTENTES ==============================
-- Columnas agregadas en versiones anteriores
ALTER TABLE tributario_schema.clientes ADD COLUMN IF NOT EXISTS telefono VARCHAR(20);
ALTER TABLE tributario_schema.clientes ADD COLUMN IF NOT EXISTS email VARCHAR(150);
ALTER TABLE tributario_schema.activos_fijos ADD COLUMN IF NOT EXISTS codigo VARCHAR(40);
ALTER TABLE tributario_schema.trabajadores ADD COLUMN IF NOT EXISTS essalud BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tributario_schema.trabajadores ADD COLUMN IF NOT EXISTS quinta_categoria BOOLEAN NOT NULL DEFAULT FALSE;

-- HU-03: datos de contrato del extranjero; regimen_laboral deja de ser obligatorio.
-- No se fuerza NOT NULL en fecha_ingreso para no rechazar filas anteriores sin fecha.
ALTER TABLE tributario_schema.trabajadores ADD COLUMN IF NOT EXISTS tipo_contrato VARCHAR(40);
ALTER TABLE tributario_schema.trabajadores ADD COLUMN IF NOT EXISTS fecha_inicio_contrato DATE;
ALTER TABLE tributario_schema.trabajadores ADD COLUMN IF NOT EXISTS fecha_fin_contrato DATE;
ALTER TABLE tributario_schema.trabajadores ALTER COLUMN regimen_laboral DROP NOT NULL;

-- HU-04: version y fecha de emision. Las filas anteriores toman como fecha de emision su fecha de
-- subida y se numeran como versiones 1, 2, ... por cliente y tipo (en orden de id).
ALTER TABLE tributario_schema.documentos_legales ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;
ALTER TABLE tributario_schema.documentos_legales ADD COLUMN IF NOT EXISTS fecha_emision DATE;
UPDATE tributario_schema.documentos_legales SET fecha_emision = subido_en::date WHERE fecha_emision IS NULL;
UPDATE tributario_schema.documentos_legales d SET version = x.n
    FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY cliente_id, tipo ORDER BY id) AS n
          FROM tributario_schema.documentos_legales) x
    WHERE d.id = x.id AND d.version <> x.n;
ALTER TABLE tributario_schema.documentos_legales ALTER COLUMN fecha_emision SET NOT NULL;

-- Unicidad (igual en base nueva y existente)
CREATE UNIQUE INDEX IF NOT EXISTS uq_trabajadores_cliente_documento
    ON tributario_schema.trabajadores(cliente_id, tipo_documento, numero_documento);
CREATE UNIQUE INDEX IF NOT EXISTS uq_documentos_cliente_tipo_version
    ON tributario_schema.documentos_legales(cliente_id, tipo, version);

CREATE INDEX IF NOT EXISTS idx_documentos_cliente ON tributario_schema.documentos_legales(cliente_id);
CREATE INDEX IF NOT EXISTS idx_socios_cliente ON tributario_schema.socios(cliente_id);
CREATE INDEX IF NOT EXISTS idx_activos_cliente ON tributario_schema.activos_fijos(cliente_id);
CREATE INDEX IF NOT EXISTS idx_trabajadores_cliente ON tributario_schema.trabajadores(cliente_id);
CREATE INDEX IF NOT EXISTS idx_obligaciones_cliente ON tributario_schema.obligaciones(cliente_id);
