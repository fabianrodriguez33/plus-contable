# HU-07: integración propuesta sobre develop

La rama de integración parte de `origin/develop` y conserva la tabla existente
`tributario_schema.obligaciones`. El `schema.sql` de esta rama propone añadir
`tipo_obligacion` sin valor por defecto, crear `cronograma_tributario` y añadir
unicidad por `(cliente_id, tipo_obligacion, periodo)`. **No se aplicó este script
a ninguna base de datos compartida.** En este proyecto `schema.sql` se ejecuta
en cada arranque, por lo que el equipo debe revisar el cambio antes de arrancar
esta rama contra una base compartida.

Las obligaciones nuevas exigen tipo, periodo mensual y fecha de vencimiento
explícita. Las filas antiguas conservan `tipo_obligacion = NULL`: PostgreSQL
permite varias filas con esa combinación bajo el índice único. No se les asigna
un tipo inventado. Antes de exigir `NOT NULL`, Jeanpierre y Ricardo deben
revisar duplicados, clasificar los datos anteriores y acordar su migración.

Se conservan `estado` (`PENDIENTE`/`DECLARADO`) y las columnas de HU-09
(`fecha_declaracion`, `numero_formulario`, `monto_determinado`). Se conservan
`fecha_vencimiento` y `vencimientos_historial` de HU-10. No se agrega
`estado_pago` ni se almacena `VENCIDO`.

El CSV de Julio se incorporó sin alterar sus filas como
`backend/ms-tributario/src/main/resources/cronograma_sunat_2026.csv` junto
con su nota de fuente en `docs/cronograma_sunat_2026.md`. El cargador inserta
las 84 filas de forma idempotente al arrancar el servicio. Se comprobó en una
instancia temporal de PostgreSQL, nunca en una base compartida. Tampoco hay asignación automática de fechas a
tipos: Beto debe confirmar qué obligaciones usan el cronograma y cómo se
determina el grupo RUC aplicable.
