-- Datos de DEMOSTRACION de Plus Contable. Todo es ficticio: nombres, RUC (con digito verificador valido pero inventados;
-- podrian coincidir por casualidad con uno real, no sirven para tramites) y fechas (relativas a hoy).
--
-- NO lleva contrasenas: las dos cuentas demo reciben las que genera db/demo/cargar-demo.ps1 al ejecutarse, que llegan por las
-- variables de entorno DEMO_PW_CONTADOR y DEMO_PW_CLIENTE y no se guardan en ningun archivo.
-- Requisito: ms-auth y ms-tributario ya arrancaron al menos una vez contra esta base (crean sus tablas y los roles).
-- Se puede repetir: los clientes y las obligaciones no se duplican y las cuentas demo reciben contrasenas nuevas.
\set ON_ERROR_STOP on
\getenv pw_contador DEMO_PW_CONTADOR
\getenv pw_cliente DEMO_PW_CLIENTE
SELECT (:'pw_contador' <> '' AND :'pw_cliente' <> '') AS listo \gset
\if :listo
\else
    \echo 'Faltan DEMO_PW_CONTADOR o DEMO_PW_CLIENTE: use db/demo/cargar-demo.ps1'
    \quit
\endif

SET client_min_messages = warning; -- sin avisos NOTICE: PowerShell los trata como error
CREATE EXTENSION IF NOT EXISTS pgcrypto;
BEGIN;

-- ---- ms-tributario: clientes ficticios (el primero es el que usa la cuenta de cliente demo)
INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario, direccion, telefono, email) VALUES
    ('20999000011', '(DEMO) Panaderia El Trigal SAC',        'MYPE',    'Av. Ejemplo 123, Lima',   '900000001', 'trigal@ejemplo.invalid'),
    ('20999000020', '(DEMO) Ferreteria Los Andes EIRL',      'MYPE',    'Jr. Modelo 456, Cusco',   '900000002', 'andes@ejemplo.invalid'),
    ('20999000038', '(DEMO) Consultora Horizonte SAC',       'RER',     'Calle Prueba 789, Lima',  '900000003', 'horizonte@ejemplo.invalid'),
    ('20999000046', '(DEMO) Transportes Rapido Norte SRL',   'GENERAL', 'Av. Falsa 321, Trujillo', '900000004', 'norte@ejemplo.invalid'),
    ('20999000054', '(DEMO) Bodega La Esquina EIRL',         'MYPE',    'Psje. Inventado 654, Ica', NULL,       NULL)
ON CONFLICT (ruc) DO NOTHING;

INSERT INTO tributario_schema.socios (cliente_id, nombre_completo, tipo_documento, numero_documento, aporte, porcentaje, cargo)
SELECT c.id, s.nombre, 'DNI', s.doc, s.aporte, s.pct, s.cargo
FROM tributario_schema.clientes c
JOIN (VALUES
    ('20999000011', 'Ana Ejemplo Prueba',  '00000001', 5000, 60.00, 'Gerente general'),
    ('20999000011', 'Luis Modelo Demo',    '00000002', 3333, 40.00, NULL),
    ('20999000020', 'Rosa Ficticia Demo',  '00000003', 8000, 100.00, 'Titular gerente')
) AS s(ruc, nombre, doc, aporte, pct, cargo) ON s.ruc = c.ruc
WHERE NOT EXISTS (SELECT 1 FROM tributario_schema.socios x WHERE x.cliente_id = c.id AND x.numero_documento = s.doc);

-- Obligaciones del mes anterior: unas por vencer pronto, una vencida y una ya declarada (fechas relativas a hoy)
INSERT INTO tributario_schema.obligaciones (cliente_id, tipo_obligacion, periodo, fecha_vencimiento, estado, fecha_declaracion, numero_formulario)
SELECT c.id, o.tipo, date_trunc('month', current_date - interval '1 month')::date, current_date + o.dias, o.estado,
       CASE WHEN o.estado = 'DECLARADO' THEN current_date - 1 END, CASE WHEN o.estado = 'DECLARADO' THEN '0000000001' END
FROM tributario_schema.clientes c
JOIN (VALUES
    ('20999000011', 'IGV',    2, 'PENDIENTE'),
    ('20999000011', 'RENTA',  9, 'PENDIENTE'),
    ('20999000011', 'PLAME', -3, 'DECLARADO'),
    ('20999000020', 'IGV',   -2, 'PENDIENTE'),
    ('20999000038', 'IGV',    5, 'PENDIENTE'),
    ('20999000046', 'IGV',    1, 'PENDIENTE')
) AS o(ruc, tipo, dias, estado) ON o.ruc = c.ruc
ON CONFLICT DO NOTHING;

-- ---- ms-auth: un contador demo y un cliente MYPE demo (la contrasena se cifra con bcrypt, como hace Spring: prefijo $2a$)
INSERT INTO auth_schema.usuarios (username, password_hash, nombre_completo, email, debe_cambiar_password)
VALUES ('contador.demo', crypt(:'pw_contador', gen_salt('bf', 10)), 'Contador Demo', 'contador.demo@ejemplo.invalid', FALSE)
ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash, intentos_fallidos = 0, bloqueado_hasta = NULL, activo = TRUE;

INSERT INTO auth_schema.usuarios (username, password_hash, nombre_completo, email, debe_cambiar_password, cliente_id)
VALUES ('cliente.demo', crypt(:'pw_cliente', gen_salt('bf', 10)), 'Ana Ejemplo Prueba (cliente demo)', 'trigal@ejemplo.invalid', FALSE,
        (SELECT id FROM tributario_schema.clientes WHERE ruc = '20999000011'))
ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash, intentos_fallidos = 0, bloqueado_hasta = NULL, activo = TRUE,
        cliente_id = EXCLUDED.cliente_id;

INSERT INTO auth_schema.usuario_roles (usuario_id, rol_id)
SELECT u.id, r.id FROM auth_schema.usuarios u JOIN auth_schema.roles r
  ON (u.username = 'contador.demo' AND r.nombre = 'CONTADOR') OR (u.username = 'cliente.demo' AND r.nombre = 'CLIENTE_MYPE')
ON CONFLICT DO NOTHING;

COMMIT;
\echo Datos de demostracion cargados.
