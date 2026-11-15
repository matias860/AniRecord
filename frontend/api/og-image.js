import { aniListImageData, escapeXml, pngResponse, safeText, wrapText } from '../server/imageUtils.js'

export default async function handler(request) {
  const params = new URL(request.url).searchParams
  const title = safeText(params.get('title'), 110) || 'AniRecord'
  const type = safeText(params.get('type'), 30) || 'Anime y manga'
  const status = safeText(params.get('status'), 36) || 'Tu historia, en orden'
  const [cover, banner] = await Promise.all([aniListImageData(params.get('cover')), aniListImageData(params.get('banner'))])
  const titleSvg = wrapText(title, 25, 3).map((line, index) => `<text x="430" y="${285 + index * 66}" class="title">${escapeXml(line)}</text>`).join('')
  const backdrop = banner || cover
  const pillWidth = Math.max(110, type.length * 12 + 32)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">
    <defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#070911"/><stop offset=".52" stop-color="#0d1020"/><stop offset="1" stop-color="#241b44"/></linearGradient><linearGradient id="shade"><stop stop-color="#05070d" stop-opacity=".98"/><stop offset=".58" stop-color="#070911" stop-opacity=".84"/><stop offset="1" stop-color="#07080f" stop-opacity=".7"/></linearGradient><pattern id="dots" width="24" height="24" patternUnits="userSpaceOnUse"><circle cx="2" cy="2" r="1.2" fill="#c3b8ff" opacity=".25"/></pattern><filter id="shadow"><feDropShadow dx="0" dy="18" stdDeviation="18" flood-opacity=".65"/></filter><clipPath id="cover"><rect x="76" y="142" width="286" height="420" rx="22"/></clipPath><style>.text{font-family:Arial,sans-serif;fill:#f7f6fc}.title{font-family:Arial,sans-serif;fill:#f7f6fc;font-size:58px;font-weight:800}.muted{font-family:Arial,sans-serif;fill:#c3c5d1}.pill{font-family:Arial,sans-serif;fill:#e6e1ff;font-size:17px;font-weight:700;letter-spacing:1px}</style></defs>
    <rect width="1200" height="630" fill="url(#bg)"/>${backdrop ? `<image href="${backdrop}" width="1200" height="630" preserveAspectRatio="xMidYMid slice" opacity=".36"/>` : ''}<rect width="1200" height="630" fill="url(#shade)"/><rect width="1200" height="630" fill="url(#dots)"/><circle cx="1110" cy="-80" r="300" fill="#9b87ff" opacity=".18"/>
    <rect x="60" y="126" width="318" height="452" rx="30" fill="#17152a" stroke="#c3b8ff" stroke-opacity=".35" filter="url(#shadow)"/>${cover ? `<image href="${cover}" x="76" y="142" width="286" height="420" preserveAspectRatio="xMidYMid slice" clip-path="url(#cover)"/>` : `<rect x="76" y="142" width="286" height="420" rx="22" fill="#201b39"/><text x="219" y="365" text-anchor="middle" class="text" font-size="74" font-weight="800">AR</text>`}
    <rect x="62" y="42" width="46" height="46" rx="14" fill="#8873ef"/><text x="85" y="73" text-anchor="middle" class="text" font-size="19" font-weight="800">AR</text><text x="123" y="73" class="text" font-size="29" font-weight="800">Ani<tspan fill="#c3b8ff">Record</tspan></text>
    <rect x="430" y="198" width="${pillWidth}" height="42" rx="21" fill="#9b87ff" fill-opacity=".16" stroke="#c3b8ff" stroke-opacity=".35"/><text x="446" y="226" class="pill">${escapeXml(type.toUpperCase())}</text><circle cx="${pillWidth + 448}" cy="219" r="3" fill="#77798a"/><text x="${pillWidth + 466}" y="226" class="muted" font-size="19" font-weight="600">${escapeXml(status)}</text>
    ${titleSvg}<rect x="430" y="520" width="46" height="3" rx="2" fill="#9b87ff"/><text x="492" y="528" class="muted" font-size="19">Descubrí, organizá y seguí cada historia.</text>
  </svg>`
  return pngResponse(svg, 'public, s-maxage=86400, stale-while-revalidate=604800')
}
