import assert from 'node:assert/strict'
import test from 'node:test'
import { pwaManifestUrl } from './pwaBranding.js'

test('builds a versioned manifest URL from valid profile colors', () => {
  assert.equal(pwaManifestUrl('#12ab34', '#050607'), '/api/pwa-manifest?accent=12ab34&background=050607&v=3')
  assert.equal(pwaManifestUrl('invalid', null), '/api/pwa-manifest?accent=9b72ff&background=080a12&v=3')
})
