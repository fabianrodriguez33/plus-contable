-- HT-01 · Envoltorio de psql para db/roles-por-servicio.sql.
--
-- Lo ejecuta solo el contenedor de PostgreSQL al CREAR el volumen (carpeta docker-entrypoint-initdb.d) y tambien
-- se puede ejecutar a mano sobre una base existente (ver el README, seccion "Usuarios de base de datos por servicio").
--
-- Lee las variables de entorno AUTH_DB_USER, AUTH_DB_PASSWORD, TRIBUTARIO_DB_USER y TRIBUTARIO_DB_PASSWORD.
-- Si falta alguna contrasena no hace nada y avisa: el proyecto sigue funcionando con DB_USER y DB_PASSWORD.
\set ON_ERROR_STOP on
\set auth_user ms_auth
\set auth_password ''
\set tributario_user ms_tributario
\set tributario_password ''
\getenv auth_user AUTH_DB_USER
\getenv auth_password AUTH_DB_PASSWORD
\getenv tributario_user TRIBUTARIO_DB_USER
\getenv tributario_password TRIBUTARIO_DB_PASSWORD

SELECT (:'auth_password' <> '' AND :'tributario_password' <> '') AS listo \gset

\if :listo
    SELECT set_config('rolesvc.auth_user', :'auth_user', false) AS a,
           set_config('rolesvc.auth_password', :'auth_password', false) AS b,
           set_config('rolesvc.tributario_user', :'tributario_user', false) AS c,
           set_config('rolesvc.tributario_password', :'tributario_password', false) AS d \gset
    \i /db/roles-por-servicio.sql
    \echo Usuarios por servicio creados: :auth_user (auth_schema) y :tributario_user (tributario_schema)
\else
    \echo 'AUTH_DB_PASSWORD y TRIBUTARIO_DB_PASSWORD no estan definidas: no se crean usuarios por servicio.'
\endif
