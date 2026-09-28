# Auditoría de código y experiencia móvil — 28 de septiembre de 2026

## Alcance

Se revisaron el frontend React/Vite, las funciones Edge de Vercel, el backend Spring Boot, la configuración de despliegue, autenticación, CORS/CSRF, sesiones, carga de imágenes, validaciones, acceso a datos y dependencias. También se midieron las vistas principales en anchos de 320, 360, 390 y 430 px, y el compositor 3×3 en alturas de 568, 640, 760 y 844 px.

Esto es una revisión de código y configuración, no una prueba de penetración contra producción.

## Problemas corregidos

### Alta — dependencia vulnerable de procesamiento de imágenes

- `@vercel/og` resolvía `sharp 0.35.3`, afectado por una vulnerabilidad de severidad alta en libheif.
- Se actualizó el lockfile a `sharp 0.35.5` y sus binarios asociados.
- Verificación: `npm audit --omit=dev` informa **0 vulnerabilidades**.

### Media — origen local autorizado por CORS en producción

- `WebConfig` agregaba `http://localhost:5173` a la lista de orígenes para todos los entornos.
- La sesión de producción usa una cookie `Secure; SameSite=None`, por lo que un sitio malicioso servido en ese origen local podía realizar solicitudes con credenciales y con el encabezado anti-CSRF permitido.
- Se eliminó el alta incondicional. El desarrollo local conserva acceso porque `app.frontend-url` usa localhost como valor predeterminado cuando no se configura otro origen.

### Media — ausencia de cabeceras defensivas en el frontend

