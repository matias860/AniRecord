/// <reference lib="webworker" />
import { clientsClaim } from 'workbox-core'
import { cleanupOutdatedCaches, createHandlerBoundToURL, precacheAndRoute } from 'workbox-precaching'
import { NavigationRoute, registerRoute } from 'workbox-routing'
import { CacheFirst, StaleWhileRevalidate } from 'workbox-strategies'

clientsClaim()
cleanupOutdatedCaches()
precacheAndRoute(self.__WB_MANIFEST)

self.addEventListener('message', (event) => {
  if (event.data?.type === 'SKIP_WAITING') self.skipWaiting()
})

registerRoute(new NavigationRoute(createHandlerBoundToURL('/index.html'), { denylist: [/^\/api\//] }))
registerRoute(({ request, url }) => url.origin === self.location.origin && ['style', 'script', 'font'].includes(request.destination), new StaleWhileRevalidate({ cacheName: 'anirecord-static-v1' }))
registerRoute(({ request, url }) => url.origin === self.location.origin && !url.pathname.startsWith('/api/') && request.destination === 'image', new CacheFirst({ cacheName: 'anirecord-images-v1' }))

self.addEventListener('push', (event) => {
  let data
  try { data = event.data?.json() || {} } catch { data = { body: event.data?.text() || 'Hay una novedad en AniRecord.' } }
  event.waitUntil(self.registration.showNotification(data.title || 'AniRecord', {
    body: data.body || 'Hay una novedad en tu biblioteca.',
    icon: data.icon || '/pwa-notification-192x192.png',
    badge: '/pwa-badge-96x96.png',
    tag: data.tag || 'anirecord-update',
    renotify: false,
    data: { url: data.url || '/notificaciones' },
  }))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const destination = new URL(event.notification.data?.url || '/', self.location.origin).href
  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(async (windows) => {
    const existing = windows.find((client) => new URL(client.url).origin === self.location.origin)
    if (existing) { await existing.focus(); return existing.navigate(destination) }
    return self.clients.openWindow(destination)
  }))
})
