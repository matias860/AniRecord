import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'
import { normalizeNotificationSyncResponse, notificationSyncCanAttempt } from '../../lib/notificationSync.js'

export const fetchUnreadCount = createAsyncThunk('notifications/unreadCount', async () => Number((await apiClient.get('/notifications/unread-count')).data?.count) || 0, {
  condition: (_, { getState }) => getState().notifications.countStatus !== 'loading',
})
export const fetchNotifications = createAsyncThunk('notifications/fetch', async () => (await apiClient.get('/notifications', { params: { limit: 10 } })).data, {
  condition: (_, { getState }) => getState().notifications.status !== 'loading',
})
export const syncNotifications = createAsyncThunk('notifications/sync', async (_, { rejectWithValue }) => {
  const attemptedAt = Date.now()
  try {
    const data = (await apiClient.post('/notifications/sync', null, { params: { limit: 10 } })).data
    return { ...normalizeNotificationSyncResponse(data, attemptedAt), attemptedAt }
  } catch (error) {
    return rejectWithValue({ attemptedAt, message: error.message })
  }
}, {
  condition: (_, { getState }) => {
    const state = getState()
    return state.auth.accountType === 'ANILIST'
      && state.notifications.syncStatus !== 'loading'
      && notificationSyncCanAttempt(
        state.notifications.lastSyncedAt,
        state.notifications.lastSyncAttemptAt,
      )
  },
})
export const markNotificationRead = createAsyncThunk('notifications/read', async (id) => { await apiClient.patch(`/notifications/${id}/read`); return id })
export const markNotificationsRead = createAsyncThunk('notifications/readVisible', async (ids) => { await apiClient.patch('/notifications/read', ids); return ids })
export const markAllNotificationsRead = createAsyncThunk('notifications/readAll', async () => { await apiClient.patch('/notifications/read-all') })

const slice = createSlice({
  name: 'notifications', initialState: { items: [], unreadCount: 0, countStatus: 'idle', status: 'idle', syncStatus: 'idle', lastSyncedAt: null, lastSyncAttemptAt: null, error: null }, reducers: {},
  extraReducers: (builder) => builder
    .addCase(fetchUnreadCount.pending, (state) => { state.countStatus = 'loading' })
    .addCase(fetchUnreadCount.fulfilled, (state, action) => { state.countStatus = 'succeeded'; state.unreadCount = action.payload })
    .addCase(fetchUnreadCount.rejected, (state) => { state.countStatus = 'failed' })
    .addCase(fetchNotifications.pending, (state) => { state.status = 'loading'; state.error = null })
    .addCase(fetchNotifications.fulfilled, (state, action) => { state.status = 'succeeded'; state.items = action.payload; state.error = null })
    .addCase(fetchNotifications.rejected, (state, action) => { state.status = 'failed'; state.error = action.error.message })
    .addCase(syncNotifications.pending, (state) => { state.syncStatus = 'loading' })
    .addCase(syncNotifications.fulfilled, (state, action) => {
      state.syncStatus = action.payload.successful ? 'succeeded' : 'failed'
      state.lastSyncAttemptAt = action.payload.attemptedAt
      if (action.payload.successful) state.lastSyncedAt = action.payload.syncedAt
      if (Array.isArray(action.payload.items)) {
        state.items = action.payload.items
      }
      state.status = 'succeeded'
      state.error = null
    })
    .addCase(syncNotifications.rejected, (state, action) => {
      state.syncStatus = 'failed'
      if (action.payload?.attemptedAt) state.lastSyncAttemptAt = action.payload.attemptedAt
    })
    .addCase(markNotificationRead.fulfilled, (state, action) => { const item = state.items.find((entry) => entry.id === action.payload); if (item && !item.read) { item.read = true; state.unreadCount = Math.max(0, state.unreadCount - 1) } })
    .addCase(markNotificationsRead.fulfilled, (state, action) => { const ids = new Set(action.payload); let changed = 0; state.items.forEach((item) => { if (ids.has(item.id) && !item.read) { item.read = true; changed += 1 } }); state.unreadCount = Math.max(0, state.unreadCount - changed) })
    .addCase(markAllNotificationsRead.fulfilled, (state) => { state.items.forEach((item) => { item.read = true }); state.unreadCount = 0 })
})
export default slice.reducer
