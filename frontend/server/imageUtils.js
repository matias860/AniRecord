import { Buffer } from 'node:buffer'
import sharp from 'sharp'

const MAX_REMOTE_IMAGE_BYTES = 5 * 1024 * 1024

export function safeText(value, maxLength) {
  return Array.from(String(value || '').normalize('NFKC'), (character) => {
    const code = character.charCodeAt(0)
    return code < 32 || code === 127 ? ' ' : character
  }).join('')
    .replace(/\s+/g, ' ').trim().slice(0, maxLength)
}

export function escapeXml(value) {
  return String(value).replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;',
  })[character])
}

export function wrapText(value, maxCharacters, maxLines = 3) {
  const words = String(value).split(/\s+/).filter(Boolean)
  const lines = []
  let current = ''
  let complete = true
  for (const word of words) {
    const candidate = current ? `${current} ${word}` : word
    if (candidate.length <= maxCharacters || !current) current = candidate
    else if (lines.length < maxLines - 1) { lines.push(current); current = word }
    else { complete = false; break }
  }
  if (current && lines.length < maxLines) lines.push(current)
  if (!complete && lines.length) lines[lines.length - 1] = `${lines.at(-1).replace(/…?$/, '')}…`
  return lines
}

export async function aniListImageData(value) {
  if (!value) return null
  let url
  try { url = new URL(value) } catch { return null }
  if (url.protocol !== 'https:' || !(url.hostname === 'anilist.co' || url.hostname.endsWith('.anilist.co'))) return null
  try {
    const response = await fetch(url, { redirect: 'error', signal: AbortSignal.timeout(5000) })
    const type = response.headers.get('content-type')?.split(';')[0]?.toLowerCase()
    const declaredSize = Number(response.headers.get('content-length') || 0)
    if (!response.ok || !/^image\/(png|jpe?g|webp)$/.test(type || '') || declaredSize > MAX_REMOTE_IMAGE_BYTES) return null
    const bytes = Buffer.from(await response.arrayBuffer())
    if (bytes.length > MAX_REMOTE_IMAGE_BYTES) return null
    return `data:${type};base64,${bytes.toString('base64')}`
  } catch { return null }
}

export async function pngResponse(svg, cacheControl) {
  const image = await sharp(Buffer.from(svg)).png({ compressionLevel: 9 }).toBuffer()
  return new Response(image, { headers: {
    'content-type': 'image/png', 'cache-control': cacheControl, 'x-content-type-options': 'nosniff',
  } })
}
