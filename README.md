# AniRecord

Aplicación personal para registrar anime y manga con datos obtenidos desde AniList.

## Producción

- Sitio: https://anirecord.com.ar
- API pública (proxy de AniRecord): https://anirecord.com.ar/api
- Base de datos: PostgreSQL administrado por Neon

El backend gratuito se suspende después de un período sin tráfico. La primera
petición puede demorar cerca de un minuto; los datos permanecen guardados en
Neon aunque el servicio esté dormido.

## Tecnologías

- React, Vite, Redux Toolkit, HeroUI, Tailwind y Axios.
- Java 17, Spring Boot, JPA/Hibernate y Flyway.
- PostgreSQL en Neon.
- AniList GraphQL API y OAuth.

## Desarrollo del frontend

Ejecutar `iniciar.bat`, o desde `frontend`:

```powershell
npm install
npm run dev
```

El entorno de desarrollo utiliza el backend publicado. Esto permite trabajar
desde cualquier PC sin instalar ni iniciar MySQL.

## Desarrollo del backend

Configurar estas variables de entorno antes de ejecutar Spring Boot:

```text
JDBC_DATABASE_URL=jdbc:postgresql://HOST/anime_list?sslmode=require
DATABASE_USERNAME=USUARIO_NEON
DATABASE_PASSWORD=CONTRASEÑA_NEON
ANILIST_CLIENT_ID=ID_DE_ANILIST
ANILIST_CLIENT_SECRET=SECRETO_DE_ANILIST
ANILIST_REDIRECT_URI=http://localhost:8080/api/anilist/oauth/callback
```

Después, desde `backend`:

```powershell
.\mvnw.cmd spring-boot:run
```

Las modificaciones de la base se agregan como nuevas migraciones en
`backend/src/main/resources/db/migration` (`V2__...sql`, `V3__...sql`, etc.).
Flyway las aplica automáticamente sin borrar los datos existentes.

## Sincronización manual hacia AniList

Las cuentas conectadas con AniList tienen en su perfil una sección para revisar y
enviar animes de AniRecord a AniList. Se comparan estado, episodios y puntuación
(convertida a la escala de 100 de AniList), y se pueden seleccionar las entradas
a enviar. No se eliminan entradas ni se exportan manga, notas, fechas o favoritos.

La vista previa es privada por usuario, dura dos horas y se invalida si el backend
se reinicia. Cada envío vuelve a comprobar los valores locales y remotos. Si hay
un cambio posterior, un error o una interrupción, se detiene el proceso y se debe
revisar otra vez; los cambios ya aplicados permanecen en AniList y no se duplican.
Mantener la página abierta durante el envío. No hay sincronización al iniciar sesión.

El token OAuth permanece cifrado en el servidor. Las escrituras no tienen reintentos
automáticos y las peticiones de exportación se espacian al menos 2,5 segundos por
instancia. No se requiere una migración de base de datos. La compatibilidad técnica
con la API no sustituye las condiciones de uso de AniList aplicables al proyecto.

## Despliegue continuo

Los cambios enviados a `main` se despliegan automáticamente:

- Vercel reconstruye el frontend.
- Northflank reconstruye el backend cuando cambia la carpeta `backend`.
- Flyway aplica las nuevas migraciones en Neon al iniciar el backend.
