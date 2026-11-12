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
    regimen_tributario VARCHAR(20)  NOT NULL CHECK (regimen_tributario IN ('MYPE', 'RER', 'GENERAL')),
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
    aporte           NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (aporte >= 0),
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

-- HU-07 amplía la tabla provisional sin alterar las columnas de declaración de HU-09
-- (fecha_declaracion, numero_formulario, monto_determinado) ni el historial de HU-10.
CREATE TABLE IF NOT EXISTS tributario_schema.obligaciones (
    id                 BIGSERIAL PRIMARY KEY,
    cliente_id         BIGINT NOT NULL REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    tipo_obligacion    VARCHAR(30),
    periodo            DATE NOT NULL, -- primer dia del mes tributario
    fecha_vencimiento  DATE NOT NULL,
    estado             VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'DECLARADO')),
    fecha_declaracion  DATE,
    numero_formulario  VARCHAR(30),
    monto_determinado  NUMERIC(14,2)
);

-- HU-07: cronograma mensual de SUNAT; se carga desde el CSV versionado, sin asignar tipos.
CREATE TABLE IF NOT EXISTS tributario_schema.cronograma_tributario (
    periodo_tributario DATE NOT NULL CHECK (EXTRACT(DAY FROM periodo_tributario) = 1),
    ultimo_digito_ruc  VARCHAR(20) NOT NULL,
    fecha_vencimiento  DATE NOT NULL,
    CONSTRAINT uq_cronograma_periodo_grupo UNIQUE (periodo_tributario, ultimo_digito_ruc)
);

-- HU-10 (Jeanpierre): historial de fechas de vencimiento anteriores
CREATE TABLE IF NOT EXISTS tributario_schema.vencimientos_historial (
    id              BIGSERIAL PRIMARY KEY,
    obligacion_id   BIGINT NOT NULL REFERENCES tributario_schema.obligaciones(id) ON DELETE CASCADE,
    fecha_anterior  DATE NOT NULL,
    fecha_nueva     DATE NOT NULL,
    cambiado_en     TIMESTAMP NOT NULL DEFAULT NOW()
);

-- HU-17 (Ricardo): fraccionamiento tributario vigente de un cliente y sus cuotas. El calendario lee las cuotas por fecha;
-- una cuota PENDIENTE con fecha anterior a hoy es la que se resalta como riesgo (no se guarda un estado «vencida»).
CREATE TABLE IF NOT EXISTS tributario_schema.fraccionamientos (
    id                  BIGSERIAL PRIMARY KEY,
    cliente_id          BIGINT NOT NULL REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    tributo             VARCHAR(30) NOT NULL
                        CHECK (tributo IN ('IGV', 'RENTA', 'PLAME', 'ESSALUD', 'RETENCION_QUINTA', 'AFP')),
    monto_total         NUMERIC(14,2) NOT NULL CHECK (monto_total > 0),
    numero_cuotas       INTEGER NOT NULL CHECK (numero_cuotas >= 2),
    fecha_primera_cuota DATE NOT NULL,
    registrado_en       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tributario_schema.cuotas_fraccionamiento (
    id                 BIGSERIAL PRIMARY KEY,
    fraccionamiento_id BIGINT NOT NULL REFERENCES tributario_schema.fraccionamientos(id) ON DELETE CASCADE,
    numero             INTEGER NOT NULL CHECK (numero >= 1),
    fecha_vencimiento  DATE NOT NULL,
    monto              NUMERIC(14,2) NOT NULL CHECK (monto > 0),
    estado             VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'PAGADA')),
    fecha_pago         DATE,
    CONSTRAINT uq_cuotas_fraccionamiento_numero UNIQUE (fraccionamiento_id, numero)
);

CREATE INDEX IF NOT EXISTS idx_fraccionamientos_cliente ON tributario_schema.fraccionamientos(cliente_id);
CREATE INDEX IF NOT EXISTS idx_cuotas_fraccionamiento_fecha ON tributario_schema.cuotas_fraccionamiento(fecha_vencimiento, estado);

-- HU-18 (Julio): notificaciones del Buzón SOL (esquela, carta inductiva o comunicado) de un cliente.
-- El plazo es obligatorio: sin plazo de respuesta no se guarda.
CREATE TABLE IF NOT EXISTS tributario_schema.notificaciones_sol (
    id               BIGSERIAL PRIMARY KEY,
    cliente_id       BIGINT NOT NULL REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    tipo             VARCHAR(20) NOT NULL CHECK (tipo IN ('ESQUELA', 'CARTA_INDUCTIVA', 'COMUNICADO')),
    fecha_recepcion  DATE NOT NULL,
    plazo_respuesta  DATE NOT NULL,
    estado           VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'ATENDIDA')),
    fecha_atencion   DATE,
    registrado_en    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_notificaciones_plazo CHECK (plazo_respuesta >= fecha_recepcion)
);

