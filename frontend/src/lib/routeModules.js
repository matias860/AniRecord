const loaders = {
  home: () => import('../views/HomeView.jsx'),
  library: () => import('../views/LibraryView.jsx'),
  mediaDetail: () => import('../views/MediaDetailView.jsx'),
  catalog: () => import('../views/CatalogView.jsx'),
  profile: () => import('../views/ProfileView.jsx'),
  login: () => import('../views/LoginView.jsx'),
  import: () => import('../views/ImportView.jsx'),
  characterDetail: () => import('../views/CharacterDetailView.jsx'),
  staffDetail: () => import('../views/StaffDetailView.jsx'),
}

const loaded = new Map()

export function loadRoute(name) {
  if (!loaders[name]) return Promise.resolve()
  if (!loaded.has(name)) loaded.set(name, loaders[name]())
  return loaded.get(name)
}

export function preloadRoute(pathname) {
  if (/^\/media\//.test(pathname)) return loadRoute('mediaDetail')
  if (/^\/personajes\//.test(pathname)) return loadRoute('characterDetail')
  if (/^\/(seiyuus|profesionales)\//.test(pathname)) return loadRoute('staffDetail')
  if (pathname.startsWith('/biblioteca')) return loadRoute('library')
  if (pathname.startsWith('/catalogo')) return loadRoute('catalog')
  if (pathname.startsWith('/perfil')) return loadRoute('profile')
  if (pathname.startsWith('/login')) return loadRoute('login')
  return loadRoute('home')
}

export default loaders
