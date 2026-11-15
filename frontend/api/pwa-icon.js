import { pngResponse } from '../server/imageUtils.js'

const A = 'M511.636 1320H854.029L1387.847 0H1016.788L927.333 245.122H440.09L349.514 0H-22.545ZM556.393 548.151H811.152L685.212 902.183H682.212Z'
const R = 'M64.182 1320H632.424Q750 1320 841.212 1270.591Q932.424 1221.182 984.484 1129.136Q1036.545 1037.091 1036.545 910.061Q1036.545 783.909 993.772 705.106Q951 626.303 891.181 587.061Q831.363 547.818 780.666 538.152L1098.727 0H708.364L430.182 511.303H412.666V0H64.182ZM412.666 748.818H506.97Q571.728 748.818 625.379 780Q679.031 811.182 679.031 898.637Q679.031 986.091 626.698 1016.91Q574.364 1047.728 509.606 1047.728H412.666Z'
const color = (value, fallback) => /^[0-9a-f]{6}$/i.test(value || '') ? `#${value}` : fallback

export default function handler(request) {
  const params = new URL(request.url).searchParams
  const size = [96, 128, 180, 192, 512].includes(Number(params.get('size'))) ? Number(params.get('size')) : 512
  const accent = color(params.get('accent'), '#9b72ff')
  const background = color(params.get('background'), '#080a12')
  const radius = params.get('shape') === 'rounded' ? 112 : 0
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 512 512"><defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="${accent}"/><stop offset=".88" stop-color="${background}"/></linearGradient><clipPath id="shape"><rect width="512" height="512" rx="${radius}"/></clipPath></defs><g clip-path="url(#shape)"><rect width="512" height="512" fill="url(#bg)"/><g fill="#fff"><path d="${A}" transform="translate(61 370) scale(.153664 -.153664)"/><path d="${R}" transform="translate(264.913 370) scale(.153664 -.153664)"/></g></g></svg>`
  return pngResponse(svg, 'public, max-age=604800, immutable')
}
