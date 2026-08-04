export const STATUS_LABELS = {
  PLANNED: 'Pendiente', IN_PROGRESS: 'En curso', PAUSED: 'Pausado',
  COMPLETED: 'Completado', DROPPED: 'Abandonado',
}

export const cleanSynopsis = (value = '') =>
  value.replace(/<br\s*\/?\s*>/gi, ' ').replace(/<[^>]*>/g, '').trim()

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
