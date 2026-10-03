import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'
import { normalizeNotificationSyncResponse, notificationSyncIsDue } from '../../lib/notificationSync.js'

export const fetchNotifications = createAsyncThunk('notifications/fetch', async () => (await apiClient.get('/notifications', { params: { limit: 10 } })).data, {
  condition: (_, { getState }) => getState().notifications.status !== 'loading',
})
export const syncNotifications = createAsyncThunk('notifications/sync', async () => {
  const data = (await apiClient.post('/notifications/sync', null, { params: { limit: 10 } })).data
  return normalizeNotificationSyncResponse(data)
}, {
  condition: (_, { getState }) => {
    const state = getState()
    return state.auth.accountType === 'ANILIST'
      && state.notifications.syncStatus !== 'loading'
      && notificationSyncIsDue(state.notifications.lastSyncedAt)
  },
})
export const markNotificationRead = createAsyncThunk('notifications/read', async (id) => { await apiClient.patch(`/notifications/${id}/read`); return id })
export const markNotificationsRead = createAsyncThunk('notifications/readVisible', async (ids) => { await apiClient.patch('/notifications/read', ids); return ids })
export const markAllNotificationsRead = createAsyncThunk('notifications/readAll', async () => { await apiClient.patch('/notifications/read-all') })

const slice = createSlice({
  name: 'notifications', initialState: { items: [], status: 'idle', syncStatus: 'idle', lastSyncedAt: null, error: null }, reducers: {},
  extraReducers: (builder) => builder
    .addCase(fetchNotifications.pending, (state) => { state.status = 'loading'; state.error = null })
    .addCase(fetchNotifications.fulfilled, (state, action) => { state.status = 'succeeded'; state.items = action.payload; state.error = null })
    .addCase(fetchNotifications.rejected, (state, action) => { state.status = 'failed'; state.error = action.error.message })
    .addCase(syncNotifications.pending, (state) => { state.syncStatus = 'loading' })
    .addCase(syncNotifications.fulfilled, (state, action) => { state.syncStatus = action.payload.successful ? 'succeeded' : 'failed'; if (action.payload.successful) state.lastSyncedAt = action.payload.syncedAt; state.items = action.payload.items })
    .addCase(syncNotifications.rejected, (state) => { state.syncStatus = 'failed' })
    .addCase(markNotificationRead.fulfilled, (state, action) => { const item = state.items.find((entry) => entry.id === action.payload); if (item) item.read = true })
    .addCase(markNotificationsRead.pending, (state, action) => { const ids = new Set(action.meta.arg); state.items.forEach((item) => { if (ids.has(item.id)) item.read = true }) })
    .addCase(markAllNotificationsRead.pending, (state) => { state.items.forEach((item) => { item.read = true }) })
    .addCase(markAllNotificationsRead.fulfilled, (state) => { state.items.forEach((item) => { item.read = true }) })
})
export default slice.reducer