CREATE INDEX IF NOT EXISTS idx_notificaciones_cliente_estado_plazo
    ON tributario_schema.notificaciones_sol(cliente_id, estado, plazo_respuesta);

-- HU-13 (Julio): pagos de una obligación ya declarada. Varios pagos parciales hasta saldar; cada pago guarda su
-- comprobante (PDF en disco, como los documentos de HU-04) y el acumulado y el saldo que quedaron después de él.
CREATE TABLE IF NOT EXISTS tributario_schema.pagos_obligacion (
    id                  BIGSERIAL PRIMARY KEY,
    obligacion_id       BIGINT NOT NULL REFERENCES tributario_schema.obligaciones(id) ON DELETE CASCADE,
    fecha_pago          DATE NOT NULL,
    monto_pagado        NUMERIC(14,2) NOT NULL CHECK (monto_pagado > 0),
    numero_constancia   VARCHAR(30) NOT NULL,
    nombre_archivo      VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
    tamano_bytes        BIGINT NOT NULL,
    ruta_almacenamiento VARCHAR(400) NOT NULL,
    acumulado_despues   NUMERIC(14,2) NOT NULL,
    saldo_despues       NUMERIC(14,2) NOT NULL CHECK (saldo_despues >= 0),
    registrado_en       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pagos_obligacion_constancia UNIQUE (obligacion_id, numero_constancia)
);

CREATE INDEX IF NOT EXISTS idx_pagos_obligacion_fecha
    ON tributario_schema.pagos_obligacion(obligacion_id, fecha_pago);

