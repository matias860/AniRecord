export function luminance(hex) {
  const values = hex.slice(1).match(/../g).map((part) => parseInt(part, 16) / 255)
  const linear = values.map((value) => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4)
  return linear[0] * .2126 + linear[1] * .7152 + linear[2] * .0722
}
export function safeDarkColor(hex) {
  if (!/^#[0-9a-f]{6}$/i.test(hex || '')) return '#171b29'
  let channels = hex.slice(1).match(/../g).map((part) => parseInt(part, 16))
  let color = hex
  while (luminance(color) > .025) {
    channels = channels.map((value) => Math.floor(value * .95))
    color = '#' + channels.map((value) => value.toString(16).padStart(2, '0')).join('')
  }
  return color
}
export function accentTextColor(hex) {
  return /^#[0-9a-f]{6}$/i.test(hex || '') && luminance(hex) > .179 ? '#080a10' : '#ffffff'
}

export function safeAccentColor(hex) {
  if (!/^#[0-9a-f]{6}$/i.test(hex || '')) return '#a88bff'
  let channels = hex.slice(1).match(/../g).map((part) => parseInt(part, 16))
  let color = hex
  while (luminance(color) < .3) {
    channels = channels.map((value) => Math.min(255, Math.ceil(value + (255 - value) * .05)))
    color = '#' + channels.map((value) => value.toString(16).padStart(2, '0')).join('')
  }
  return color
}
