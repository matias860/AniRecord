const DEFAULT_ACCENT = '#9b72ff'
const DEFAULT_BACKGROUND = '#080a12'
const ICON_VERSION = '3'
const FAVICON_VERSION = '4'

function validColor(value, fallback) {
  return /^#[0-9a-f]{6}$/i.test(value || '') ? value : fallback
}

export function pwaManifestUrl(accent, background) {
  const query = new URLSearchParams({
    accent: validColor(accent, DEFAULT_ACCENT).slice(1),
    background: validColor(background, DEFAULT_BACKGROUND).slice(1),
    v: ICON_VERSION,
  })
  return `/api/pwa-manifest?${query}`
}

export function pwaFaviconUrl(accent, background) {
  const query = new URLSearchParams({
    accent: validColor(accent, DEFAULT_ACCENT).slice(1),
    background: validColor(background, DEFAULT_BACKGROUND).slice(1),
    shape: 'rounded',
    v: FAVICON_VERSION,
  })
  return `/api/pwa-icon?size=128&${query}`
}

export function updatePwaBranding(accent, background) {
  const accentColor = validColor(accent, DEFAULT_ACCENT)
  const backgroundColor = validColor(background, DEFAULT_BACKGROUND)
  const query = new URLSearchParams({
    accent: accentColor.slice(1),
    background: backgroundColor.slice(1),
    v: ICON_VERSION,
  })
  const manifest = document.querySelector('#anirecord-manifest') || document.querySelector('link[rel="manifest"]')
  if (manifest) manifest.href = pwaManifestUrl(accentColor, backgroundColor)

  const iconUrl = pwaFaviconUrl(accentColor, backgroundColor)
  const favicon = document.querySelector('link[rel="icon"]')
  if (favicon) {
    favicon.href = iconUrl
    favicon.type = 'image/png'
  }
  const appleIcon = document.querySelector('link[rel="apple-touch-icon"]')
  if (appleIcon) appleIcon.href = `/api/pwa-icon?size=180&${query}`

  const themeMeta = document.querySelector('meta[name="theme-color"]')
  if (themeMeta) themeMeta.content = backgroundColor
  try { window.localStorage.setItem('anirecord-pwa-theme', JSON.stringify({ accent: accentColor, background: backgroundColor })) } catch { /* Branding still works when storage is unavailable. */ }
}
