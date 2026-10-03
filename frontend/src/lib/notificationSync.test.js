import test from 'node:test'
import assert from 'node:assert/strict'
import {
  normalizeNotificationSyncResponse,
  notificationSyncCanAttempt,
  notificationSyncInterval,
  notificationSyncIsDue,
  notificationSyncRetryInterval,
} from './notificationSync.js'

test('notification sync is due initially and after fifteen minutes', () => {
  const now = 2_000_000
  assert.equal(notificationSyncIsDue(null, now), true)
  assert.equal(notificationSyncIsDue(now - notificationSyncInterval + 1, now), false)
  assert.equal(notificationSyncIsDue(now - notificationSyncInterval, now), true)
})

test('failed attempts use a short backoff without changing the success cadence', () => {
  const now = 2_000_000
  assert.equal(notificationSyncCanAttempt(null, now - notificationSyncRetryInterval + 1, now), false)
  assert.equal(notificationSyncCanAttempt(null, now - notificationSyncRetryInterval, now), true)
  assert.equal(notificationSyncCanAttempt(now - notificationSyncInterval + 1, null, now), false)
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
  assert.deepEqual(normalizeNotificationSyncResponse({ added: 0 }, 123), {
    items: null, successful: true, syncedAt: 123,
  })
})
