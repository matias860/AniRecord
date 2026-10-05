export const CURRENT_VERSION = '1.9.1'

export const RELEASES = [
  {
    version: '1.9.1',
    date: '5 de octubre de 2026',
    title: 'Corrige el estilo inicial del 3×3',
    summary: 'Corregimos la carga de los estilos del 3×3.',
    changes: [
      { type: 'Visual', text: 'El botón y las publicaciones 3×3 conservan su diseño desde que abre la página.' },
    ],
  },
  {
    version: '1.9.0',
    date: '5 de octubre de 2026',
    title: 'Una experiencia más rápida',
    summary: 'Reducimos esperas y trabajo innecesario para que AniRecord responda mejor desde el primer momento.',
    changes: [
      { type: 'Rendimiento', text: 'El inicio y el catálogo ya se pueden recorrer mientras comprobamos tu sesión.' },
      { type: 'Rendimiento', text: 'Las bibliotecas grandes hacen menos trabajo al pasar el cursor por sus portadas.' },
      { type: 'Rendimiento', text: 'Las herramientas pesadas del perfil se cargan solamente cuando se necesitan.' },
      { type: 'Visual', text: 'La navegación entre páginas empieza de inmediato y respeta la preferencia de reducir movimiento.' },
      { type: 'Rendimiento', text: 'Las imágenes y estadísticas fuera de pantalla consumen menos recursos.' },
    ],
  },
  {
    version: '1.8.3',
    date: '5 de octubre de 2026',
    title: 'Organiza las versiones menores',
    summary: 'Ordenamos las correcciones dentro de cada versión principal.',
    changes: [
      { type: 'Visual', text: 'La lista principal ahora muestra solamente las versiones grandes.' },
      { type: 'Mejora', text: 'Las correcciones pequeñas se pueden desplegar dentro de la versión a la que pertenecen.' },
    ],
  },
  {
    version: '1.8.2',
    date: '5 de octubre de 2026',
    title: 'Alinea los tildes del historial',
    summary: 'Corregimos la posición de los indicadores de cada cambio.',
    changes: [
      { type: 'Visual', text: 'Los tildes ahora mantienen la misma posición en todos los cambios.' },
    ],
  },
  {
    version: '1.8.1',
    date: '5 de octubre de 2026',
    title: 'Mejora el historial de versiones',
    summary: 'Ajustamos la presentación y el texto del historial.',
    changes: [
      { type: 'Visual', text: 'El historial ahora tiene un ícono propio y muestra la fecha completa de cada versión.' },
      { type: 'Visual', text: 'Agrandamos y centramos la descripción de cada cambio.' },
      { type: 'Mejora', text: 'Simplificamos las explicaciones para contar claramente qué cambió.' },
    ],
  },
  {
    version: '1.8.0',
    date: '5 de octubre de 2026',
    title: 'Una base más sólida',
    summary: 'Mejoramos los procesos internos que mantienen tu cuenta, biblioteca y notificaciones seguras y consistentes.',
    changes: [
      { type: 'Lógica', text: 'Las actualizaciones desde AniList ya no frenan las sincronizaciones de otras personas.' },
      { type: 'Seguridad', text: 'El inicio de sesión es más seguro y resistente a intentos repetidos.' },
      { type: 'Lógica', text: 'Si tenés muchas notificaciones, AniRecord las recupera de a poco sin perder las anteriores.' },
      { type: 'Rendimiento', text: 'Los 3×3 y las importaciones ahora cargan con menos trabajo.' },
    ],
  },
  {
    version: '1.7.0',
    date: '3 de octubre de 2026',
    title: 'Sincronización más clara',
    summary: 'Separamos cada dirección de sincronización y mejoramos la información que llega desde AniList.',
    changes: [
      { type: 'Visual', text: 'El perfil distingue claramente AniRecord → AniList y AniList → AniRecord.' },
      { type: 'Lógica', text: 'AniRecord trae cambios de AniList al iniciar cuando detecta novedades.' },
      { type: 'Lógica', text: 'El calendario incluye anime pendiente de la temporada, incluso antes de su estreno.' },
      { type: 'Lógica', text: 'Corregimos la carga y el estado de lectura de las notificaciones.' },
    ],
  },
  {
    version: '1.6.0',
    date: '1 de octubre de 2026',
    title: 'Catálogo completo',
    summary: 'El catálogo ganó filtros más precisos, enlaces directos por categoría y una navegación consistente.',
    changes: [
      { type: 'Visual', text: 'Agregamos todas las categorías disponibles en AniList con un selector adaptable.' },
      { type: 'Lógica', text: 'Las categorías de cada ficha abren el catálogo con el filtro correspondiente.' },
      { type: 'Lógica', text: 'Los filtros se conservan al navegar hacia atrás, adelante o compartir un enlace.' },
      { type: 'Visual', text: 'Ajustamos filtros y desplegables para escritorio y celular.' },
    ],
  },
  {
    version: '1.5.0',
    date: '29 de septiembre de 2026',
    title: 'Manga y mejor rendimiento',
    summary: 'La sincronización llegó al manga junto con una revisión general de velocidad y uso de recursos.',
    changes: [
      { type: 'Integración', text: 'Ya se pueden revisar y enviar cambios de manga a AniList sin pisar datos más recientes.' },
      { type: 'Rendimiento', text: 'Reducimos trabajo inicial y llamadas en segundo plano.' },
      { type: 'Seguridad', text: 'Hicimos más segura la carga de imágenes y evitamos que dos cambios al mismo tiempo se pisen.' },
    ],
  },
  {
    version: '1.4.0',
    date: '23 de septiembre de 2026',
    title: 'Los 3×3 llegan al inicio',
    summary: 'La comunidad puede armar y publicar selecciones temáticas directamente desde la página principal.',
    changes: [
      { type: 'Visual', text: 'Nuevo creador 3×3 con portadas, selección rápida y vista previa.' },
      { type: 'Comunidad', text: 'Los 3×3 publicados aparecen en el inicio y rotan cuando hay varias participaciones.' },
      { type: 'Visual', text: 'Pulimos el selector, sus animaciones y su adaptación a pantallas pequeñas.' },
    ],
  },
  {
    version: '1.3.0',
    date: '21 de septiembre de 2026',
    title: 'De AniRecord a AniList',
    summary: 'Incorporamos el envío manual y revisado de anime sin perder información más reciente de AniList.',
    changes: [
      { type: 'Integración', text: 'Vista previa de cada alta o actualización antes de enviarla a AniList.' },
      { type: 'Lógica', text: 'La comparación conserva puntajes y episodios que estén más actualizados en AniList.' },
      { type: 'Visual', text: 'Progreso de envío con portada, estado y errores específicos por obra.' },
      { type: 'Visual', text: 'Formatos, fechas y títulos largos se muestran de forma más consistente.' },
    ],
  },
  {
    version: '1.2.0',
    date: '31 de agosto de 2026',
    title: 'AniRecord toma forma',
    summary: 'La aplicación estrenó su identidad actual y sumó más información útil en cada ficha.',
    changes: [
      { type: 'Visual', text: 'Nueva identidad AniRecord y dirección anirecord.com.ar.' },
      { type: 'Integración', text: 'Las fichas muestran dónde ver cada serie mediante sus plataformas disponibles.' },
      { type: 'Visual', text: 'Mejoramos banners, encabezados y tarjetas para celular.' },
    ],
  },
]
