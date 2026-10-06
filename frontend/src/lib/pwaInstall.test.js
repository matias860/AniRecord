import assert from 'node:assert/strict'
import test from 'node:test'
import { isAppInstalled, subscriptionUsesKey, urlBase64ToUint8Array } from './pwaInstall.js'

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

test('detects an installed related web app outside standalone mode', async () => {
  const originalWindow = Object.getOwnPropertyDescriptor(globalThis, 'window')
  const originalNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: { matchMedia: () => ({ matches: false }), navigator: {} },
  })
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { getInstalledRelatedApps: async () => [{ platform: 'webapp', id: '/' }] },
  })
  try {
    assert.equal(await isAppInstalled(), true)
  } finally {
    if (originalWindow) Object.defineProperty(globalThis, 'window', originalWindow)
    else delete globalThis.window
    if (originalNavigator) Object.defineProperty(globalThis, 'navigator', originalNavigator)
    else delete globalThis.navigator
  }
})

test('reports an uninstalled app when no related web app is present', async () => {
  const originalWindow = Object.getOwnPropertyDescriptor(globalThis, 'window')
  const originalNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: { matchMedia: () => ({ matches: false }), navigator: {} },
  })
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { getInstalledRelatedApps: async () => [] },
  })
  try {
    assert.equal(await isAppInstalled(), false)
  } finally {
    if (originalWindow) Object.defineProperty(globalThis, 'window', originalWindow)
    else delete globalThis.window
    if (originalNavigator) Object.defineProperty(globalThis, 'navigator', originalNavigator)
    else delete globalThis.navigator
  }
})
