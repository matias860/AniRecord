const DEFAULT_ACCENT = '#9b72ff'
const DEFAULT_BACKGROUND = '#080a12'

function validColor(value, fallback) {
  return /^#[0-9a-f]{6}$/i.test(value || '') ? value : fallback
}

export function updatePwaBranding(accent, background) {
  const accentColor = validColor(accent, DEFAULT_ACCENT)
  const backgroundColor = validColor(background, DEFAULT_BACKGROUND)
  const query = new URLSearchParams({
    accent: accentColor.slice(1),
    background: backgroundColor.slice(1),
  })
  const manifest = document.querySelector('link[rel="manifest"]')
  if (manifest) manifest.href = `/api/pwa-manifest?${query}`

  const iconUrl = `/api/pwa-icon?size=192&${query}`
  const favicon = document.querySelector('link[rel="icon"]')
  if (favicon) {
    favicon.href = iconUrl
    favicon.type = 'image/png'
  }
  const appleIcon = document.querySelector('link[rel="apple-touch-icon"]')
  if (appleIcon) appleIcon.href = `/api/pwa-icon?size=180&${query}`
}
