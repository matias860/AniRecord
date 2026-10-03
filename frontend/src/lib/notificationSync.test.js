import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeNotificationSyncResponse, notificationSyncInterval, notificationSyncIsDue } from './notificationSync.js'

test('notification sync is due initially and after fifteen minutes', () => {
  const now = 2_000_000
  assert.equal(notificationSyncIsDue(null, now), true)
  assert.equal(notificationSyncIsDue(now - notificationSyncInterval + 1, now), false)
  assert.equal(notificationSyncIsDue(now - notificationSyncInterval, now), true)
})

test('failed notification refresh keeps its items without reporting success', () => {
  const result = normalizeNotificationSyncResponse({
    added: 0,
    successful: false,
    notifications: [{ id: 1 }],
  }, 123)

  assert.deepEqual(result, { items: [{ id: 1 }], successful: false, syncedAt: 123 })
})

test('legacy notification refresh responses remain compatible during deployment', () => {
  assert.deepEqual(normalizeNotificationSyncResponse([{ id: 1 }], 123), {
    items: [{ id: 1 }], successful: true, syncedAt: 123,
  })
})
