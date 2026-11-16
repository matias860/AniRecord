const SECURITY_HEADERS = {
  'Content-Security-Policy': "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self' https://anilist.co; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self' data:; img-src 'self' data: blob: https:; media-src 'self' https:; connect-src 'self' https://graphql.anilist.co; frame-src https://www.youtube.com https://www.youtube-nocookie.com https://www.dailymotion.com; worker-src 'self' blob:; manifest-src 'self'; upgrade-insecure-requests",
  'Referrer-Policy': 'no-referrer',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=(), usb=(), browsing-topics=()',
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'X-Permitted-Cross-Domain-Policies': 'none',
  'Cross-Origin-Opener-Policy': 'same-origin',
  'Origin-Agent-Cluster': '?1',
}

export function secureResponse(response, cacheControl) {
  const headers = new Headers(response.headers)
  for (const [name, value] of Object.entries(SECURITY_HEADERS)) headers.set(name, value)
  if (cacheControl) headers.set('Cache-Control', cacheControl)
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers })
}

export function jsonError(status, message) {
  return secureResponse(Response.json({ status, message, errors: {} }, { status }), 'private, no-store, max-age=0')
}
