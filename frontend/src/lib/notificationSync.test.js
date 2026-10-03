import test from 'node:test'
import assert from 'node:assert/strict'
import { notificationSyncInterval, notificationSyncIsDue } from './notificationSync.js'

test('notification sync is due initially and after fifteen minutes', () => {
  const now = 2_000_000
  assert.equal(notificationSyncIsDue(null, now), true)
  assert.equal(notificationSyncIsDue(now - notificationSyncInterval + 1, now), false)
  assert.equal(notificationSyncIsDue(now - notificationSyncInterval, now), true)
})
