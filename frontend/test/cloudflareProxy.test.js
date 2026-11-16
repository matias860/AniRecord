import assert from 'node:assert/strict'
import test from 'node:test'

import { proxyApiRequest } from '../cloudflare/proxy.js'

const env = {
  BACKEND_ORIGIN: 'https://backend.example',
  PROXY_SECRET: 'shared-secret',
}

test('proxies API requests to the backend with the original query and trusted client data', async (t) => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })

  let receivedUrl
  let receivedInit
  globalThis.fetch = async (url, init) => {
    receivedUrl = url
    receivedInit = init
    return Response.json({ status: 'ok' }, { headers: { 'Cache-Control': 'no-cache' } })
  }

  const response = await proxyApiRequest(new Request('https://anirecord.com.ar/api/health?full=true', {
    headers: { 'CF-Connecting-IP': '203.0.113.7' },
  }), env)

  assert.equal(receivedUrl.href, 'https://backend.example/api/health?full=true')
  assert.equal(receivedInit.method, 'GET')
  assert.equal(receivedInit.headers.get('X-Forwarded-For'), '203.0.113.7')
  assert.equal(receivedInit.headers.get('X-Forwarded-Host'), 'anirecord.com.ar')
  assert.equal(receivedInit.headers.get('X-Forwarded-Proto'), 'https')
  assert.equal(receivedInit.headers.get('X-AniRecord-Proxy'), 'shared-secret')
  assert.equal(response.status, 200)
  assert.equal(response.headers.get('X-Frame-Options'), 'DENY')
  assert.deepEqual(await response.json(), { status: 'ok' })
})

test('preserves POST bodies, application headers and upstream cookies', async (t) => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })

  let receivedInit
  globalThis.fetch = async (_url, init) => {
    receivedInit = init
    return new Response(null, {
      status: 204,
      headers: { 'Set-Cookie': 'ANIRECORD_SESSION=session; Path=/; Secure; HttpOnly; SameSite=Lax' },
    })
  }

  const request = new Request('https://anirecord.com.ar/api/user/preferences', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
      Cookie: 'ANIRECORD_SESSION=old',
    },
    body: JSON.stringify({ titleLanguage: 'ROMAJI' }),
  })
  const response = await proxyApiRequest(request, env)

  assert.equal(await new Response(receivedInit.body).text(), '{"titleLanguage":"ROMAJI"}')
  assert.equal(receivedInit.headers.get('X-Requested-With'), 'XMLHttpRequest')
  assert.equal(receivedInit.headers.get('Cookie'), 'ANIRECORD_SESSION=old')
  assert.match(response.headers.get('Set-Cookie'), /ANIRECORD_SESSION=session/)
  assert.equal(response.status, 204)
})

test('redirects OAuth entry points directly to the backend without consuming a Worker fetch', async (t) => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  globalThis.fetch = async () => { throw new Error('OAuth redirect must not fetch') }

  const response = await proxyApiRequest(
    new Request('https://anirecord.com.ar/api/anilist/oauth/login?returnTo=%2Fperfil'),
    env,
  )

  assert.equal(response.status, 307)
  assert.equal(response.headers.get('Location'), 'https://backend.example/api/anilist/oauth/login?returnTo=%2Fperfil')
  assert.equal(response.headers.get('Cache-Control'), 'private, no-store, max-age=0')
})

test('rejects unsafe backend configuration and requests outside the API namespace', async () => {
  const unsafe = await proxyApiRequest(
    new Request('https://anirecord.com.ar/api/health'),
    { BACKEND_ORIGIN: 'http://backend.example' },
  )
  const missing = await proxyApiRequest(
    new Request('https://anirecord.com.ar/catalogo'),
    env,
  )

  assert.equal(unsafe.status, 503)
  assert.equal(missing.status, 404)
  assert.match((await unsafe.json()).message, /forma segura/)
})

test('returns a stable service-unavailable response when the backend cannot be reached', async (t) => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  globalThis.fetch = async () => { throw new Error('network unavailable') }

  const response = await proxyApiRequest(new Request('https://anirecord.com.ar/api/health'), env)

  assert.equal(response.status, 503)
  assert.equal(response.headers.get('Cache-Control'), 'private, no-store, max-age=0')
  assert.deepEqual(await response.json(), {
    status: 503,
    message: 'El backend no está disponible temporalmente',
    errors: {},
  })
})
