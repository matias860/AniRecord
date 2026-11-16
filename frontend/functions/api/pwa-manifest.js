import { GET } from '../../api/pwa-manifest.js'
import { secureResponse } from '../../cloudflare/security.js'

export function onRequestGet({ request }) {
  return secureResponse(GET(request))
}
