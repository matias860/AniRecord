import { secureResponse } from '../../cloudflare/security.js'

export async function onRequestGet({ request, env }) {
  const asset = await env.ASSETS.fetch(new URL('/og-default.png', request.url))
  return secureResponse(asset, 'public, max-age=86400, stale-while-revalidate=604800')
}
