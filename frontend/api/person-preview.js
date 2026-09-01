const ANILIST_ENDPOINT = 'https://graphql.anilist.co'
const SITE_NAME = 'AniRecord'
const SITE_ORIGIN = 'https://anirecord.com.ar'
const OG_VERSION = '20260901'

export const config = { runtime: 'edge' }

const CHARACTER_QUERY = `
  query ShareCharacter($id: Int!) {
    Character(id: $id) {
      name { full }
      image { large }
      description
      media(page: 1, perPage: 1, sort: POPULARITY_DESC) {
        nodes { type format title { english romaji } }
      }
    }
  }
`

const STAFF_QUERY = `
  query ShareStaff($id: Int!) {
    Staff(id: $id) {
      name { full }
      image { large }
      description
      age
      gender
      primaryOccupations
      staffMedia(page: 1, perPage: 1, sort: POPULARITY_DESC) {
        edges { staffRole node { type format title { english romaji } } }
      }
    }
  }
`

export default async function handler(request) {
  const url = new URL(request.url)
  const id = Number(url.searchParams.get('id'))
  const requestedKind = url.searchParams.get('kind')
  const kind = requestedKind === 'staff' || requestedKind === 'professional' ? requestedKind : 'character'
  if (!Number.isInteger(id) || id <= 0) return new Response('Persona no válida', { status: 400 })

  const person = await fetchPerson(id, kind)
  const isStaff = kind === 'staff' || kind === 'professional'
  const isProfessional = kind === 'professional'
  const route = isProfessional ? 'profesionales' : isStaff ? 'seiyuus' : 'personajes'
  const label = isProfessional ? 'Profesional' : isStaff ? 'Seiyū' : 'Personaje'
  const professionalMedia = person?.staffMedia?.edges?.[0]
  const media = isProfessional ? professionalMedia?.node : isStaff ? null : person?.media?.nodes?.[0]
  const name = person?.name?.full || `${label} #${id}`
  const origin = media?.title?.english || media?.title?.romaji || ''
  const originType = mediaType(media)
  const canonicalUrl = `${SITE_ORIGIN}/${route}/${id}`
  const description = plainText(person?.description)
    || (origin ? `${name}, personaje de ${origin}.` : `Consulta a ${name} en AniRecord.`)
  const imageUrl = new URL('/api/og-person-image', url.origin)
  imageUrl.searchParams.set('v', OG_VERSION)
  imageUrl.searchParams.set('name', name)
  imageUrl.searchParams.set('kind', label)
  if (kind === 'staff') {
    if (person?.age) imageUrl.searchParams.set('age', String(person.age))
    if (person?.gender) imageUrl.searchParams.set('gender', genderLabel(person.gender))
  } else {
    if (origin) imageUrl.searchParams.set('origin', origin)
    if (isProfessional && professionalMedia?.staffRole) imageUrl.searchParams.set('originType', professionalRoleLabel(professionalMedia.staffRole))
    else if (originType) imageUrl.searchParams.set('originType', originType)
  }
  if (person?.image?.large) imageUrl.searchParams.set('image', person.image.large)

  const detailText = kind === 'staff'
    ? staffDetails(person)
    : isProfessional ? professionalDetails(person, professionalMedia, origin)
    : origin ? `De ${origin}${originType ? ` · ${originType}` : ''}` : label
  const html = `<!doctype html>
<html lang="es">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>${escapeHtml(name)} · ${SITE_NAME}</title>
    <meta name="description" content="${escapeHtml(description)}">
    <link rel="canonical" href="${canonicalUrl}">
    <meta property="og:type" content="profile">
    <meta property="og:site_name" content="${SITE_NAME}">
    <meta property="og:title" content="${escapeHtml(name)}">
    <meta property="og:description" content="${escapeHtml(detailText)}">
    <meta property="og:url" content="${canonicalUrl}">
    <meta property="og:image" content="${escapeHtml(imageUrl.toString())}">
    <meta property="og:image:secure_url" content="${escapeHtml(imageUrl.toString())}">
    <meta property="og:image:type" content="image/png">
    <meta property="og:image:width" content="1200">
    <meta property="og:image:height" content="630">
    <meta property="og:image:alt" content="Tarjeta de ${escapeHtml(name)} en AniRecord">
    <meta name="twitter:card" content="summary_large_image">
    <meta name="twitter:title" content="${escapeHtml(name)}">
    <meta name="twitter:description" content="${escapeHtml(detailText)}">
    <meta name="twitter:image" content="${escapeHtml(imageUrl.toString())}">
  </head>
  <body style="font-family:system-ui;background:#0a0c14;color:#eef0f8;padding:40px">
    <h1>${escapeHtml(name)}</h1>
    <p>${escapeHtml(detailText)}</p>
    <p><a style="color:#a88bff" href="${canonicalUrl}">Abrir en AniRecord</a></p>
  </body>
</html>`

  return new Response(html, { headers: {
    'content-type': 'text/html; charset=utf-8',
    'cache-control': 'public, s-maxage=3600, stale-while-revalidate=86400',
  } })
}

async function fetchPerson(id, kind) {
  try {
    const query = kind === 'character' ? CHARACTER_QUERY : STAFF_QUERY
    const response = await fetch(ANILIST_ENDPOINT, {
      method: 'POST',
      headers: { 'content-type': 'application/json', accept: 'application/json' },
      body: JSON.stringify({ query, variables: { id } }),
      signal: AbortSignal.timeout(6000),
    })
    if (!response.ok) return null
    const data = (await response.json())?.data
    return kind === 'character' ? data?.Character : data?.Staff
  } catch {
    return null
  }
}

function mediaType(media) {
  if (!media) return ''
  if (media.type === 'ANIME') return 'Anime'
  return media.format === 'NOVEL' ? 'Light novel' : 'Manga'
}

function staffDetails(person) {
  const details = []
  if (person?.age) details.push(`${person.age} años`)
  if (person?.gender) details.push(genderLabel(person.gender))
  return details.join(' · ') || 'Seiyū en AniRecord'
}

function professionalDetails(person, mediaEdge, origin) {
  const occupation = person?.primaryOccupations?.[0]
  return [professionalRoleLabel(occupation), professionalRoleLabel(mediaEdge?.staffRole), origin].filter(Boolean).join(' · ') || 'Profesional en AniRecord'
}

function professionalRoleLabel(role) {
  return {
    Director: 'Dirección', Writer: 'Guion', Illustrator: 'Ilustración', Mangaka: 'Mangaka',
    'Original Creator': 'Obra original', 'Original Story': 'Historia original',
    'Series Composition': 'Composición de serie', 'Character Design': 'Diseño de personajes',
    Story: 'Historia', Art: 'Dibujo', 'Story & Art': 'Historia y dibujo',
  }[role] || role || ''
}

function genderLabel(gender) {
  return { Male: 'Hombre', Female: 'Mujer', Nonbinary: 'No binario' }[gender] || gender
}

function plainText(value) {
  return String(value || '').replace(/<br\s*\/?>/gi, ' ').replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ').replace(/&amp;/gi, '&').replace(/\s+/g, ' ').trim().slice(0, 180)
}

function escapeHtml(value) {
  return String(value || '').replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;',
  })[character])
}
