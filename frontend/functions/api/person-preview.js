import { GET } from '../../api/person-preview.js'
import { secureResponse } from '../../cloudflare/security.js'

export async function onRequestGet({ request }) {
  return secureResponse(await GET(request))
}
