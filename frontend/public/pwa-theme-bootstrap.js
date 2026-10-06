(() => {
  const fallback = { accent: '#9b72ff', background: '#080a12' }
  const valid = (value, defaultValue) => /^#[0-9a-f]{6}$/i.test(value || '') ? value : defaultValue
  let stored = fallback
  try { stored = { ...fallback, ...JSON.parse(localStorage.getItem('anirecord-pwa-theme') || '{}') } } catch { /* Use the default brand before React starts. */ }
  const accent = valid(stored.accent, fallback.accent)
  const background = valid(stored.background, fallback.background)
  const query = new URLSearchParams({ accent: accent.slice(1), background: background.slice(1), v: '3' })
  const manifest = document.createElement('link')
  manifest.id = 'anirecord-manifest'
  manifest.rel = 'manifest'
  manifest.href = `/api/pwa-manifest?${query}`
  document.head.append(manifest)
  document.documentElement.style.setProperty('--user-bg', background)
  document.documentElement.style.setProperty('--accent', accent)
  const theme = document.querySelector('meta[name="theme-color"]')
  if (theme) theme.content = background
})()
