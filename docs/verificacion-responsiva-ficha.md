# Verificación del diseño responsivo de la ficha del cliente (HU-01)

Criterio DoD de la tarjeta HU-01: «Diseño responsivo». Alcance: la pestaña **Datos generales** de la ficha del cliente
(`/clientes/:id` y `/clientes/nuevo`) y la estructura que la rodea (barra lateral, barra superior y barra de pestañas).
No se midieron las demás pestañas (socios, planilla, activos, acuerdo, vencimientos, programación) ni el calendario.

## Qué se comprueba y cómo

1. **Prueba automática (vitest, en cada ejecución).** jsdom no calcula el diseño, así que las pruebas protegen las clases
   que lo hacen responsivo y el comportamiento del menú móvil:
   - `src/pages/FichaMaestraPage.test.tsx`: el formulario es de una columna y pasa a dos con `md:grid-cols-2`; la dirección
     fiscal y la casilla de buen contribuyente ocupan todo el ancho; los campos usan `w-full` y no llevan anchos fijos;
     la barra de pestañas se desplaza dentro de sí misma (`overflow-x-auto`); el alta de un cliente nuevo usa el mismo formulario.
   - `src/components/Navbar.test.tsx`: la barra lateral está oculta en móvil y aparece desde `md`; el botón de menú solo
     se ve en móvil; el contenido puede encogerse (`min-w-0`); el menú móvil se abre, cierra con el botón y con Escape.
2. **Medición en el navegador (manual, repetible).** Es la que comprueba que no hay desbordamiento horizontal de verdad.

## Medición del 02/11/2026

Navegador integrado de la aplicación de escritorio, con una base desechable y un cliente de demostración. Los anchos se
emularon con el visor (375 × 812 y 768 × 1024); no se probó en un teléfono ni en una tableta reales.

| Ancho | Columnas del formulario | Desbordamiento horizontal | Navegación | Pestañas |
|---|---|---|---|---|
| 375 px (móvil) | 1 (294 px) | No (`scrollWidth` 375 = ancho de la ventana); ningún elemento del formulario fuera de la ventana | Barra lateral oculta; botón de menú (hamburguesa) | Se desplazan dentro de su barra (contenido de 1211 px en una barra de 294 px) |
| 768 px (tableta) | 2 (207 px cada una) | No (`scrollWidth` 768 = ancho de la ventana); ningún elemento fuera | Barra lateral fija visible | Se desplazan dentro de su barra |

Observaciones:

- A 768 px la barra lateral ya ocupa 240 px, así que cada columna del formulario queda angosta y algunos valores largos
  (por ejemplo una razón social extensa) se recortan dentro del campo, sin romper la página.
- En 1024 px y en escritorio no se midió.

## Cómo repetir la medición

1. Abrir la ficha de un cliente (pestaña Datos generales) en el navegador.
2. Emular 375 × 812 y luego 768 × 1024 (herramientas de desarrollo → modo dispositivo).
3. Ejecutar en la consola y comprobar que `desbordaHorizontal` es `false` y `elementosFuera` es 0:

```js
const de = document.documentElement
const fuera = [...document.querySelectorAll('form *')].filter((e) => {
  const r = e.getBoundingClientRect()
  return r.width > 0 && (r.right > innerWidth + 1 || r.left < -1)
}).length
({
  innerWidth,
  scrollWidth: de.scrollWidth,
  desbordaHorizontal: de.scrollWidth > innerWidth,
  columnasFormulario: getComputedStyle(document.querySelector('form .grid')).gridTemplateColumns,
  elementosFuera: fuera,
})
```

## Pendiente para el Sprint 2

- Medir el desbordamiento de forma automática a cada ancho (por ejemplo con Playwright), lo que agrega una dependencia
  de desarrollo y la instalación de navegadores.
- Medir el resto de pestañas de la ficha y el calendario.
