import { ImageResponse } from '@vercel/og'
import { createElement as h } from 'react'

export const config = { runtime: 'edge' }

const WIDTH = 1200
const HEIGHT = 630
const ACCENT = '#9b87ff'
const ACCENT_SOFT = '#c3b8ff'

export default function handler(request) {
  const params = new URL(request.url).searchParams
  const title = limit(params.get('title'), 110) || 'AniRecord'
  const type = limit(params.get('type'), 30) || 'Anime y manga'
  const status = limit(params.get('status'), 36) || 'Tu historia, en orden'
  const cover = safeImage(params.get('cover'))
  const banner = safeImage(params.get('banner'))
  const backdrop = banner || cover

  return new ImageResponse(
    h('div', { style: rootStyle },
      backdrop && h('img', { src: backdrop, width: WIDTH, height: HEIGHT, style: backdropStyle }),
      h('div', { style: shadeStyle }),
      h('div', { style: dotsStyle }),
      h('div', { style: topGlowStyle }),
      h('div', { style: bottomGlowStyle }),
      h('div', { style: shellStyle },
        h('div', { style: headerStyle },
          h('div', { style: brandStyle }, bookIcon(46), h('span', null, 'Ani'), h('span', { style: brandAccentStyle }, 'Record')),
          h('div', { style: domainStyle }, h('span', { style: liveDotStyle }), h('span', null, 'anirecord.com.ar')),
        ),
        h('div', { style: mainStyle },
          h('div', { style: coverFrameStyle },
            cover ? h('img', { src: cover, width: 286, height: 420, style: coverStyle }) : h('div', { style: emptyCoverStyle }, bookIcon(96, true))),
          h('div', { style: copyStyle },
            h('div', { style: labelsStyle },
              h('span', { style: typePillStyle }, type), h('span', { style: dividerDotStyle }), h('span', { style: statusStyle }, status)),
            h('div', { style: titleStyle }, title),
            h('div', { style: footerStyle }, h('div', { style: footerLineStyle }), h('span', null, 'Descubrí, organizá y seguí cada historia.')),
          ),
        ),
      ),
    ),
    { width: WIDTH, height: HEIGHT, headers: { 'cache-control': 'public, s-maxage=86400, stale-while-revalidate=604800' } },
  )
}

