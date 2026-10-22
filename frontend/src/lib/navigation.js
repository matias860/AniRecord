export function routeSectionTitle(pathname = '/') {
  if (pathname === '/') return 'Inicio'
  if (pathname.startsWith('/biblioteca')) return 'Biblioteca'
  if (pathname.startsWith('/catalogo')) return 'Catálogo'
  if (pathname.startsWith('/media/')) return 'Detalle de obra'
  if (pathname.startsWith('/personajes/')) return 'Personaje'
  if (/^\/(seiyuus|profesionales)\//.test(pathname)) return 'Profesional'
  if (pathname.startsWith('/perfil')) return 'Perfil'
  if (pathname.startsWith('/login')) return 'Iniciar sesión'
  return 'Anime y manga'
}
