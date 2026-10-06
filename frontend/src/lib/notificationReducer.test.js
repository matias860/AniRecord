import test from 'node:test'
import assert from 'node:assert/strict'
import notificationReducer, {
  fetchNotifications,
  fetchUnreadCount,
  markNotificationsRead,
  syncNotifications,
} from '../features/notifications/notificationSlice.js'

const existingNotification = { id: 1, title: 'Existente', read: false }

test('legacy sync success preserves notifications loaded by GET', () => {
  const state = {
    items: [existingNotification],
    status: 'failed',
    syncStatus: 'loading',
    lastSyncedAt: null,
    lastSyncAttemptAt: null,
    error: 'Error anterior',
  }

  const next = notificationReducer(state, {
    type: syncNotifications.fulfilled.type,
    payload: { items: null, successful: true, syncedAt: 123, attemptedAt: 123 },
  })

  assert.deepEqual(next.items, [existingNotification])
  assert.equal(next.status, 'succeeded')
  assert.equal(next.syncStatus, 'succeeded')
  assert.equal(next.error, null)
  assert.equal(next.lastSyncedAt, 123)
  assert.equal(next.lastSyncAttemptAt, 123)
})

test('valid local history clears a stale fetch error when remote sync fails', () => {
  const state = {
    items: [],
    status: 'failed',
    syncStatus: 'loading',
    lastSyncedAt: null,
    lastSyncAttemptAt: null,
    error: 'Error anterior',
  }

  const next = notificationReducer(state, {
    type: syncNotifications.fulfilled.type,
    payload: {
      items: [existingNotification],
      successful: false,
      syncedAt: 123,
      attemptedAt: 123,
    },
  })

  assert.deepEqual(next.items, [existingNotification])
  assert.equal(next.status, 'succeeded')
  assert.equal(next.syncStatus, 'failed')
  assert.equal(next.error, null)
  assert.equal(next.lastSyncedAt, null)
  assert.equal(next.lastSyncAttemptAt, 123)
})

test('rejected sync stores its attempt time for retry backoff', () => {
  const next = notificationReducer(undefined, {
    type: syncNotifications.rejected.type,
    payload: { attemptedAt: 456, message: 'Sin conexión' },
  })

  assert.equal(next.syncStatus, 'failed')
  assert.equal(next.lastSyncAttemptAt, 456)
})

test('limited notification history does not replace the server unread total', () => {
  let state = notificationReducer(undefined, {
    type: fetchUnreadCount.fulfilled.type,
    payload: 27,
  })

  state = notificationReducer(state, {
    type: fetchNotifications.fulfilled.type,
    payload: [{ id: 1, title: 'Visible', read: false }],
  })

  assert.equal(state.unreadCount, 27)
  assert.equal(state.items.length, 1)

  state = notificationReducer(state, {
    type: markNotificationsRead.pending.type,
    meta: { arg: [1] },
  })

  assert.equal(state.unreadCount, 26)
  assert.equal(state.items[0].read, true)
})
