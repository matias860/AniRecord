const STORAGE_KEY = 'anirecord:content-preferences:v1'

export const DEFAULT_CONTENT_PREFERENCES = Object.freeze({
  adultContentMode: 'HIDE',
  titleLanguage: 'AUTO',
})

export function loadContentPreferences() {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
    return {
      adultContentMode: ['HIDE', 'BLUR', 'SHOW'].includes(stored.adultContentMode) ? stored.adultContentMode : 'HIDE',
      titleLanguage: ['AUTO', 'ENGLISH', 'ROMAJI', 'NATIVE'].includes(stored.titleLanguage) ? stored.titleLanguage : 'AUTO',
    }
  } catch {
    return { ...DEFAULT_CONTENT_PREFERENCES }
  }
}

export function storeContentPreferences(preferences) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      adultContentMode: preferences.adultContentMode,
      titleLanguage: preferences.titleLanguage,
    }))
  } catch { /* La navegación sigue funcionando aunque el almacenamiento esté bloqueado. */ }
}

export function preferredTitle(media, language = 'AUTO') {
  if (!media) return ''
  const variants = {
    AUTO: [media.title, media.englishTitle, media.romajiTitle, media.nativeTitle],
    ENGLISH: [media.englishTitle, media.title, media.romajiTitle, media.nativeTitle],
    ROMAJI: [media.romajiTitle, media.title, media.englishTitle, media.nativeTitle],
    NATIVE: [media.nativeTitle, media.title, media.romajiTitle, media.englishTitle],
  }
  return (variants[language] || variants.AUTO).find((title) => typeof title === 'string' && title.trim()) || 'Sin título'
}

export function adultPresentation(media, mode = 'HIDE') {
  if (!media?.adultContent) return 'show'
  if (mode === 'SHOW') return 'show'
  if (mode === 'BLUR' || media.trackingStatus) return 'blur'
  return 'hide'
}
