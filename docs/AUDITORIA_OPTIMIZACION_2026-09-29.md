# Auditoría general de optimización — 29 de septiembre de 2026

## Alcance

Se revisaron el frontend React/Vite, el estado Redux, las cargas de imágenes, las llamadas automáticas, el backend Spring Boot, las consultas JPA, las tareas programadas, el acceso a AniList, el esquema PostgreSQL y la configuración de despliegue. El objetivo fue mejorar velocidad y consumo sin agregar servicios pagos ni cambiar el comportamiento del producto.

## Mejoras aplicadas

### Carga inicial del frontend

- Las vistas ahora se cargan por ruta. La entrada ya no descarga por adelantado biblioteca, catálogo, detalles, perfiles, personas e importación.
- El JavaScript inicial bajó de **402,01 kB a 218,43 kB** sin comprimir y de **122,86 kB a 68,62 kB gzip**: una reducción aproximada del **44 %** en la descarga comprimida inicial.
- Las portadas de los selectores de 3×3, recomendación semanal y muestra comunitaria usan carga diferida y decodificación asíncrona. Esto evita descargar de inmediato imágenes que todavía están fuera de pantalla.

### Trabajo del home

- El cruce entre emisiones y biblioteca recorría la biblioteca completa por cada emisión.
- Ahora se construye un índice por `anilistId` una sola vez y se memorizan progreso, contadores, orden de estanterías y calendario.
- El costo principal pasa de crecer como emisiones × biblioteca a crecer como emisiones + biblioteca, y abrir un modal ya no repite esos cálculos si los datos no cambiaron.

### Exportación a AniList

- Al confirmar cada cambio, el backend volvía a consultar y construir el resumen de toda la biblioteca para localizar una sola entrada.
- Ahora valida esa entrada con una consulta puntual por `entryId` y `userId`.
- Se mantienen sin cambios la vista previa, la comprobación de concurrencia y las reglas que impiden reducir progreso o reemplazar datos más nuevos de AniList.

### Consultas automáticas y uso de AniList

- El timbre de notificaciones consultaba cada 5 minutos incluso con la pestaña oculta. Ahora consulta cada 15 minutos y solo si el documento está visible; al volver a la pestaña actualiza en ese momento.
- En una pestaña abierta de forma continua, el sondeo periódico máximo baja aproximadamente un **67 %** y las pestañas en segundo plano dejan de consultar.
- El observador de nuevas secuelas y relaciones recorría obras de todas las cuentas, aunque las cuentas conectadas ya reciben notificaciones desde AniList. Ahora consulta únicamente obras pertenecientes a cuentas locales.
- La búsqueda de destinatarios de esas relaciones también filtra las cuentas locales en PostgreSQL en vez de cargar todas y descartarlas en Java.

## Estado comprobado

- `npm audit --omit=dev`: **0 vulnerabilidades** en dependencias de producción.
- ESLint: sin errores ni advertencias.
- Build de producción de Vite: correcto.
- Backend: **77 pruebas**, 0 fallos y 0 errores.
- Las consultas principales de biblioteca e historial ya usan proyecciones o `EntityGraph`, y `open-in-view` está desactivado. No se detectó un problema general de N+1 en esas pantallas.
- El pool de PostgreSQL está limitado a tres conexiones y el caché de AniList tiene tamaño y vencimiento acotados, adecuados para el plan gratuito actual.
- Los límites de frecuencia, sesiones digeridas, cifrado de tokens, validación de imágenes y cabeceras defensivas de la auditoría anterior siguen presentes.

## Mejoras pendientes, por prioridad

### 1. Importación masiva desde AniList

`LibraryService.importForUser` busca y guarda medios y entradas uno por uno. En bibliotecas grandes produce muchas consultas dentro de una transacción larga y el método está sincronizado para toda la instancia. No afecta la navegación diaria, pero puede hacer lenta una importación grande.

La mejora correcta es cargar de una vez las entradas del usuario y los medios por todos los IDs recibidos, resolverlos en mapas y guardar lotes. Conviene hacerlo en una tarea separada con pruebas de importación, porque esta ruta modifica datos personales y una optimización apresurada tendría más riesgo que beneficio.

### 2. Migraciones de base de datos

Flyway está incluido, pero desactivado por defecto. Esto evita ejecutar automáticamente la migración histórica destructiva `V3`, pero obliga a recordar cambios manuales de esquema en producción. Es el principal riesgo operativo de largo plazo.

No se debe activar Flyway directamente sobre la base existente. Primero hay que registrar de forma segura el estado actual como línea base o crear una cadena nueva de migraciones no destructivas y validarla sobre una copia de la base.

### 3. CSS global acumulado

`src/index.css` tiene unas 721 líneas y 170,6 kB; el CSS global construido pesa 268,15 kB, 43,28 kB gzip. No bloquea el funcionamiento, pero todas las rutas lo reciben.

Conviene seguir moviendo estilos a hojas por componente o vista cuando se modifique cada sección. Una separación masiva tendría riesgo visual alto; hacerlo de forma gradual conserva estabilidad y permite que Vite cargue CSS junto con cada ruta.

### 4. Crecimiento de tablas históricas

Las sesiones vencidas, el historial de actividad y las notificaciones no tienen una política de retención. Las búsquedas actuales están indexadas y limitadas, por lo que no es urgente con el volumen actual, pero el almacenamiento crecerá con los años.

Se puede eliminar automáticamente solo las sesiones vencidas. Para actividad y notificaciones hace falta decidir cuánto historial quiere conservar el producto antes de borrar datos del usuario.

### 5. Consultas comunitarias al crecer la audiencia

El 3×3 limita a 24 resultados después de cargar las publicaciones del período y consulta la versión del avatar por autor. Las recomendaciones semanales hacen una consulta similar por avatar. Con pocos usuarios el costo es pequeño; si la comunidad crece, el límite debe pasar a PostgreSQL mediante `Pageable` y los avatares deben resolverse en lote.

### 6. Mantenibilidad del cliente de AniList

`AniListClient` concentra más de mil líneas de consultas GraphQL, mapeos y reintentos. Funciona y dispone de caché, pero hace que cada cambio sea más difícil de revisar. Separarlo por biblioteca, catálogo, personas y relaciones reduciría riesgo de mantenimiento; no produciría por sí solo una mejora visible de velocidad.

### 7. Actualizaciones menores de dependencias

Hay versiones menores disponibles de React, Redux Toolkit, Axios, Vite, HeroUI y otras herramientas. La auditoría no encontró vulnerabilidades que obliguen a actualizar ahora. Es preferible agrupar esas actualizaciones en mantenimiento periódico y probarlas juntas, en lugar de añadir cambios de versión a esta optimización.

## Decisiones que se conservaron

- No se agregó Redis, un proxy de imágenes ni otro servicio con costo o mantenimiento recurrente.
- No se añadió paginación a la biblioteca porque el home y los filtros necesitan el conjunto local completo; antes de cambiarlo conviene medir bibliotecas reales grandes.
- No se activó compresión adicional del backend sin medir primero lo que ya comprime Vercel en el proxy, para evitar gastar CPU de la instancia gratuita sin una ganancia comprobada.
