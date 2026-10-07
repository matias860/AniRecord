import test from 'node:test'
import assert from 'node:assert/strict'
import { safeDarkColor, safeAccentColor, luminance, accentTextColor } from './themeContrast.js'
test('bright backgrounds keep at least 4.5 contrast with muted text', () => {
  for (const color of ['#ffffff', '#ffff00', '#00ff00', '#ff00ff', '#ff0000']) {
    const adjusted = safeDarkColor(color)
    assert.ok(luminance(adjusted) <= .025)
    assert.ok((luminance('#9299ad') + .05) / (luminance(adjusted) + .05) >= 4.5)
    assert.equal(safeDarkColor(adjusted), adjusted)
  }
})
test('dark profile colors stay unchanged and button labels contrast with accent', () => {
  assert.equal(safeDarkColor('#0a0c14'), '#0a0c14')
  assert.equal(accentTextColor('#00ff00'), '#080a10')
  assert.equal(accentTextColor('#000000'), '#ffffff')
})

test('dark accents are readable over the darkest allowed surface', () => {
  for (const color of ['#000000', '#0000ff', '#ff0000', '#00ff00']) {
    const adjusted = safeAccentColor(color)
    assert.ok((luminance(adjusted) + .05) / (.025 + .05) >= 4.5)
    assert.equal(safeAccentColor(adjusted), adjusted)
  }
  assert.equal(safeAccentColor('#00ff00'), '#00ff00')
})
