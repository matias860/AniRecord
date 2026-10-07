// AniList's large variant is 230px; extraLarge uses the same asset in /large/.
// Only transform the verified AniList media-cover CDN path.
export function coverSources(source) {
  if (!source) return undefined
  try {
    const url = new URL(source)
    if (url.protocol !== 'https:' || !/^s\d+\.anilist\.co$/.test(url.hostname)) return undefined
    if (!/^\/file\/anilistcdn\/media\/(anime|manga)\/cover\/(medium|large)\//.test(url.pathname)) return undefined
    const medium = new URL(url)
    const large = new URL(url)
    medium.pathname = url.pathname.replace(/\/cover\/(medium|large)\//, '/cover/medium/')
    large.pathname = url.pathname.replace(/\/cover\/(medium|large)\//, '/cover/large/')
    return `${medium.href} 230w, ${large.href} 460w`
  } catch {
    return undefined
  }
}

export function highResolutionCover(source) {
  return coverSources(source) ? source.replace('/cover/medium/', '/cover/large/') : source
}
