import { writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import sharp from 'sharp'

const output = fileURLToPath(new URL('../public/og-default.png', import.meta.url))
const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#05070d"/><stop offset=".55" stop-color="#0b1020"/><stop offset="1" stop-color="#241b44"/></linearGradient>
    <radialGradient id="glow"><stop stop-color="#9b87ff" stop-opacity=".55"/><stop offset="1" stop-color="#9b87ff" stop-opacity="0"/></radialGradient>
    <pattern id="dots" width="28" height="28" patternUnits="userSpaceOnUse"><circle cx="2" cy="2" r="1.4" fill="#c3b8ff" opacity=".24"/></pattern>
  </defs>
  <rect width="1200" height="630" fill="url(#bg)"/><rect width="1200" height="630" fill="url(#dots)"/><circle cx="1060" cy="30" r="380" fill="url(#glow)"/>
  <rect x="86" y="92" width="112" height="112" rx="31" fill="#8873ef" stroke="#c3b8ff" stroke-opacity=".7"/><text x="142" y="164" text-anchor="middle" font-family="Arial,sans-serif" font-size="46" font-weight="800" fill="#fff">AR</text>
  <text x="228" y="166" font-family="Arial,sans-serif" font-size="58" font-weight="800" fill="#fff">Ani<tspan fill="#bcaeff">Record</tspan></text>
  <text x="86" y="335" font-family="Arial,sans-serif" font-size="76" font-weight="800" fill="#f7f6fc">Tu historia, en orden.</text>
  <text x="90" y="405" font-family="Arial,sans-serif" font-size="30" fill="#c3c5d1">Descubrí, organizá y seguí cada anime y manga.</text>
  <rect x="90" y="478" width="72" height="5" rx="3" fill="#9b87ff"/><text x="184" y="491" font-family="Arial,sans-serif" font-size="25" fill="#d6d7e0">anirecord.com.ar</text>
</svg>`

await writeFile(output, await sharp(Buffer.from(svg)).png({ compressionLevel: 9 }).toBuffer())
console.log(`Generated ${output}`)
