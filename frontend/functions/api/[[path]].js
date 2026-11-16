import { proxyApiRequest } from '../../cloudflare/proxy.js'

export function onRequest({ request, env }) {
  return proxyApiRequest(request, env)
}
