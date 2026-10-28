const GOOGLE_SEARCH_URL = 'https://www.google.com/search'

const PROVIDERS = [
  {
    key: 'crunchyroll',
    aliases: ['crunchyroll'],
    domains: ['crunchyroll.com'],
    androidPackage: 'com.crunchyroll.crunchyroid',
  },
  {
    key: 'netflix',
    aliases: ['netflix'],
    domains: ['netflix.com'],
    androidPackage: 'com.netflix.mediaclient',
  },
  {
    key: 'prime-video',
    aliases: ['amazon prime video', 'prime video', 'amazon video'],
    domains: ['primevideo.com'],
    androidPackage: 'com.amazon.avod.thirdpartyclient',
  },
  {
    key: 'disney-plus',
    aliases: ['disney+', 'disney plus'],
    domains: ['disneyplus.com'],
    androidPackage: 'com.disney.disneyplus',
  },
  {
    key: 'hidive',
    aliases: ['hidive'],
    domains: ['hidive.com'],
    androidPackage: 'com.hidive.android',
  },
  {
    key: 'hulu',
    aliases: ['hulu'],
    domains: ['hulu.com'],
    androidPackage: 'com.hulu.plus',
  },
  {
    key: 'youtube',
    aliases: ['youtube'],
    domains: ['youtube.com', 'youtu.be'],
    androidPackage: 'com.google.android.youtube',
  },
]

function normalizedProviderName(value) {
  return String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9+]+/g, ' ')
    .trim()
}

function providerFor(site) {
  const normalized = normalizedProviderName(site)
  return PROVIDERS.find((provider) => provider.aliases.some((alias) => normalizedProviderName(alias) === normalized)) || null
}

function trustedProviderUrl(value, provider) {
  if (!provider) return null
  try {
    const url = new URL(value)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.port) return null
    const host = url.hostname.toLowerCase().replace(/\.$/, '')
    const trusted = provider.domains.some((domain) => host === domain || host.endsWith(`.${domain}`))
    if (!trusted) return null
    url.protocol = 'https:'
    return url
  } catch {
    return null
  }
}

export function streamingSearchUrl({ title, romajiTitle, site } = {}) {
  const primaryTitle = String(title || romajiTitle || '').trim()
  const alternateTitle = String(romajiTitle || '').trim()
  const parts = [primaryTitle]
  if (alternateTitle && normalizedProviderName(alternateTitle) !== normalizedProviderName(primaryTitle)) parts.push(alternateTitle)
  if (site) parts.push(String(site).trim(), 'ver online')
  else parts.push('dónde ver online')
  const query = parts.filter(Boolean).join(' ').trim() || 'anime dónde ver online'
  const url = new URL(GOOGLE_SEARCH_URL)
  url.searchParams.set('q', query)
  return url.href
}

export function isAndroidUserAgent(userAgent = globalThis.navigator?.userAgent || '') {
  return /android/i.test(userAgent)
}

export function androidStreamingIntent(url, androidPackage, fallbackUrl) {
  const path = `${url.hostname}${url.pathname}${url.search}`
  return `intent://${path}#Intent;scheme=https;package=${androidPackage};S.browser_fallback_url=${encodeURIComponent(fallbackUrl)};end`
}

export function resolveStreamingDestination({ link, title, romajiTitle, userAgent } = {}) {
  const site = String(link?.site || '').trim()
  const fallbackUrl = streamingSearchUrl({ title, romajiTitle, site })
  const provider = providerFor(site)
  const officialUrl = trustedProviderUrl(link?.url, provider)

  if (!officialUrl) {
    return { href: fallbackUrl, kind: 'GOOGLE_SEARCH_URL', target: '_blank' }
  }
  if (isAndroidUserAgent(userAgent)) {
    return {
      href: androidStreamingIntent(officialUrl, provider.androidPackage, fallbackUrl),
      kind: 'ANDROID_INTENT',
    }
  }
  return { href: officialUrl.href, kind: 'OFFICIAL_WEB_URL', target: '_blank' }
}

export const supportedStreamingProviders = PROVIDERS.map(({ key, aliases, domains }) => ({ key, aliases, domains }))
