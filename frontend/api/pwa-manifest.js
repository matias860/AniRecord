export const config = { runtime: 'edge' }

const color = (value, fallback) => /^[0-9a-f]{6}$/i.test(value || '') ? value.toLowerCase() : fallback

export default function handler(request) {
  const params = new URL(request.url).searchParams
  const accent = color(params.get('accent'), '9b72ff')
  const background = color(params.get('background'), '080a12')
  const icon = (size, purpose) => ({
    src: `/api/pwa-icon?size=${size}&accent=${accent}&background=${background}`,
    sizes: `${size}x${size}`,
    type: 'image/png',
    ...(purpose ? { purpose } : {}),
  })
  return Response.json({
    id: '/',
    name: 'AniRecord',
    short_name: 'AniRecord',
    description: 'Tu biblioteca personal de anime y manga.',
    lang: 'es-AR',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    background_color: `#${background}`,
    theme_color: `#${accent}`,
    categories: ['entertainment', 'lifestyle'],
    icons: [icon(192), icon(512), icon(512, 'maskable')],
  }, { headers: { 'cache-control': 'private, max-age=300', 'content-type': 'application/manifest+json' } })
}