- Vercel no enviaba una política CSP ni cabeceras explícitas contra embedding, sniffing o acceso innecesario a sensores.
- Se agregaron CSP, HSTS, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` y `Permissions-Policy`.
- La CSP permite imágenes HTTPS externas porque los perfiles de AniList y los proveedores de streaming las necesitan; scripts y frames quedan restringidos.

### Media — desborde de la navegación en teléfonos estrechos

- La barra autenticada podía superar los 320 px por la suma del logo, navegación, perfil, notificaciones, cierre de sesión, padding y `gap`.
- Se redujeron separaciones de manera progresiva, se fijaron elementos que no deben encogerse y se eliminó el margen extra del cierre de sesión.
- Se quitó `min-width: 320px` del `body`, que forzaba scroll horizontal en dispositivos más estrechos o con zoom.
- Medición posterior: `scrollWidth` coincide con el viewport en 320, 360, 390 y 430 px; la barra, las acciones del home y la página quedan dentro de sus límites.

### Media — modales difíciles de usar con poco alto disponible

- El editor de imágenes podía superar la altura útil sin un contenedor desplazable.
- Se agregó altura máxima con `dvh` y scroll interno al editor.
- El compositor 3×3 ahora reduce padding, título, separaciones, radios y tamaño mínimo de candidatos en teléfonos; en pantallas bajas oculta texto auxiliar y mantiene accesibles la grilla y los botones mediante scroll.
- La pantalla de acceso, la carga, el catálogo y el modal de recomendaciones usan unidades dinámicas de viewport para no saltar detrás de la barra del navegador móvil.

## Controles revisados que ya estaban bien implementados

- Las contraseñas locales se almacenan con BCrypt factor 12 y tienen límites compatibles con BCrypt.
- Las cookies de sesión son `HttpOnly`, `Secure` en producción, sin atributo `Domain` y con vencimiento.
- Los tokens nuevos de sesión se guardan digeridos; los tokens de AniList se cifran con AES-GCM.
- Las operaciones de escritura bajo `/api` exigen un encabezado personalizado. Un formulario cross-site común no puede generarlo y CORS limita los orígenes que sí pueden usarlo.
- CORS usa coincidencias exactas, credenciales explícitas y una lista acotada de métodos.
- Registro, login, claim y llamadas a AniList tienen límites de frecuencia.
- Los servicios privados obtienen el usuario actual y las consultas de biblioteca incluyen su `userId`; no se encontró acceso horizontal evidente entre cuentas.
- Los DTO de entrada limitan nombres, biografía, colores, progreso, puntajes, IDs y cantidad de elementos del 3×3.
- Las cargas de avatar/banner limitan tamaño, aceptan solo JPEG/PNG/WebP y comprueban la firma real del archivo. SVG no está permitido.
- Las funciones OG solo cargan imágenes HTTPS alojadas en dominios de AniList; no se encontró SSRF por URL libre.
- Los enlaces de streaming se filtran a HTTP/HTTPS en el backend. El parser de texto de AniList solo reconoce enlaces HTTP/HTTPS y React no usa HTML sin escapar.
- No se encontraron usos de `dangerouslySetInnerHTML`, `eval` ni `new Function`.
- La sincronización hacia AniList mantiene la lógica defensiva agregada anteriormente: previsualización, confirmación por entrada y conservación de cambios ya enviados.

## Seguimiento aplicado después de la auditoría

### Resuelto sin servicios pagos — rate limit del backend

Se agregaron cuotas específicas para importaciones y sincronizaciones de AniList, cargas de avatar/banner, publicaciones 3×3, recomendaciones y todas las escrituras restantes. Una sesión autenticada recibe una cuota propia estricta y una cuota por IP más amplia para no bloquear a usuarios distintos detrás de la misma red. Los identificadores de sesión se digieren antes de usarse como claves y el mapa tiene un máximo de 20.000 ventanas para mantener acotada la memoria.

La IP toma el primer valor de `X-Forwarded-For`, que Vercel reemplaza por la IP real antes del rewrite y Northflank propaga por su balanceador. El límite sigue siendo local a la instancia y se reinicia con el proceso; esto se acepta deliberadamente para mantener el servicio gratuito y evitar Redis, Upstash o consumo extra del firewall. Si en el futuro el backend escala a varias réplicas, habrá que reevaluarlo.

### Resuelto — secretos de producción

El backend ahora aborta el arranque cuando la URL OAuth es HTTPS y falta `ANILIST_CLIENT_SECRET`. En desarrollo local se genera un secreto efímero por proceso en lugar de usar una clave literal conocida.

### Resuelto — sesiones antiguas sin digest

Se eliminó la búsqueda alternativa de sesiones y tickets guardados como texto. Una sesión legacy que todavía existiera requerirá iniciar sesión una vez; las sesiones modernas no se ven afectadas.

### Resuelto — trazabilidad OAuth segura

El callback registra si faltó código/estado y la clase de excepción cuando falla el intercambio. No escribe códigos OAuth, estados ni tokens en los logs.

### Resuelto — operaciones costosas

Las rutas costosas tienen cuotas dedicadas. Los límites ocurren antes de procesar imágenes o llamar a AniList, por lo que reducen trabajo y tráfico externo.

### En progreso — CSS acumulativo

Se inició la separación con `styles/mobile.css`, que concentra las protecciones compartidas de viewport dinámico. El resto debe seguir migrándose por componente cuando se toque cada pantalla; moverlo todo de una sola vez agregaría riesgo visual sin beneficio operativo inmediato.

### Resuelto — configuración de desarrollo

Vite usa ahora `VITE_API_PROXY_TARGET` de forma explícita y mantiene `http://localhost:8080` como valor local predeterminado. Se eliminó la variable que no participaba del flujo.

### Decisión aceptada — imágenes externas de perfil

Los perfiles de AniList pueden conservar URLs HTTPS externas. Se decidió no crear un proxy propio para evitar almacenamiento, transferencia y mantenimiento adicionales. Las cargas hechas dentro de AniRecord siguen sirviéndose desde la infraestructura actual.

## Verificación ejecutada

- Auditoría npm de dependencias de producción.
- Compilación y lint del frontend.
- Suite Maven del backend.
- Lectura automatizada de tamaños de cajas y overflow a 320/360/390/430 px.
- Prueba del modal 3×3 en 320×568, 320×760, 360×640 y 390×844.
- Revisión manual de autenticación, sesiones, CORS, CSRF, rate limiting, uploads, URLs externas, DTOs y consultas por usuario.

## Limitaciones de la verificación

El dominio público no respondió correctamente desde el navegador de verificación durante esta auditoría, por lo que la comprobación visual se hizo con el frontend local y un fixture temporal que usa las mismas clases y hojas de estilo. El fixture se eliminó al terminar. El build y las mediciones de layout sí usan el código final.
