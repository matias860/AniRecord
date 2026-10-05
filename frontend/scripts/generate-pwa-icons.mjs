import sharp from 'sharp'

const source = 'public/anirecord-logo.svg'
await Promise.all([
  [192, 'pwa-192x192.png'],
  [512, 'pwa-512x512.png'],
  [180, 'apple-touch-icon.png'],
  [96, 'pwa-badge-96x96.png'],
].map(([size, name]) => sharp(source, { density: 384 }).resize(size, size).png().toFile(`public/${name}`)))

const inner = await sharp(source, { density: 384 }).resize(360, 360).png().toBuffer()
const background = Buffer.from(`<svg width="512" height="512" xmlns="http://www.w3.org/2000/svg">
  <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#9b72ff"/><stop offset="1" stop-color="#080a12"/></linearGradient></defs>
  <rect width="512" height="512" fill="url(#g)"/>
</svg>`)
await sharp(background).composite([{ input: inner, left: 76, top: 76 }]).png().toFile('public/pwa-maskable-512x512.png')
