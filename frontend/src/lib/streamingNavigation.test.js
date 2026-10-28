import assert from 'node:assert/strict'
import test from 'node:test'
import { androidStreamingIntent, isAndroidUserAgent, resolveStreamingDestination, streamingSearchUrl } from './streamingNavigation.js'

const android = 'Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 Chrome/152 Mobile Safari/537.36'
const desktop = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/152 Safari/537.36'

test('opens a verified provider in its Android app with a Google fallback', () => {
  const destination = resolveStreamingDestination({
    link: { site: 'Crunchyroll', url: 'http://www.crunchyroll.com/one-piece' },
    title: 'One Piece',
    userAgent: android,
  })
  assert.equal(destination.kind, 'ANDROID_INTENT')
  assert.match(destination.href, /^intent:\/\/www\.crunchyroll\.com\/one-piece#Intent;scheme=https;/)
  assert.match(destination.href, /package=com\.crunchyroll\.crunchyroid;/)
  assert.match(destination.href, /S\.browser_fallback_url=https%3A%2F%2Fwww\.google\.com%2Fsearch%3Fq%3DOne%2BPiece%2BCrunchyroll%2Bver%2Bonline;/)
  assert.equal(destination.target, undefined)
})

test('keeps the official HTTPS page on desktop', () => {
  const destination = resolveStreamingDestination({
    link: { site: 'Amazon Prime Video', url: 'https://www.primevideo.com/detail/ABC123' },
    title: 'Frieren',
    userAgent: desktop,
  })
  assert.deepEqual(destination, {
    href: 'https://www.primevideo.com/detail/ABC123',
    kind: 'OFFICIAL_WEB_URL',
    target: '_blank',
  })
})

test('recognizes every verified AniList provider alias', () => {
  const samples = [
    ['Netflix', 'https://www.netflix.com/title/123', 'com.netflix.mediaclient'],
    ['Prime Video', 'https://primevideo.com/detail/123', 'com.amazon.avod.thirdpartyclient'],
    ['Disney+', 'https://www.disneyplus.com/series/example', 'com.disney.disneyplus'],
    ['HIDIVE', 'https://www.hidive.com/stream/example', 'com.hidive.android'],
    ['Hulu', 'https://www.hulu.com/series/example', 'com.hulu.plus'],
    ['YouTube', 'https://youtu.be/abc1234', 'com.google.android.youtube'],
  ]
  for (const [site, url, packageName] of samples) {
    const destination = resolveStreamingDestination({ link: { site, url }, title: 'Ejemplo', userAgent: android })
    assert.equal(destination.kind, 'ANDROID_INTENT')
    assert.match(destination.href, new RegExp(`package=${packageName.replaceAll('.', '\\.')};`))
  }
})

test('unknown providers use Google instead of guessing an Android package', () => {
  const destination = resolveStreamingDestination({
    link: { site: 'Bilibili TV', url: 'https://www.bilibili.tv/media/123' },
    title: 'Frieren',
    romajiTitle: 'Sousou no Frieren',
    userAgent: android,
  })
  assert.equal(destination.kind, 'GOOGLE_SEARCH_URL')
  assert.equal(new URL(destination.href).searchParams.get('q'), 'Frieren Sousou no Frieren Bilibili TV ver online')
  assert.doesNotMatch(destination.href, /intent:|package=/)
})

test('rejects lookalike domains and unsafe URL schemes', () => {
  for (const url of ['https://netflix.com.evil.example/title/123', 'javascript:alert(1)', 'https://user:pass@netflix.com/title/123']) {
    const destination = resolveStreamingDestination({ link: { site: 'Netflix', url }, title: 'Ejemplo', userAgent: android })
    assert.equal(destination.kind, 'GOOGLE_SEARCH_URL')
  }
})

test('keeps path and query in the Android intent and encodes its fallback', () => {
  const url = new URL('https://www.youtube.com/playlist?list=PL123')
  const fallback = streamingSearchUrl({ title: 'Ejemplo', site: 'YouTube' })
  const intent = androidStreamingIntent(url, 'com.google.android.youtube', fallback)
  assert.match(intent, /^intent:\/\/www\.youtube\.com\/playlist\?list=PL123#Intent;/)
  assert.ok(intent.includes(`S.browser_fallback_url=${encodeURIComponent(fallback)};end`))
})

test('detects Android without treating desktop or iOS as Android', () => {
  assert.equal(isAndroidUserAgent(android), true)
  assert.equal(isAndroidUserAgent(desktop), false)
  assert.equal(isAndroidUserAgent('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)'), false)
})

test('builds a useful generic search when streaming data is incomplete', () => {
  const destination = resolveStreamingDestination({ link: {}, title: 'Blue Box', userAgent: android })
  assert.equal(destination.kind, 'GOOGLE_SEARCH_URL')
  assert.equal(new URL(destination.href).searchParams.get('q'), 'Blue Box dónde ver online')
})
