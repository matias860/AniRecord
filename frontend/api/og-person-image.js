import { ImageResponse } from '@vercel/og'
import { createElement as h } from 'react'

export const config = { runtime: 'edge' }

const ACCENT = '#9b87ff'
const ACCENT_SOFT = '#c3b8ff'

export default function handler(request) {
  const params = new URL(request.url).searchParams
  const name = limit(params.get('name'), 90) || 'AniRecord'
  const kind = limit(params.get('kind'), 24) || 'Persona'
  const origin = limit(params.get('origin'), 90)
  const originType = limit(params.get('originType'), 36)
  const age = limit(params.get('age'), 4)
  const gender = limit(params.get('gender'), 24)
  const image = safeImage(params.get('image'))

  return new ImageResponse(
    h('div', { style: rootStyle },
      h('div', { style: dotsStyle }), h('div', { style: leftGlowStyle }), h('div', { style: rightGlowStyle }),
      h('div', { style: shellStyle },
        h('div', { style: headerStyle },
          h('div', { style: brandStyle }, bookIcon(46), h('span', null, 'Ani'), h('span', { style: accentStyle }, 'Record')),
          h('div', { style: domainStyle }, h('span', { style: liveDotStyle }), h('span', null, 'anirecord.com.ar')),
        ),
        h('div', { style: mainStyle },
          h('div', { style: portraitFrameStyle },
            image ? h('img', { src: image, width: 306, height: 428, style: portraitStyle }) : h('div', { style: portraitFallbackStyle }, name.slice(0, 1).toUpperCase())),
          h('div', { style: copyStyle },
            h('div', { style: kindPillStyle }, kind),
            h('div', { style: nameStyle }, name),
            origin && h('div', { style: detailsCardStyle },
              h('div', { style: detailLabelStyle }, kind === 'Profesional' ? 'TRAYECTORIA DESTACADA' : 'SERIE DE PROCEDENCIA'),
              h('div', { style: detailValueStyle }, origin),
              originType && h('div', { style: originTypeStyle }, originType),
            ),
            kind === 'Seiyū' && (age || gender) && h('div', { style: factRowStyle },
              age && h('div', { style: factStyle }, h('span', { style: factLabelStyle }, 'EDAD'), h('span', { style: factValueStyle }, `${age} años`)),
              gender && h('div', { style: factStyle }, h('span', { style: factLabelStyle }, 'GÉNERO'), h('span', { style: factValueStyle }, gender)),
            ),
            h('div', { style: footerStyle }, h('div', { style: footerLineStyle }),
              h('span', null, kind === 'Personaje' ? 'Conocé su historia en AniRecord.' : 'Explorá su trayectoria en AniRecord.')),
          ),
        ),
      ),
    ),
    { width: 1200, height: 630, headers: { 'cache-control': 'public, s-maxage=86400, stale-while-revalidate=604800' } },
  )
}

