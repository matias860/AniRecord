import { ImageResponse } from '@vercel/og'
import { createElement as h } from 'react'

export const config = { runtime: 'edge' }

export default function handler(request) {
  const params = new URL(request.url).searchParams
  const name = limit(params.get('name'), 90) || 'Anime List'
  const kind = limit(params.get('kind'), 24) || 'Persona'
  const origin = limit(params.get('origin'), 90)
  const originType = limit(params.get('originType'), 24)
  const image = safeImage(params.get('image'))

  return new ImageResponse(
    h('div', { style: rootStyle },
      h('div', { style: dotStyle }),
      h('div', { style: glowOneStyle }),
      h('div', { style: glowTwoStyle }),
      h('div', { style: contentStyle },
        image
          ? h('img', { src: image, width: 300, height: 430, style: portraitStyle })
          : h('div', { style: portraitFallbackStyle }, name.slice(0, 1).toUpperCase()),
        h('div', { style: copyStyle },
          h('div', { style: brandStyle }, bookIcon(44), h('span', null, 'Anime'), h('span', { style: accentStyle }, 'List')),
          h('div', { style: eyebrowStyle }, kind),
          h('div', { style: nameStyle }, name),
          origin && h('div', { style: originStyle },
            h('span', { style: originLabelStyle }, kind === 'Seiyū' ? 'Conocido por' : 'De'),
            h('span', null, origin),
            originType && h('span', { style: originTypeStyle }, originType),
          ),
        ),
      ),
    ),
    { width: 1200, height: 630, headers: { 'cache-control': 'public, s-maxage=86400, stale-while-revalidate=604800' } },
  )
}

const rootStyle = { width: '100%', height: '100%', display: 'flex', position: 'relative', overflow: 'hidden', color: '#f4f2fb', background: 'linear-gradient(135deg,#080a12 0%,#11101d 52%,#171229 100%)' }
const dotStyle = { width: '100%', height: '100%', position: 'absolute', inset: 0, opacity: .22, backgroundImage: 'radial-gradient(rgba(185,159,255,.8) 1.5px,transparent 1.5px)', backgroundSize: '25px 25px' }
const glowOneStyle = { width: 560, height: 560, display: 'flex', position: 'absolute', left: -180, bottom: -330, borderRadius: 999, background: 'radial-gradient(circle,rgba(116,91,232,.45),rgba(116,91,232,0) 70%)' }
const glowTwoStyle = { width: 500, height: 500, display: 'flex', position: 'absolute', right: -100, top: -300, borderRadius: 999, background: 'radial-gradient(circle,rgba(168,139,255,.34),rgba(168,139,255,0) 70%)' }
const contentStyle = { width: '100%', height: '100%', display: 'flex', alignItems: 'center', gap: 62, padding: '64px 82px' }
const portraitStyle = { width: 300, height: 430, flexShrink: 0, objectFit: 'cover', border: '4px solid rgba(255,255,255,.15)', borderRadius: 28, boxShadow: '0 32px 75px rgba(0,0,0,.62)' }
const portraitFallbackStyle = { width: 300, height: 430, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, border: '3px solid rgba(168,139,255,.35)', borderRadius: 28, color: '#c1adff', background: 'linear-gradient(145deg,#211b39,#0d1019)', fontSize: 120, fontWeight: 800 }
const copyStyle = { minWidth: 0, flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }
const brandStyle = { display: 'flex', alignItems: 'center', marginBottom: 58, fontSize: 27, fontWeight: 800 }
const accentStyle = { color: '#b99fff' }
const eyebrowStyle = { marginBottom: 15, color: '#bca8ff', fontSize: 20, fontWeight: 800, letterSpacing: 3.2, textTransform: 'uppercase' }
const nameStyle = { maxWidth: 680, fontSize: 62, fontWeight: 800, letterSpacing: -2.8, lineHeight: 1.02, textShadow: '0 5px 25px rgba(0,0,0,.75)' }
const originStyle = { maxWidth: 690, display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 10, marginTop: 30, color: '#d5d3df', fontSize: 22 }
const originLabelStyle = { color: '#979aaa' }
const originTypeStyle = { display: 'flex', marginLeft: 5, padding: '6px 12px', border: '1px solid rgba(185,159,255,.28)', borderRadius: 999, color: '#c7b6ff', background: 'rgba(168,139,255,.1)', fontSize: 16, fontWeight: 700 }

function safeImage(value) {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && (url.hostname === 'anilist.co' || url.hostname.endsWith('.anilist.co')) ? url.toString() : null
  } catch { return null }
}

function bookIcon(size) {
  const page = { width: 11, height: 20, border: '2px solid #fff', background: 'rgba(255,255,255,.08)' }
  return h('div', { style: { width: size, height: size, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, marginRight: 13, borderRadius: 13, background: 'linear-gradient(145deg,#a88bff,#6e5be8)', boxShadow: '0 10px 28px rgba(110,91,232,.3)' } },
    h('div', { style: { display: 'flex' } },
      h('div', { style: { ...page, borderRadius: '5px 1px 1px 5px' } }),
      h('div', { style: { ...page, borderRadius: '1px 5px 5px 1px' } }),
    ))
}

function limit(value, size) {
  const text = String(value || '').trim()
  return text.length > size ? `${text.slice(0, size - 1)}…` : text
}
