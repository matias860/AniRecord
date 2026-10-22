import assert from 'node:assert/strict'
import test from 'node:test'
import { pwaFaviconUrl, pwaManifestUrl } from './pwaBranding.js'

test('builds a versioned manifest URL from valid profile colors', () => {
  assert.equal(pwaManifestUrl('#12ab34', '#050607'), '/api/pwa-manifest?accent=12ab34&background=050607&v=3')
  assert.equal(pwaManifestUrl('invalid', null), '/api/pwa-manifest?accent=9b72ff&background=080a12&v=3')
})

test('builds a rounded favicon URL without changing the installable icon', () => {
  assert.equal(pwaFaviconUrl('#12ab34', '#050607'), '/api/pwa-icon?size=128&accent=12ab34&background=050607&shape=rounded&v=4')
})
