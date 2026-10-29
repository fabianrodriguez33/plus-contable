# HU-07: propuesta de integración de obligaciones

Esta propuesta no se aplica a ninguna base de datos. La rama `integration/sprint1`
ya define `tributario_schema.obligaciones` con `id`, `cliente_id`, `periodo`,
`fecha_vencimiento`, `estado` y las columnas de declaración de HU-09.
HU-10 usa `fecha_vencimiento` y `vencimientos_historial`.

## Cambios propuestos para revisión conjunta

1. Revisar duplicados existentes por `(cliente_id, tipo_obligacion, periodo)` antes
   de añadir una restricción única. Definir con el equipo cómo resolverlos si existen.
2. Añadir `tipo_obligacion` a la tabla existente, migrar los registros anteriores
   solo cuando el equipo conozca su tipo y después exigir `NOT NULL` y unicidad
   por `(cliente_id, tipo_obligacion, periodo)`.
3. Crear una tabla separada de cronograma con `periodo_tributario`,
   `ultimo_digito_ruc` y `fecha_vencimiento`, y clave única por período y grupo.
   Cargarla desde `docs/cronograma_sunat_2026.csv` de Julio una vez revisada e
   incorporada explícitamente junto con `docs/cronograma_sunat_2026.md`.
4. Mantener `estado` con `PENDIENTE` y `DECLARADO`, las columnas de HU-09 y el
   historial de HU-10. La fecha de vencimiento de cada obligación solo se
   asignará cuando Beto confirme a qué tipos corresponde el cronograma y cómo
   seleccionar el grupo RUC; HU-10 podrá modificarla después.

No se propone guardar `VENCIDO` ni añadir `estado_pago`. Antes de implementar la
migración se necesita acordar con Jeanpierre el cambio de la tabla compartida y
verificar los datos existentes. El código actual solo prepara registros en memoria:
la unicidad definitiva debe imponerse en la base de datos para evitar carreras.
