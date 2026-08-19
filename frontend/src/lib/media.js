export const STATUS_LABELS = {
  PLANNED: 'Pendiente', IN_PROGRESS: 'En curso', PAUSED: 'Pausado',
  COMPLETED: 'Completado', DROPPED: 'Abandonado',
}

const normalizeAniListText = (value) => (value ?? '')
  .replace(/<br\s*\/?\s*>/gi, '\n')
  .replace(/<[^>]*>/g, '')
  .replace(/~!([\s\S]*?)!~/g, '$1')
  .replace(/(__|\*\*)(.*?)\1/g, '$2')
  .replace(/^\s*[-*]\s+/gm, '• ')
  .replace(/[ \t]+\n/g, '\n')
  .replace(/\n{3,}/g, '\n\n')
  .trim()

export const cleanAniListText = (value) => normalizeAniListText(value)
  .replace(/\[([^\]]+)]\([^\s)]+\)/g, '$1')

export const parseAniListText = (value) => {
  const text = normalizeAniListText(value)
  const linkPattern = /\[([^\]]+)]\((https?:\/\/[^\s)]+)\)/gi
  const segments = []
  let cursor = 0
  for (const match of text.matchAll(linkPattern)) {
    if (match.index > cursor) segments.push({ text: text.slice(cursor, match.index) })
    const href = match[2]
    const to = aniListInternalPath(href)
    segments.push(to ? { text: match[1], to } : { text: match[1], href })
    cursor = match.index + match[0].length
  }
  if (cursor < text.length) segments.push({ text: text.slice(cursor) })
  return segments
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

export const formatCountdown = (seconds) => {
  if (seconds == null || seconds < 0) return 'Próximamente'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  if (days) return `${days}d ${hours}h`
  const minutes = Math.floor((seconds % 3600) / 60)
  return `${hours}h ${minutes}m`
}
