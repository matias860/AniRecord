import { secureResponse } from '../../cloudflare/security.js'

export async function onRequestGet({ request, env }) {
  const size = Number(new URL(request.url).searchParams.get('size'))
  const assetPath = size > 192 ? '/pwa-512x512.png' : '/pwa-192x192.png'
  const asset = await env.ASSETS.fetch(new URL(assetPath, request.url))
  return secureResponse(asset, 'public, max-age=604800, immutable')
}
