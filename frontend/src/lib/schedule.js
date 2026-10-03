export function isUpcomingPremiere(entry, now = new Date()) {
  const nextEpisode = entry.airing?.nextAiringEpisode
  if (nextEpisode?.episode > 1) return false
  if (nextEpisode?.episode === 1) return nextEpisode.airingAt * 1000 > now.getTime()

  if (entry.releaseStartDate) {
    const releaseDate = new Date(`${entry.releaseStartDate}T12:00:00`)
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 12)
    if (releaseDate.getTime() !== today.getTime()) return releaseDate > today
  }
  return entry.releaseStatus === 'NOT_YET_RELEASED'
}

export function hasPendingEpisode(entry, now = new Date()) {
  const nextEpisode = entry.airing?.nextAiringEpisode
  return Boolean(nextEpisode)
    && !isUpcomingPremiere(entry, now)
    && nextEpisode.episode - 1 > (entry.progress ?? entry.airing?.currentProgress ?? 0)
}
