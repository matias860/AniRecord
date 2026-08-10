export const STATUS_LABELS = {
  PLANNED: 'Pendiente', IN_PROGRESS: 'En curso', PAUSED: 'Pausado',
  COMPLETED: 'Completado', DROPPED: 'Abandonado',
}

export const cleanAniListText = (value) => (value ?? '')
  .replace(/<br\s*\/?\s*>/gi, '\n')
  .replace(/<[^>]*>/g, '')
  .replace(/~!([\s\S]*?)!~/g, '$1')
  .replace(/\[([^\]]+)]\([^\s)]+\)/g, '$1')
  .replace(/(__|\*\*)(.*?)\1/g, '$2')
  .replace(/^\s*[-*]\s+/gm, '• ')
  .replace(/[ \t]+\n/g, '\n')
  .replace(/\n{3,}/g, '\n\n')
  .trim()

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
