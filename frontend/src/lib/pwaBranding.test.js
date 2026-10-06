import assert from 'node:assert/strict'
import test from 'node:test'
import { blendHexColors } from './pwaBranding.js'

test('blends the profile accent into the Android system bar color', () => {
  assert.equal(blendHexColors('#ffffff', '#000000'), '#1f1f1f')
  assert.equal(blendHexColors('#ffffff', '#000000', 0), '#000000')
  assert.equal(blendHexColors('#ffffff', '#000000', 1), '#ffffff')
})
