const ANILIST_ENDPOINT = 'https://graphql.anilist.co'
const SITE_NAME = 'Anime List'

export const config = { runtime: 'edge' }

const MEDIA_QUERY = `
  query ShareMedia($id: Int!) {
    Media(id: $id) {
      id
      type
      format
      status
      title { english romaji }
      coverImage { extraLarge large }
      bannerImage
      description
    }
  }
`

export default async function handler(request) {
  const url = new URL(request.url)
  const id = Number(url.searchParams.get('id'))
  const siteOrigin = url.origin
  const canonicalUrl = `${siteOrigin}/media/${id}`

  if (!Number.isInteger(id) || id <= 0) return new Response('Serie no válida', { status: 400 })

  const media = await fetchMedia(id)
  const title = media?.title?.english || media?.title?.romaji || `Serie #${id}`
  const type = mediaTypeLabel(media)
  const status = statusLabel(media?.status)
  const description = plainText(media?.description) || `Consulta ${title} en tu biblioteca de Anime List.`
  const imageUrl = new URL('/api/og-image', siteOrigin)
  imageUrl.searchParams.set('title', title)
  imageUrl.searchParams.set('type', type)
  imageUrl.searchParams.set('status', status)
  if (media?.coverImage?.extraLarge || media?.coverImage?.large) imageUrl.searchParams.set('cover', media.coverImage.extraLarge || media.coverImage.large)
  if (media?.bannerImage) imageUrl.searchParams.set('banner', media.bannerImage)

  const html = `<!doctype html>
<html lang="es">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>${escapeHtml(title)} · ${SITE_NAME}</title>
    <meta name="description" content="${escapeHtml(description)}">
    <link rel="canonical" href="${canonicalUrl}">
    <meta property="og:type" content="website">
    <meta property="og:site_name" content="${SITE_NAME}">
    <meta property="og:title" content="${escapeHtml(title)}">
    <meta property="og:description" content="${escapeHtml(`${type} · ${status}. ${description}`)}">
    <meta property="og:url" content="${canonicalUrl}">
    <meta property="og:image" content="${escapeHtml(imageUrl.toString())}">
    <meta property="og:image:secure_url" content="${escapeHtml(imageUrl.toString())}">
    <meta property="og:image:type" content="image/png">
    <meta property="og:image:width" content="1200">
    <meta property="og:image:height" content="630">
    <meta property="og:image:alt" content="Tarjeta de ${escapeHtml(title)} en Anime List">
    <meta name="twitter:card" content="summary_large_image">
    <meta name="twitter:title" content="${escapeHtml(title)}">
    <meta name="twitter:description" content="${escapeHtml(`${type} · ${status}`)}">
    <meta name="twitter:image" content="${escapeHtml(imageUrl.toString())}">
  </head>
  <body style="font-family:system-ui;background:#0a0c14;color:#eef0f8;padding:40px">
    <h1>${escapeHtml(title)}</h1>
    <p>${escapeHtml(`${type} · ${status}`)}</p>
    <p><a style="color:#a88bff" href="${canonicalUrl}">Abrir en Anime List</a></p>
  </body>
</html>`

  return new Response(html, {
    headers: {
      'content-type': 'text/html; charset=utf-8',
      'cache-control': 'public, s-maxage=3600, stale-while-revalidate=86400',
    },
  })
}

async function fetchMedia(id) {
  try {
    const response = await fetch(ANILIST_ENDPOINT, {
      method: 'POST',
      headers: { 'content-type': 'application/json', accept: 'application/json' },
      body: JSON.stringify({ query: MEDIA_QUERY, variables: { id } }),
      signal: AbortSignal.timeout(6000),
    })
    if (!response.ok) return null
    return (await response.json())?.data?.Media || null
  } catch {
    return null
  }
}

function statusLabel(status) {
  return {
    RELEASING: 'En emisión', FINISHED: 'Finalizado', NOT_YET_RELEASED: 'Próximamente',
    CANCELLED: 'Cancelado', HIATUS: 'En pausa',
  }[status] || 'En Anime List'
}

function mediaTypeLabel(media) {
  if (media?.format === 'MUSIC') return 'Music video'
  if (media?.format === 'NOVEL') return 'Light novel'
  return media?.type === 'ANIME' ? 'Anime' : 'Manga'
}

function plainText(value) {
  return String(value || '')
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 180)
}

function escapeHtml(value) {
  return String(value || '').replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;',
  })[character])
}
