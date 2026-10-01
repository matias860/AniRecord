const RETURN_PATH_KEY = 'anime-list:return-after-login'
const PRODUCTION_OAUTH_ORIGIN = 'https://p01--anime-list--244mm2wyvsvc.code.run'

export function oauthLoginUrl(env = import.meta.env ?? {}) {
  const configuredOrigin = env.VITE_OAUTH_ORIGIN?.trim().replace(/\/+$/, '')
  const origin = configuredOrigin || (env.DEV ? '' : PRODUCTION_OAUTH_ORIGIN)
  return `${origin}/api/auth/login`
}

export function currentReturnPath(location = window.location) {
  return `${location.pathname || '/'}${location.search || ''}${location.hash || ''}`
}

export function rememberReturnPath(path = currentReturnPath()) {
  const safePath = safeReturnPath(path)
  if (safePath !== '/login') window.sessionStorage.setItem(RETURN_PATH_KEY, safePath)
  return safePath
}

export function consumeReturnPath(fallback = '/') {
  const destination = getReturnPath(fallback)
  window.sessionStorage.removeItem(RETURN_PATH_KEY)
  return destination
}

export function getReturnPath(fallback = '/') {
  return safeReturnPath(window.sessionStorage.getItem(RETURN_PATH_KEY) || fallback)
}

export function clearReturnPath() {
  window.sessionStorage.removeItem(RETURN_PATH_KEY)
}

export function requestLogin(navigate, location) {
  rememberReturnPath(currentReturnPath(location))
  navigate('/login')
}

function safeReturnPath(path) {
  const value = String(path || '/')
  return value.startsWith('/') && !value.startsWith('//') ? value : '/'
}
