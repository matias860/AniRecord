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

## Riesgos y deuda pendientes

### Media — rate limit local y confianza en `X-Forwarded-For`

`RateLimitFilter` guarda contadores en memoria. Cada réplica tiene su propio estado y un reinicio los borra. Además usa el primer valor de `X-Forwarded-For`; la seguridad de ese dato depende de que el proxy de Northflank elimine valores aportados por el cliente. Conviene migrarlo a Redis/Upstash o al firewall del proveedor y definir explícitamente qué proxy es confiable.

### Media — secretos de desarrollo como último fallback

Si faltan tanto `TOKEN_ENCRYPTION_KEY` como las credenciales OAuth, el backend arranca con claves literales de desarrollo. En ese estado no se puede iniciar OAuth nuevo, pero el comportamiento no es fail-closed y una mala configuración podría pasar desapercibida. Producción debería exigir las variables y abortar el arranque si faltan.

### Baja — compatibilidad temporal con sesiones antiguas sin digest

`CurrentUserService` y el flujo de claim todavía prueban el token antiguo en texto después de probar su digest. Esto facilita la migración de sesiones existentes, pero una fila legacy robada equivale a una sesión activa. Debe retirarse después de invalidar o migrar todas las sesiones antiguas.

### Baja — errores OAuth con poca trazabilidad

El callback captura cualquier `RuntimeException` y redirige a un error genérico sin registrar la causa. No filtra datos al usuario, pero complica distinguir rechazo de AniList, error de base de datos o configuración incorrecta. Se recomienda logging estructurado sin tokens ni códigos OAuth.

### Baja — rate limit incompleto para operaciones costosas

El límite dedicado cubre autenticación y rutas AniList, pero no cargas de imágenes, sincronización completa, publicaciones 3×3 ni recomendaciones. Autenticación y tamaño máximo reducen el impacto, aunque faltan cuotas por usuario para abuso sostenido.

### Baja — CSS acumulativo difícil de mantener

`index.css` contiene varias generaciones de reglas para los mismos selectores y breakpoints. La cascada actual funciona, pero ya produjo anulaciones involuntarias en navegación, recomendaciones y detalle. Conviene separar layout, componentes y responsive por módulo y eliminar reglas superadas.

### Baja — configuración de desarrollo confusa

`frontend/.env.development` define `VITE_API_URL`, pero `apiClient` usa `/api` y Vite siempre lo redirige a `localhost:8080`. La variable no participa del flujo. Conviene eliminarla o hacer que `vite.config.js` la use de manera explícita para evitar probar contra un backend distinto del esperado.

### Informativa — imágenes externas de perfil

Los perfiles de AniList pueden conservar URLs HTTPS externas. Esto es necesario para mostrar avatar y banner, pero hace que el navegador contacte al host de la imagen y revele IP/agente de usuario. Las imágenes subidas localmente no tienen este problema. Un proxy de imágenes propio sería la opción de privacidad más fuerte.

## Verificación ejecutada

- Auditoría npm de dependencias de producción.
- Compilación y lint del frontend.
- Suite Maven del backend.
- Lectura automatizada de tamaños de cajas y overflow a 320/360/390/430 px.
- Prueba del modal 3×3 en 320×568, 320×760, 360×640 y 390×844.
- Revisión manual de autenticación, sesiones, CORS, CSRF, rate limiting, uploads, URLs externas, DTOs y consultas por usuario.

## Limitaciones de la verificación

El dominio público no respondió correctamente desde el navegador de verificación durante esta auditoría, por lo que la comprobación visual se hizo con el frontend local y un fixture temporal que usa las mismas clases y hojas de estilo. El fixture se eliminó al terminar. El build y las mediciones de layout sí usan el código final.
