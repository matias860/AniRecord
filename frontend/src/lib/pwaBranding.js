const DEFAULT_ACCENT = '#9b72ff'
const DEFAULT_BACKGROUND = '#080a12'
const ICON_VERSION = '2'

function validColor(value, fallback) {
  return /^#[0-9a-f]{6}$/i.test(value || '') ? value : fallback
}

export function blendHexColors(foreground, background, weight = 0.12) {
  const front = validColor(foreground, DEFAULT_ACCENT).slice(1).match(/../g).map((part) => Number.parseInt(part, 16))
  const back = validColor(background, DEFAULT_BACKGROUND).slice(1).match(/../g).map((part) => Number.parseInt(part, 16))
  const channel = (index) => Math.round(front[index] * weight + back[index] * (1 - weight)).toString(16).padStart(2, '0')
  return `#${channel(0)}${channel(1)}${channel(2)}`
}

export function updatePwaBranding(accent, background) {
  const accentColor = validColor(accent, DEFAULT_ACCENT)
  const backgroundColor = validColor(background, DEFAULT_BACKGROUND)
  const query = new URLSearchParams({
    accent: accentColor.slice(1),
    background: backgroundColor.slice(1),
    v: ICON_VERSION,
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

  const systemBarColor = blendHexColors(accentColor, backgroundColor)
  const themeMeta = document.querySelector('meta[name="theme-color"]')
  if (themeMeta) themeMeta.content = systemBarColor
}
