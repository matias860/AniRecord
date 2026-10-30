export const STATUS_LABELS = {
  PLANNED: 'Pendiente', IN_PROGRESS: 'En curso', PAUSED: 'Pausado',
  COMPLETED: 'Completado', DROPPED: 'Abandonado',
}

const normalizeAniListText = (value, preserveSpoilers = false) => {
  let text = (value ?? '')
  .replace(/<br\s*\/?\s*>/gi, '\n')
  .replace(/<[^>]*>/g, '')
  .replace(/(__|\*\*)(.*?)\1/g, '$2')
  .replace(/^\s*[-*]\s+/gm, '• ')
  .replace(/[ \t]+\n/g, '\n')
  .replace(/\n{3,}/g, '\n\n')
  .trim()
  if (!preserveSpoilers) text = text.replace(/~!([\s\S]*?)!~/g, '$1')
  return text
}

export const cleanAniListText = (value) => normalizeAniListText(value)
  .replace(/\[([^\]]+)]\([^\s)]+\)/g, '$1')

export const parseAniListText = (value) => {
  const text = normalizeAniListText(value, true)
  const spoilerPattern = /~!([\s\S]*?)!~/g
  const segments = []
  let cursor = 0
  for (const match of text.matchAll(spoilerPattern)) {
    if (match.index > cursor) segments.push(...parseAniListLinks(text.slice(cursor, match.index), false))
    segments.push(...parseAniListLinks(match[1], true))
    cursor = match.index + match[0].length
  }
  if (cursor < text.length) segments.push(...parseAniListLinks(text.slice(cursor), false))
  return segments
}

const parseAniListLinks = (text, spoiler) => {
  const linkPattern = /\[([^\]]+)]\((https?:\/\/[^\s)]+)\)/gi
  const segments = []
  let cursor = 0
  for (const match of text.matchAll(linkPattern)) {
    if (match.index > cursor) segments.push({ text: text.slice(cursor, match.index), spoiler })
    const href = match[2]
    const to = aniListInternalPath(href)
    segments.push(to ? { text: match[1], to, spoiler } : { text: match[1], href, spoiler })
    cursor = match.index + match[0].length
  }
  if (cursor < text.length) segments.push({ text: text.slice(cursor), spoiler })
  return segments
}

export const isUnreleasedAnime = (media) => media.mediaType === 'ANIME' && media.releaseStatus === 'NOT_YET_RELEASED'

export const normalizeMediaId = (value) => {
  const id = Number(value)
  return Number.isInteger(id) && id > 0 ? id : null
}

export const formatReleaseYears = (media) => {
  const start = media.releaseYear || media.releaseStartDate?.slice(0, 4)
  const end = media.releaseEndYear || media.releaseEndDate?.slice(0, 4)
  if (!start) return 'Sin fecha'
  return media.mediaType === 'MANGA' && media.releaseStatus === 'FINISHED' && end
    ? `${start} - ${end}` : String(start)
}

const aniListInternalPath = (href) => {
  try {
    const url = new URL(href)
    if (!['anilist.co', 'www.anilist.co'].includes(url.hostname.toLowerCase())) return null
    const [, resource, rawId] = url.pathname.split('/')
    const id = Number(rawId)
    if (!Number.isInteger(id) || id <= 0) return null
    if (resource === 'character') return `/personajes/${id}`
    if (resource === 'staff') return `/profesionales/${id}`
    if (resource === 'anime' || resource === 'manga') return `/media/${id}`
    return null
  } catch {
    return null
  }
}

export const cleanSynopsis = (value = '') => cleanAniListText(value).replace(/\s*\n\s*/g, ' ')

export const getProgressPercent = (progress, total) =>
  total ? Math.min(100, Math.round((progress / total) * 100)) : 0

export const formatRuntime = (minutes) => {
  const value = Number(minutes)
  if (!Number.isFinite(value) || value <= 0) return null
  const rounded = Math.round(value)
  const hours = Math.floor(rounded / 60)
  const remainingMinutes = rounded % 60
  if (!hours) return `${remainingMinutes} min`
  return remainingMinutes ? `${hours} h ${remainingMinutes} min` : `${hours} h`
}

export const watchTimeSummary = (media, progress = 0) => {
  const duration = Number(media?.insights?.durationMinutes)
  if (media?.mediaType !== 'ANIME' || !Number.isFinite(duration) || duration <= 0) return null
  const totalUnits = Number(media.totalUnits)
  const isMovie = media.mediaFormat === 'MOVIE'
  const totalMinutes = Number.isFinite(totalUnits) && totalUnits > 0 ? duration * totalUnits : null
  const safeProgress = Math.max(0, Number(progress) || 0)
  const remainingMinutes = totalMinutes == null ? null : Math.max(0, totalMinutes - Math.min(safeProgress, totalUnits) * duration)
  return {
    perUnit: isMovie ? null : formatRuntime(duration),
    duration: isMovie ? formatRuntime(totalMinutes || duration) : null,
    total: !isMovie && totalMinutes != null ? formatRuntime(totalMinutes) : null,
    remaining: !isMovie && remainingMinutes != null ? formatRuntime(remainingMinutes) : null,
  }
}

export const formatCountdown = (seconds) => {
  if (seconds == null || seconds < 0) return 'Próximamente'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  if (days) return `${days}d ${hours}h`
  const minutes = Math.floor((seconds % 3600) / 60)
  return `${hours}h ${minutes}m`
}
