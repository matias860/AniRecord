import { ImageResponse } from '@vercel/og'
import { createElement as h } from 'react'

export const config = { runtime: 'edge' }

const WIDTH = 1200
const HEIGHT = 630

export default function handler(request) {
  const params = new URL(request.url).searchParams
  const title = limit(params.get('title'), 110) || 'Anime List'
  const type = limit(params.get('type'), 30) || 'Serie'
  const status = limit(params.get('status'), 36) || 'En Anime List'
  const cover = safeImage(params.get('cover'))
  const banner = safeImage(params.get('banner'))
  const backdrop = banner || cover

  return new ImageResponse(
    h('div', { style: rootStyle },
      backdrop && h('img', { src: backdrop, width: WIDTH, height: HEIGHT, style: backdropStyle }),
      h('div', { style: shadeStyle }),
      h('div', { style: glowStyle }),
      h('div', { style: contentStyle },
        cover
          ? h('img', { src: cover, width: 280, height: 406, style: coverStyle })
          : h('div', { style: emptyCoverStyle }, bookIcon(86, false)),
        h('div', { style: copyStyle },
          h('div', { style: brandStyle },
            bookIcon(44, true),
            h('span', null, 'Anime'),
            h('span', { style: brandAccentStyle }, 'List'),
          ),
          h('div', { style: eyebrowStyle }, `${type}  ·  ${status}`),
          h('div', { style: titleStyle }, title),
          h('div', { style: footerStyle }, 'Descubre, organiza y sigue cada historia.'),
        ),
      ),
    ),
    {
      width: WIDTH,
      height: HEIGHT,
      headers: { 'cache-control': 'public, s-maxage=86400, stale-while-revalidate=604800' },
    },
  )
}

const rootStyle = { width: '100%', height: '100%', display: 'flex', position: 'relative', overflow: 'hidden', color: '#f4f2fb', background: 'linear-gradient(135deg,#080a12,#171329)' }
const backdropStyle = { width: '100%', height: '100%', position: 'absolute', inset: 0, objectFit: 'cover', opacity: .52 }
const shadeStyle = { width: '100%', height: '100%', position: 'absolute', inset: 0, background: 'linear-gradient(90deg,rgba(7,8,14,.98) 0%,rgba(8,9,16,.91) 38%,rgba(8,9,16,.55) 72%,rgba(8,9,16,.76) 100%)' }
const glowStyle = { width: 520, height: 520, position: 'absolute', right: -170, top: -240, borderRadius: 999, background: 'radial-gradient(circle,rgba(153,118,255,.48),rgba(153,118,255,0) 68%)' }
const contentStyle = { width: '100%', height: '100%', display: 'flex', alignItems: 'center', gap: 54, padding: '62px 70px' }
const coverStyle = { width: 280, height: 406, flexShrink: 0, objectFit: 'cover', border: '4px solid rgba(255,255,255,.16)', borderRadius: 24, boxShadow: '0 30px 70px rgba(0,0,0,.58)' }
const emptyCoverStyle = { width: 280, height: 406, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, border: '3px solid rgba(168,139,255,.35)', borderRadius: 24, color: '#b9a5ff', background: 'linear-gradient(145deg,#201a37,#0d1019)' }
const copyStyle = { minWidth: 0, flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }
const brandStyle = { display: 'flex', alignItems: 'center', marginBottom: 54, fontSize: 27, fontWeight: 800 }
const brandAccentStyle = { color: '#b99fff' }
const eyebrowStyle = { marginBottom: 16, color: '#bca8ff', fontSize: 20, fontWeight: 800, letterSpacing: 3, textTransform: 'uppercase' }
const titleStyle = { maxWidth: 720, fontSize: 58, fontWeight: 800, letterSpacing: -2.6, lineHeight: 1.03, textShadow: '0 5px 25px rgba(0,0,0,.75)' }
const footerStyle = { marginTop: 32, color: '#b8bbca', fontSize: 21 }

function safeImage(value) {
  if (!value) return null
  try {
    const url = new URL(value)
    const allowedHost = url.hostname === 'anilist.co' || url.hostname.endsWith('.anilist.co')
    return url.protocol === 'https:' && allowedHost ? url.toString() : null
  } catch {
    return null
  }
}

function bookIcon(size, withMargin) {
  const pageHeight = Math.round(size * .45)
  const pageWidth = Math.round(size * .24)
  const border = Math.max(2, Math.round(size * .045))
  const page = { width: pageWidth, height: pageHeight, border: `${border}px solid #fff`, background: 'rgba(255,255,255,.08)' }
  return h('div', {
    style: {
      width: size, height: size, display: 'flex', alignItems: 'center', justifyContent: 'center',
      flexShrink: 0, marginRight: withMargin ? 13 : 0, borderRadius: Math.round(size * .3),
      background: 'linear-gradient(145deg,#a88bff,#6e5be8)', boxShadow: '0 10px 28px rgba(110,91,232,.3)',
    },
  }, h('div', { style: { display: 'flex' } },
    h('div', { style: { ...page, borderRadius: `${border * 2}px 1px 1px ${border * 2}px` } }),
    h('div', { style: { ...page, borderRadius: `1px ${border * 2}px ${border * 2}px 1px` } }),
  ))
}

function limit(value, size) {
  const text = String(value || '').trim()
  return text.length > size ? `${text.slice(0, size - 1)}…` : text
}
