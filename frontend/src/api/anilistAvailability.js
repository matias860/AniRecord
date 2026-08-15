const STORAGE_KEY = 'anime-list:anilist-unavailable-until'
const RETRY_AFTER_MS = 2 * 60 * 1000

export function isAniListTemporarilyUnavailable() {
  const unavailableUntil = Number(window.localStorage.getItem(STORAGE_KEY) || 0)
  if (unavailableUntil <= Date.now()) {
    window.localStorage.removeItem(STORAGE_KEY)
    return false
  }
  return true
}

export function rememberAniListOutage() {
  window.localStorage.setItem(STORAGE_KEY, String(Date.now() + RETRY_AFTER_MS))
}

export function clearAniListOutage() {
  window.localStorage.removeItem(STORAGE_KEY)
}

export function cachedAniListError() {
  return { message: 'AniList no está disponible temporalmente', unavailable: true }
}
