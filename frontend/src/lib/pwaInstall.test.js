import assert from 'node:assert/strict'
import test from 'node:test'
import { activePushSubscription, isAppInstalled, isIos, shouldDismissPwaUpdate, subscriptionUsesKey, urlBase64ToUint8Array } from './pwaInstall.js'

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

test('dismisses the update tray only after an intentional downward swipe', () => {
  assert.equal(shouldDismissPwaUpdate(73, 0), true)
  assert.equal(shouldDismissPwaUpdate(20, 651), true)
  assert.equal(shouldDismissPwaUpdate(-90, -900), false)
  assert.equal(shouldDismissPwaUpdate(40, 300), false)
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

test('recognizes iPadOS when Safari requests the desktop user agent', () => {
  const originalNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X)', platform: 'MacIntel', maxTouchPoints: 5 },
  })
  try {
    assert.equal(isIos(), true)
  } finally {
    if (originalNavigator) Object.defineProperty(globalThis, 'navigator', originalNavigator)
    else delete globalThis.navigator
  }
})

test('reads the active browser push endpoint for a private logout', async () => {
  const subscription = { endpoint: 'https://push.example/device' }
  const originalNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { serviceWorker: { getRegistration: async () => ({ pushManager: { getSubscription: async () => subscription } }) } },
  })
  try {
    assert.equal(await activePushSubscription(), subscription)
  } finally {
    if (originalNavigator) Object.defineProperty(globalThis, 'navigator', originalNavigator)
    else delete globalThis.navigator
  }
})