-- HU-23: incidencias del servicio contable. Una incidencia se asocia a los clientes que afecta (una fila por cliente en
-- incidencias_clientes): la de un cliente tiene una fila y una general (caida del portal SOL) una por cada cliente activo.
CREATE TABLE IF NOT EXISTS tributario_schema.incidencias (
    id              BIGSERIAL PRIMARY KEY,
    tipo            VARCHAR(40) NOT NULL
                    CHECK (tipo IN ('CAIDA_PORTAL_SOL', 'CLIENTE_NO_ENVIA_DOCUMENTOS', 'RECHAZO_FRACCIONAMIENTO', 'PROBLEMA_SIRE',
                                'PROBLEMA_SERVICIOS_EXTERNOS', 'FALLA_SISTEMA_INTERNO', 'PROBLEMA_CONEXION_INTERNET',
                                'ERROR_PROCESO_CONTABLE', 'OTRO')),
    fecha           DATE NOT NULL,
    descripcion     VARCHAR(1000) NOT NULL CHECK (length(btrim(descripcion)) > 0),
    observaciones   VARCHAR(1000),
    registrada_por  VARCHAR(60) NOT NULL,
    registrada_en   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tributario_schema.incidencias_clientes (
    incidencia_id   BIGINT NOT NULL REFERENCES tributario_schema.incidencias(id) ON DELETE CASCADE,
    cliente_id      BIGINT NOT NULL REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    PRIMARY KEY (incidencia_id, cliente_id)
);

CREATE INDEX IF NOT EXISTS idx_incidencias_clientes_cliente ON tributario_schema.incidencias_clientes(cliente_id);

-- HU-28 (Jeanpierre): programación del cobro periódico de honorarios (una por cliente) y los cobros que genera.
-- HU-29 amplía el CHECK de estado con el pago y agrega la columna pagado (en la sección de actualización).
CREATE TABLE IF NOT EXISTS tributario_schema.programaciones_cobro (
    id              BIGSERIAL PRIMARY KEY,
    cliente_id      BIGINT NOT NULL UNIQUE REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    periodicidad    VARCHAR(10) NOT NULL CHECK (periodicidad IN ('MENSUAL', 'QUINCENAL')),
    creada_en       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tributario_schema.cobros_honorarios (
    id               BIGSERIAL PRIMARY KEY,
    cliente_id       BIGINT NOT NULL REFERENCES tributario_schema.clientes(id) ON DELETE CASCADE,
    programacion_id  BIGINT NOT NULL REFERENCES tributario_schema.programaciones_cobro(id) ON DELETE CASCADE,
    fecha_cobro      DATE NOT NULL,
    monto            NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    estado           VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE')),
    CONSTRAINT uq_cobros_honorarios_cliente_fecha UNIQUE (cliente_id, fecha_cobro)
);

CREATE INDEX IF NOT EXISTS idx_cobros_honorarios_pendientes
    ON tributario_schema.cobros_honorarios(cliente_id, estado, fecha_cobro);

-- HU-29 (Jeanpierre): pagos recibidos de un cobro de honorarios. Varios pagos parciales hasta saldar el cobro; el cobro
-- guarda lo pagado (columna pagado, agregada abajo) y pasa a PAGO_PARCIAL o PAGADO.
CREATE TABLE IF NOT EXISTS tributario_schema.pagos_honorarios (
    id              BIGSERIAL PRIMARY KEY,
    cobro_id        BIGINT NOT NULL REFERENCES tributario_schema.cobros_honorarios(id) ON DELETE CASCADE,
    fecha_pago      DATE NOT NULL,
    monto           NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    medio_pago      VARCHAR(20) NOT NULL
                    CHECK (medio_pago IN ('EFECTIVO', 'TRANSFERENCIA', 'DEPOSITO', 'YAPE_PLIN', 'OTRO')),
    -- Decisión de Beto (11/11): con «Otro» se especifica el medio; con los demás queda vacío.
    medio_pago_detalle VARCHAR(100),
    registrado_por  VARCHAR(60) NOT NULL,
    registrado_en   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_pagos_honorarios_detalle CHECK (
        (medio_pago = 'OTRO') = (medio_pago_detalle IS NOT NULL AND length(btrim(medio_pago_detalle)) > 0))
);

CREATE INDEX IF NOT EXISTS idx_pagos_honorarios_cobro ON tributario_schema.pagos_honorarios(cobro_id, fecha_pago);

-- ============================== ACTUALIZACION DE BASES EXISTENTES ==============================
-- Columnas agregadas en versiones anteriores
ALTER TABLE tributario_schema.clientes ADD COLUMN IF NOT EXISTS telefono VARCHAR(20);
-- Sin valor por defecto: los registros anteriores requieren clasificación confirmada.
ALTER TABLE tributario_schema.obligaciones ADD COLUMN IF NOT EXISTS tipo_obligacion VARCHAR(30);
-- Se recrea con nombre estable para cubrir tanto tablas nuevas como anteriores.
-- NULL conserva las obligaciones antiguas sin asignarles un tipo ficticio.
ALTER TABLE tributario_schema.obligaciones DROP CONSTRAINT IF EXISTS ck_obligaciones_tipo;
ALTER TABLE tributario_schema.obligaciones ADD CONSTRAINT ck_obligaciones_tipo
    CHECK (tipo_obligacion IN ('IGV', 'RENTA', 'PLAME', 'ESSALUD', 'RETENCION_QUINTA', 'AFP'));
-- HU-13: el estado admite PAGO_PARCIAL y PAGADO. Se borra primero la restricción en línea sin nombre (la que PostgreSQL
-- llamó obligaciones_estado_check) y se recrea con nombre estable, como ck_obligaciones_tipo.
ALTER TABLE tributario_schema.obligaciones DROP CONSTRAINT IF EXISTS obligaciones_estado_check;
ALTER TABLE tributario_schema.obligaciones DROP CONSTRAINT IF EXISTS ck_obligaciones_estado;
ALTER TABLE tributario_schema.obligaciones ADD CONSTRAINT ck_obligaciones_estado
    CHECK (estado IN ('PENDIENTE', 'DECLARADO', 'PAGO_PARCIAL', 'PAGADO'));
-- HU-29: el cobro guarda lo pagado (0 en los cobros anteriores) y su estado admite PAGO_PARCIAL y PAGADO. Igual que en
-- HU-13, se borra la restricción en línea sin nombre (cobros_honorarios_estado_check) y se recrea con nombre estable.
ALTER TABLE tributario_schema.cobros_honorarios ADD COLUMN IF NOT EXISTS pagado NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE tributario_schema.cobros_honorarios DROP CONSTRAINT IF EXISTS cobros_honorarios_estado_check;
ALTER TABLE tributario_schema.cobros_honorarios DROP CONSTRAINT IF EXISTS ck_cobros_honorarios_estado;
ALTER TABLE tributario_schema.cobros_honorarios ADD CONSTRAINT ck_cobros_honorarios_estado
    CHECK (estado IN ('PENDIENTE', 'PAGO_PARCIAL', 'PAGADO'));
ALTER TABLE tributario_schema.cobros_honorarios DROP CONSTRAINT IF EXISTS ck_cobros_honorarios_pagado;
ALTER TABLE tributario_schema.cobros_honorarios ADD CONSTRAINT ck_cobros_honorarios_pagado
    CHECK (pagado >= 0 AND pagado <= monto);
-- Régimen: RER (Régimen Especial del Impuesto a la Renta) reemplaza a REMIPE (decisión de Beto, 10/11). Orden obligatorio: primero se
-- borra la restricción vieja (la en línea sin nombre, que PostgreSQL llamó clientes_regimen_tributario_check, o la ya renombrada),
-- luego los clientes que estaban en REMIPE pasan a RER sin perder ningún otro dato y recién entonces se crea la restricción nueva
-- con nombre estable. La condición (condicion_habido) es otra columna y no se toca. regimen_laboral de trabajadores no cambia.
ALTER TABLE tributario_schema.clientes DROP CONSTRAINT IF EXISTS clientes_regimen_tributario_check;
ALTER TABLE tributario_schema.clientes DROP CONSTRAINT IF EXISTS ck_clientes_regimen;
UPDATE tributario_schema.clientes SET regimen_tributario = 'RER' WHERE regimen_tributario = 'REMIPE';
ALTER TABLE tributario_schema.clientes ADD CONSTRAINT ck_clientes_regimen
    CHECK (regimen_tributario IN ('MYPE', 'RER', 'GENERAL'));
ALTER TABLE tributario_schema.clientes ADD COLUMN IF NOT EXISTS email VARCHAR(150);
-- HU-07: marca manual de buen contribuyente (BUEN_CONTRIB_UESP); los clientes existentes quedan en FALSE.
ALTER TABLE tributario_schema.clientes ADD COLUMN IF NOT EXISTS buen_contribuyente BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tributario_schema.activos_fijos ADD COLUMN IF NOT EXISTS codigo VARCHAR(40);
ALTER TABLE tributario_schema.trabajadores ADD COLUMN IF NOT EXISTS essalud BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tributario_schema.trabajadores ADD COLUMN IF NOT EXISTS quinta_categoria BOOLEAN NOT NULL DEFAULT FALSE;

-- HU-23: los tipos de incidencia de la lista de Beto y las observaciones. Se borra primero la restriccion en linea sin nombre
-- (incidencias_tipo_check) y se recrea con nombre estable, como ck_obligaciones_tipo; las incidencias anteriores no cambian.
ALTER TABLE tributario_schema.incidencias DROP CONSTRAINT IF EXISTS incidencias_tipo_check;
ALTER TABLE tributario_schema.incidencias DROP CONSTRAINT IF EXISTS ck_incidencias_tipo;
ALTER TABLE tributario_schema.incidencias ADD CONSTRAINT ck_incidencias_tipo
    CHECK (tipo IN ('CAIDA_PORTAL_SOL', 'CLIENTE_NO_ENVIA_DOCUMENTOS', 'RECHAZO_FRACCIONAMIENTO', 'PROBLEMA_SIRE',
                                'PROBLEMA_SERVICIOS_EXTERNOS', 'FALLA_SISTEMA_INTERNO', 'PROBLEMA_CONEXION_INTERNET',
                                'ERROR_PROCESO_CONTABLE', 'OTRO'));
ALTER TABLE tributario_schema.incidencias ADD COLUMN IF NOT EXISTS observaciones VARCHAR(1000);

-- HU-02: aporte de cada socio (S/). Los socios anteriores quedan con aporte 0 hasta que se edite.
ALTER TABLE tributario_schema.socios ADD COLUMN IF NOT EXISTS aporte NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (aporte >= 0);

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
-- PostgreSQL permite múltiples NULL: protege las obligaciones nuevas sin inventar tipos antiguos.
CREATE UNIQUE INDEX IF NOT EXISTS uq_obligaciones_cliente_tipo_periodo
    ON tributario_schema.obligaciones(cliente_id, tipo_obligacion, periodo);

CREATE INDEX IF NOT EXISTS idx_documentos_cliente ON tributario_schema.documentos_legales(cliente_id);
CREATE INDEX IF NOT EXISTS idx_socios_cliente ON tributario_schema.socios(cliente_id);
CREATE INDEX IF NOT EXISTS idx_activos_cliente ON tributario_schema.activos_fijos(cliente_id);
CREATE INDEX IF NOT EXISTS idx_trabajadores_cliente ON tributario_schema.trabajadores(cliente_id);
CREATE INDEX IF NOT EXISTS idx_obligaciones_cliente ON tributario_schema.obligaciones(cliente_id);