const rootStyle = { width: '100%', height: '100%', display: 'flex', position: 'relative', overflow: 'hidden', color: '#f7f6fc', background: 'linear-gradient(135deg,#070911 0%,#0c0f1c 50%,#15112a 100%)' }
const dotsStyle = { width: '100%', height: '100%', position: 'absolute', inset: 0, opacity: .22, backgroundImage: 'radial-gradient(rgba(195,184,255,.75) 1.25px,transparent 1.25px)', backgroundSize: '24px 24px' }
const leftGlowStyle = { width: 550, height: 550, position: 'absolute', left: -210, bottom: -360, borderRadius: 999, background: 'radial-gradient(circle,rgba(74,205,255,.30),rgba(74,205,255,0) 70%)' }
const rightGlowStyle = { width: 520, height: 520, position: 'absolute', right: -140, top: -310, borderRadius: 999, background: 'radial-gradient(circle,rgba(155,135,255,.48),rgba(155,135,255,0) 70%)' }
const shellStyle = { width: '100%', height: '100%', display: 'flex', flexDirection: 'column', padding: '42px 62px 48px' }
const headerStyle = { width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }
const brandStyle = { display: 'flex', alignItems: 'center', fontSize: 28, fontWeight: 800, letterSpacing: '-.8px' }
const accentStyle = { color: ACCENT_SOFT }
const domainStyle = { display: 'flex', alignItems: 'center', gap: 10, padding: '10px 16px', border: '1px solid rgba(255,255,255,.12)', borderRadius: 999, color: '#c9cad5', background: 'rgba(8,10,18,.62)', fontSize: 16 }
const liveDotStyle = { width: 8, height: 8, borderRadius: 99, background: ACCENT, boxShadow: '0 0 15px rgba(155,135,255,.9)' }
const mainStyle = { minHeight: 0, flex: 1, display: 'flex', alignItems: 'center', gap: 64, paddingTop: 28 }
const portraitFrameStyle = { width: 322, height: 444, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, padding: 8, border: '1px solid rgba(195,184,255,.32)', borderRadius: 30, background: 'linear-gradient(145deg,rgba(195,184,255,.17),rgba(74,205,255,.06))', boxShadow: '0 30px 74px rgba(0,0,0,.62),0 0 34px rgba(155,135,255,.16)' }
const portraitStyle = { width: 306, height: 428, objectFit: 'cover', borderRadius: 23 }
const portraitFallbackStyle = { width: 306, height: 428, display: 'flex', alignItems: 'center', justifyContent: 'center', color: ACCENT_SOFT, borderRadius: 23, background: 'linear-gradient(145deg,#201b39,#0b0e18)', fontSize: 120, fontWeight: 800 }
const copyStyle = { minWidth: 0, flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }
const kindPillStyle = { alignSelf: 'flex-start', display: 'flex', marginBottom: 20, padding: '9px 15px', border: '1px solid rgba(195,184,255,.30)', borderRadius: 999, color: '#e6e1ff', background: 'rgba(155,135,255,.14)', fontSize: 17, fontWeight: 800, letterSpacing: 1.6, textTransform: 'uppercase' }
const nameStyle = { maxWidth: 690, fontSize: 61, fontWeight: 800, letterSpacing: -2.8, lineHeight: 1.01, textShadow: '0 6px 26px rgba(0,0,0,.72)' }
const detailsCardStyle = { maxWidth: 690, display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 10, marginTop: 28, padding: '16px 18px', border: '1px solid rgba(255,255,255,.11)', borderRadius: 17, background: 'rgba(8,10,18,.55)' }
const detailLabelStyle = { width: '100%', color: '#8e91a2', fontSize: 12, fontWeight: 800, letterSpacing: 1.6 }
const detailValueStyle = { maxWidth: 520, color: '#eceaf4', fontSize: 21, fontWeight: 700 }
const originTypeStyle = { display: 'flex', padding: '6px 11px', border: '1px solid rgba(195,184,255,.26)', borderRadius: 999, color: ACCENT_SOFT, background: 'rgba(155,135,255,.11)', fontSize: 14, fontWeight: 700 }
const factRowStyle = { display: 'flex', gap: 14, marginTop: 28 }
const factStyle = { display: 'flex', flexDirection: 'column', minWidth: 150, padding: '14px 17px', border: '1px solid rgba(255,255,255,.11)', borderRadius: 16, background: 'rgba(8,10,18,.55)' }
const factLabelStyle = { color: '#8e91a2', fontSize: 11, fontWeight: 800, letterSpacing: 1.5 }
const factValueStyle = { marginTop: 5, color: '#eceaf4', fontSize: 19, fontWeight: 700 }
const footerStyle = { display: 'flex', alignItems: 'center', gap: 16, marginTop: 28, color: '#b9bbc8', fontSize: 18 }
const footerLineStyle = { width: 46, height: 3, borderRadius: 99, background: 'linear-gradient(90deg,#9b87ff,#4acdff)' }

function safeImage(value) {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && (url.hostname === 'anilist.co' || url.hostname.endsWith('.anilist.co')) ? url.toString() : null
  } catch { return null }
}

function bookIcon(size) {
  const page = { width: 11, height: 20, border: '2px solid #fff', background: 'rgba(255,255,255,.06)' }
  return h('div', { style: { width: size, height: size, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, marginRight: 13, border: '1px solid rgba(255,255,255,.16)', borderRadius: 14, background: 'linear-gradient(145deg,#a894ff,#7560e9)', boxShadow: '0 10px 30px rgba(117,96,233,.36)' } },
    h('div', { style: { display: 'flex' } },
      h('div', { style: { ...page, borderRadius: '5px 1px 1px 5px' } }),
      h('div', { style: { ...page, borderRadius: '1px 5px 5px 1px' } }),
    ))
}

function limit(value, size) {
  const text = String(value || '').trim()
  return text.length > size ? `${text.slice(0, size - 1)}…` : text
}
