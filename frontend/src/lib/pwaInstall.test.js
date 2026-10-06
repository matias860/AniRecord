import assert from 'node:assert/strict'
import test from 'node:test'
import { subscriptionUsesKey, urlBase64ToUint8Array } from './pwaInstall.js'

const publicKey = 'AQIDBA'

test('recognizes a subscription created with the active VAPID key', () => {
  const bytes = urlBase64ToUint8Array(publicKey)
  const applicationServerKey = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)
  assert.equal(subscriptionUsesKey({ options: { applicationServerKey } }, publicKey), true)
})

test('rejects missing or stale VAPID keys', () => {
  assert.equal(subscriptionUsesKey({ options: {} }, publicKey), false)
  assert.equal(subscriptionUsesKey({ options: { applicationServerKey: new Uint8Array([1, 2, 3]).buffer } }, publicKey), false)
})
