export const config = { runtime: 'edge' }

const ICON_VERSION = '3'
const color = (value, fallback) => /^[0-9a-f]{6}$/i.test(value || '') ? value.toLowerCase() : fallback

export default function handler(request) {
  const params = new URL(request.url).searchParams
  const accent = color(params.get('accent'), '9b72ff')
  const background = color(params.get('background'), '080a12')
  const manifestUrl = `/api/pwa-manifest?accent=${accent}&background=${background}&v=${ICON_VERSION}`
  const icon = (size) => ({
    src: `/api/pwa-icon?size=${size}&accent=${accent}&background=${background}&v=${ICON_VERSION}`,
    sizes: `${size}x${size}`,
    type: 'image/png',
    purpose: 'any maskable',
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
    theme_color: `#${background}`,
    categories: ['entertainment', 'lifestyle'],
    icons: [icon(192), icon(512)],
    related_applications: [{ platform: 'webapp', url: manifestUrl, id: '/' }],
    prefer_related_applications: false,
  }, { headers: { 'cache-control': 'private, no-store', 'content-type': 'application/manifest+json' } })
}
