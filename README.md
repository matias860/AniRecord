# Anime List

Aplicación personal para registrar anime y manga.

## Tecnologías

### Frontend

- React
- Vite
- Redux Toolkit
- Tailwind CSS
- Axios

### Backend

- Java
- Spring Boot
- JWT
- JPA/Hibernate
- MySQL

## Funciones principales

- Buscar anime y manga mediante AniList.
- Añadir obras a una biblioteca personal.
- Registrar episodios vistos o capítulos leídos.
- Puntuar obras.
- Clasificar obras según su estado.
- Consultar precuelas, secuelas y material original.
- Consultar el próximo episodio de los animes en emisión.
- Mostrar portadas y banners obtenidos desde AniList.

## Configuración local del backend

1. Ejecutar `database/schema.sql` en MySQL.
2. Copiar `backend/src/main/resources/application-local.properties.example` como
   `application-local.properties`.
3. Completar el usuario y la contraseña local de MySQL.
4. Desde `backend`, ejecutar `./mvnw spring-boot:run` o, en Windows,
   `.\mvnw.cmd spring-boot:run`.

El backend se inicia en `http://localhost:8080` y el frontend en
`http://localhost:5173`.

## Endpoints principales

### AniList

- `GET /api/anilist/search?query=Frieren&type=ANIME&page=1&perPage=20`
- `GET /api/anilist/media/{anilistId}`

El tipo de búsqueda puede ser `ANIME` o `MANGA`.

### Biblioteca

- `GET /api/library`
- `GET /api/library?type=ANIME&status=IN_PROGRESS`
- `GET /api/library/{id}`
- `POST /api/library`
- `PATCH /api/library/{id}`
- `POST /api/library/{id}/progress/increment`
- `POST /api/library/{id}/progress/decrement`
- `POST /api/library/{id}/sync`
- `GET /api/library/airing`
- `DELETE /api/library/{id}`

Ejemplo para añadir una obra:

```json
{
  "anilistId": 154587,
  "trackingStatus": "PLANNED"
}
```

Ejemplo para actualizar el seguimiento:

```json
{
  "progress": 5,
  "personalScore": 9.0,
  "trackingStatus": "IN_PROGRESS"
}
```

Estados disponibles: `PLANNED`, `IN_PROGRESS`, `PAUSED`, `COMPLETED` y
`DROPPED`.
