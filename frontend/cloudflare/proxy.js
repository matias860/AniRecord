import { jsonError, secureResponse } from './security.js'

const BODYLESS_METHODS = new Set(['GET', 'HEAD'])
const HOP_BY_HOP_HEADERS = [
  'connection', 'keep-alive', 'proxy-authenticate', 'proxy-authorization',
  'te', 'trailer', 'transfer-encoding', 'upgrade', 'host',
]
const DIRECT_OAUTH_PATHS = new Set([
  '/api/auth/login', '/api/auth/connect',
  '/api/anilist/oauth/login', '/api/anilist/oauth/connect',
])

export async function proxyApiRequest(request, env) {
  const incomingUrl = new URL(request.url)
  if (!incomingUrl.pathname.startsWith('/api/')) return jsonError(404, 'Ruta no encontrada')

  let backendOrigin
  try {
    backendOrigin = new URL(env.BACKEND_ORIGIN)
  } catch {
    return jsonError(503, 'El backend no está configurado')
  }
  if (backendOrigin.protocol !== 'https:' || backendOrigin.pathname !== '/') {
    return jsonError(503, 'El backend no está configurado de forma segura')
  }

  const upstreamUrl = new URL(`${incomingUrl.pathname}${incomingUrl.search}`, backendOrigin)
  if (request.method === 'GET' && DIRECT_OAUTH_PATHS.has(incomingUrl.pathname)) {
    return secureResponse(Response.redirect(upstreamUrl, 307), 'private, no-store, max-age=0')
  }

  const headers = new Headers(request.headers)
  for (const name of HOP_BY_HOP_HEADERS) headers.delete(name)
  headers.delete('cf-ray')
  headers.delete('cf-visitor')
  headers.set('Accept-Encoding', 'identity')
  headers.set('X-Forwarded-Host', incomingUrl.host)
  headers.set('X-Forwarded-Proto', incomingUrl.protocol.replace(':', ''))

  const clientAddress = request.headers.get('CF-Connecting-IP')
  if (clientAddress) headers.set('X-Forwarded-For', clientAddress)
  if (env.PROXY_SECRET) headers.set('X-AniRecord-Proxy', env.PROXY_SECRET)

  let upstream
  try {
    upstream = await fetch(upstreamUrl, {
      method: request.method,
      headers,
      body: BODYLESS_METHODS.has(request.method) ? undefined : request.body,
      redirect: 'manual',
    })
  } catch {
    return jsonError(503, 'El backend no está disponible temporalmente')
  }

  const responseHeaders = copyResponseHeaders(upstream.headers)
  responseHeaders.delete('content-length')
  responseHeaders.delete('content-encoding')
  const response = new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  })
  return secureResponse(response)
}

function copyResponseHeaders(source) {
  const headers = new Headers(source)
  const cookies = typeof source.getSetCookie === 'function' ? source.getSetCookie() : []
  if (cookies.length) {
    headers.delete('set-cookie')
    for (const cookie of cookies) headers.append('set-cookie', cookie)
  }
  return headers
}
