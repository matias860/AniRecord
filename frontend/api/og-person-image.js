import { aniListImageData, escapeXml, pngResponse, safeText, wrapText } from '../server/imageUtils.js'

export async function GET(request) {
  const params = new URL(request.url).searchParams
  const name = safeText(params.get('name'), 90) || 'AniRecord'
  const kind = safeText(params.get('kind'), 24) || 'Persona'
  const origin = safeText(params.get('origin'), 90)
  const originType = safeText(params.get('originType'), 36)
  const age = safeText(params.get('age'), 4)
  const gender = safeText(params.get('gender'), 24)
  const portrait = await aniListImageData(params.get('image'))
  const lines = wrapText(name, 24, 3)
  const nameSvg = lines.map((line, index) => `<text x="445" y="${270 + index * 67}" class="name">${escapeXml(line)}</text>`).join('')
  const detailsY = 285 + lines.length * 67
  const pillWidth = Math.max(116, kind.length * 13 + 34)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630"><defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#070911"/><stop offset=".55" stop-color="#0c0f1c"/><stop offset="1" stop-color="#201738"/></linearGradient><pattern id="dots" width="24" height="24" patternUnits="userSpaceOnUse"><circle cx="2" cy="2" r="1.2" fill="#c3b8ff" opacity=".24"/></pattern><clipPath id="portrait"><rect x="78" y="140" width="306" height="428" rx="24"/></clipPath><filter id="shadow"><feDropShadow dx="0" dy="18" stdDeviation="18" flood-opacity=".65"/></filter><style>.text{font-family:Arial,sans-serif;fill:#f7f6fc}.name{font-family:Arial,sans-serif;fill:#f7f6fc;font-size:60px;font-weight:800}.muted{font-family:Arial,sans-serif;fill:#b9bbc8}.label{font-family:Arial,sans-serif;fill:#9b9eaf;font-size:13px;font-weight:700;letter-spacing:1.3px}</style></defs>
    <rect width="1200" height="630" fill="url(#bg)"/><rect width="1200" height="630" fill="url(#dots)"/><circle cx="1120" cy="-90" r="310" fill="#9b87ff" opacity=".19"/><rect x="62" y="124" width="338" height="460" rx="31" fill="#17152a" stroke="#c3b8ff" stroke-opacity=".35" filter="url(#shadow)"/>
    ${portrait ? `<image href="${portrait}" x="78" y="140" width="306" height="428" preserveAspectRatio="xMidYMid slice" clip-path="url(#portrait)"/>` : `<rect x="78" y="140" width="306" height="428" rx="24" fill="#201b39"/><text x="231" y="390" text-anchor="middle" class="text" font-size="120" font-weight="800">${escapeXml(name.slice(0, 1).toUpperCase())}</text>`}<rect x="62" y="42" width="46" height="46" rx="14" fill="#8873ef"/><text x="85" y="73" text-anchor="middle" class="text" font-size="19" font-weight="800">AR</text><text x="123" y="73" class="text" font-size="29" font-weight="800">Ani<tspan fill="#c3b8ff">Record</tspan></text>
    <rect x="445" y="178" width="${pillWidth}" height="42" rx="21" fill="#9b87ff" fill-opacity=".16" stroke="#c3b8ff" stroke-opacity=".35"/><text x="462" y="206" class="text" font-size="17" font-weight="800" letter-spacing="1.4">${escapeXml(kind.toUpperCase())}</text>${nameSvg}
    ${origin ? `<rect x="445" y="${detailsY}" width="665" height="98" rx="18" fill="#080a12" fill-opacity=".7" stroke="#fff" stroke-opacity=".12"/><text x="465" y="${detailsY + 28}" class="label">${kind === 'Profesional' ? 'TRAYECTORIA DESTACADA' : 'SERIE DE PROCEDENCIA'}</text><text x="465" y="${detailsY + 62}" class="text" font-size="21" font-weight="700">${escapeXml(safeText(origin, 54))}</text>${originType ? `<text x="465" y="${detailsY + 86}" class="muted" font-size="15">${escapeXml(originType)}</text>` : ''}` : ''}${kind === 'Seiyū' && (age || gender) ? `<text x="445" y="545" class="muted" font-size="18">${escapeXml([age && `${age} años`, gender].filter(Boolean).join(' · '))}</text>` : ''}
  </svg>`
  return pngResponse(svg, 'public, s-maxage=86400, stale-while-revalidate=604800')
}