const rootStyle = { width: '100%', height: '100%', display: 'flex', position: 'relative', overflow: 'hidden', color: '#f7f6fc', background: 'linear-gradient(135deg,#070911 0%,#0d1020 48%,#141129 100%)' }
const backdropStyle = { width: '100%', height: '100%', position: 'absolute', inset: 0, objectFit: 'cover', opacity: .46 }
const shadeStyle = { width: '100%', height: '100%', position: 'absolute', inset: 0, background: 'linear-gradient(90deg,rgba(5,7,13,.98) 0%,rgba(7,9,17,.94) 40%,rgba(8,10,18,.68) 72%,rgba(7,8,15,.85) 100%)' }
const dotsStyle = { width: '100%', height: '100%', position: 'absolute', inset: 0, opacity: .18, backgroundImage: 'radial-gradient(rgba(195,184,255,.75) 1.2px,transparent 1.2px)', backgroundSize: '24px 24px' }
const topGlowStyle = { width: 540, height: 540, position: 'absolute', right: -160, top: -320, borderRadius: 999, background: 'radial-gradient(circle,rgba(155,135,255,.55),rgba(155,135,255,0) 69%)' }
const bottomGlowStyle = { width: 460, height: 350, position: 'absolute', left: 20, bottom: -280, borderRadius: 999, background: 'radial-gradient(circle,rgba(74,205,255,.28),rgba(74,205,255,0) 70%)' }
const shellStyle = { width: '100%', height: '100%', display: 'flex', flexDirection: 'column', padding: '42px 62px 48px' }
const headerStyle = { width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }
const brandStyle = { display: 'flex', alignItems: 'center', fontSize: 28, fontWeight: 800, letterSpacing: '-.8px' }
const brandAccentStyle = { color: ACCENT_SOFT }
const domainStyle = { display: 'flex', alignItems: 'center', gap: 10, padding: '10px 16px', border: '1px solid rgba(255,255,255,.12)', borderRadius: 999, color: '#c9cad5', background: 'rgba(8,10,18,.62)', fontSize: 16 }
const liveDotStyle = { width: 8, height: 8, borderRadius: 99, background: ACCENT, boxShadow: '0 0 15px rgba(155,135,255,.9)' }
const mainStyle = { minHeight: 0, flex: 1, display: 'flex', alignItems: 'center', gap: 62, paddingTop: 28 }
const coverFrameStyle = { width: 302, height: 436, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, padding: 8, border: '1px solid rgba(195,184,255,.32)', borderRadius: 28, background: 'linear-gradient(145deg,rgba(195,184,255,.17),rgba(74,205,255,.06))', boxShadow: '0 28px 72px rgba(0,0,0,.62),0 0 34px rgba(155,135,255,.16)' }
const coverStyle = { width: 286, height: 420, objectFit: 'cover', borderRadius: 21 }
const emptyCoverStyle = { width: 286, height: 420, display: 'flex', alignItems: 'center', justifyContent: 'center', color: ACCENT_SOFT, borderRadius: 21, background: 'linear-gradient(145deg,#201b39,#0b0e18)' }
const copyStyle = { minWidth: 0, flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }
const labelsStyle = { display: 'flex', alignItems: 'center', gap: 14, marginBottom: 22 }
const typePillStyle = { display: 'flex', padding: '9px 15px', border: '1px solid rgba(195,184,255,.30)', borderRadius: 999, color: '#e6e1ff', background: 'rgba(155,135,255,.14)', fontSize: 17, fontWeight: 800, letterSpacing: 1.4, textTransform: 'uppercase' }
const dividerDotStyle = { width: 5, height: 5, borderRadius: 99, background: '#77798a' }
const statusStyle = { color: '#c3c5d1', fontSize: 19, fontWeight: 600 }
const titleStyle = { maxWidth: 700, fontSize: 59, fontWeight: 800, letterSpacing: -2.8, lineHeight: 1.01, textShadow: '0 7px 28px rgba(0,0,0,.75)' }
const footerStyle = { display: 'flex', alignItems: 'center', gap: 16, marginTop: 34, color: '#b9bbc8', fontSize: 19 }
const footerLineStyle = { width: 46, height: 3, borderRadius: 99, background: 'linear-gradient(90deg,#9b87ff,#4acdff)' }

function safeImage(value) {
  if (!value) return null
  try {
    const url = new URL(value)
    const allowedHost = url.hostname === 'anilist.co' || url.hostname.endsWith('.anilist.co')
    return url.protocol === 'https:' && allowedHost ? url.toString() : null
  } catch { return null }
}

function bookIcon(size, centered = false) {
  const pageHeight = Math.round(size * .44)
  const pageWidth = Math.round(size * .23)
  const border = Math.max(2, Math.round(size * .042))
  const page = { width: pageWidth, height: pageHeight, border: `${border}px solid #fff`, background: 'rgba(255,255,255,.06)' }
  return h('div', { style: { width: size, height: size, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, marginRight: centered ? 0 : 13, border: '1px solid rgba(255,255,255,.16)', borderRadius: Math.round(size * .3), background: 'linear-gradient(145deg,#a894ff,#7560e9)', boxShadow: '0 10px 30px rgba(117,96,233,.36)' } },
    h('div', { style: { display: 'flex' } },
      h('div', { style: { ...page, borderRadius: `${border * 2}px 1px 1px ${border * 2}px` } }),
      h('div', { style: { ...page, borderRadius: `1px ${border * 2}px ${border * 2}px 1px` } }),
    ))
}

function limit(value, size) {
  const text = String(value || '').trim()
  return text.length > size ? `${text.slice(0, size - 1)}…` : text
}
