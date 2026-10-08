import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'

function worker(fetch) {
  const routes = []
  let precached = false
  let fallbacks = 0
  const source = readFileSync(new URL('../sw.js', import.meta.url), 'utf8').replace(/^import .*$/gm, '')
  const context = {
    fetch, self: { __WB_MANIFEST: [], addEventListener() {} },
    clientsClaim() {}, cleanupOutdatedCaches() {},
    precacheAndRoute() { precached = true; routes.push('precache') },
    createHandlerBoundToURL() {
      assert.equal(precached, true)
      return () => { fallbacks++; return 'offline shell' }
    },
    NavigationRoute: class { constructor(handler) { this.handler = handler } },
    registerRoute(route) { routes.push(route) },
    StaleWhileRevalidate: class {}, CacheFirst: class {},
  }
  runInNewContext(source, context)
  return { routes, fallbacks: () => fallbacks }
}

test('navigation reaches the network before precache and exposes security challenges', async () => {
  const response = { status: 429, headers: { 'x-vercel-mitigated': 'challenge' } }
  const sw = worker(async () => response)
  assert.equal(sw.routes[1], 'precache')
  assert.equal(await sw.routes[0].handler({ request: 'navigation' }), response)
  assert.equal(sw.fallbacks(), 0)
})

test('offline navigation still opens the cached app shell', async () => {
  const sw = worker(async () => { throw new TypeError('offline') })
  assert.equal(await sw.routes[0].handler({ request: 'navigation' }), 'offline shell')
  assert.equal(sw.fallbacks(), 1)
})
