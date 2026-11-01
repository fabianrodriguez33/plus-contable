-- HT-01 · Un usuario de base de datos por servicio, cada uno solo con su esquema.
--
-- SQL puro (sin comandos de psql) para poder ejecutarlo desde psql (db/init/01-roles-por-servicio.sql) y desde
-- JDBC (prueba AislamientoPorUsuarioPostgresTest). Es idempotente: se puede repetir sobre una base nueva o sobre una
-- que ya tiene los esquemas y las tablas (creadas antes por el usuario administrador).
--
-- Lee sus parametros de variables de sesion, que quien lo ejecuta define antes con set_config(..., false):
--   rolesvc.auth_user, rolesvc.auth_password, rolesvc.tributario_user, rolesvc.tributario_password
-- Las contrasenas nunca van en este archivo ni en el repositorio.
--
-- Efecto, por cada servicio:
--   * crea el usuario (LOGIN) o, si ya existe, le actualiza la contrasena;
--   * crea su esquema si falta y lo deja como dueno del esquema, de sus tablas, secuencias y vistas;
--   * quita el acceso de PUBLIC al esquema. El usuario del otro servicio no recibe ningun permiso sobre el;
--   * le da CREATE sobre la base, necesario para que el schema.sql de cada servicio (CREATE SCHEMA IF NOT EXISTS) arranque.
DO $roles$
DECLARE
    servicio record;
    objeto   record;
BEGIN
    FOR servicio IN
        SELECT * FROM (VALUES
            ('auth_schema',       current_setting('rolesvc.auth_user', true),       current_setting('rolesvc.auth_password', true)),
            ('tributario_schema', current_setting('rolesvc.tributario_user', true), current_setting('rolesvc.tributario_password', true))
        ) AS t(esquema, usuario, clave)
    LOOP
        IF coalesce(servicio.usuario, '') = '' OR coalesce(servicio.clave, '') = '' THEN
            RAISE EXCEPTION 'Falta el usuario o la contrasena de % (variables rolesvc.*)', servicio.esquema;
        END IF;

        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = servicio.usuario) THEN
            EXECUTE format('ALTER ROLE %I WITH LOGIN PASSWORD %L', servicio.usuario, servicio.clave);
        ELSE
            EXECUTE format('CREATE ROLE %I WITH LOGIN PASSWORD %L', servicio.usuario, servicio.clave);
        END IF;

        -- CREATE SCHEMA IF NOT EXISTS (primera linea de cada schema.sql) exige este permiso aunque el esquema ya exista.
        -- Solo permite crear esquemas nuevos: no da acceso a los esquemas del otro servicio.
        EXECUTE format('GRANT CREATE ON DATABASE %I TO %I', current_database(), servicio.usuario);
        EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I AUTHORIZATION %I', servicio.esquema, servicio.usuario);
        EXECUTE format('ALTER SCHEMA %I OWNER TO %I', servicio.esquema, servicio.usuario);
        EXECUTE format('REVOKE ALL ON SCHEMA %I FROM PUBLIC', servicio.esquema);

        FOR objeto IN SELECT tablename AS nombre FROM pg_tables WHERE schemaname = servicio.esquema LOOP
            EXECUTE format('ALTER TABLE %I.%I OWNER TO %I', servicio.esquema, objeto.nombre, servicio.usuario);
        END LOOP;
        FOR objeto IN SELECT sequencename AS nombre FROM pg_sequences WHERE schemaname = servicio.esquema LOOP
            EXECUTE format('ALTER SEQUENCE %I.%I OWNER TO %I', servicio.esquema, objeto.nombre, servicio.usuario);
        END LOOP;
        FOR objeto IN SELECT viewname AS nombre FROM pg_views WHERE schemaname = servicio.esquema LOOP
            EXECUTE format('ALTER VIEW %I.%I OWNER TO %I', servicio.esquema, objeto.nombre, servicio.usuario);
        END LOOP;
    END LOOP;
END
$roles$;
