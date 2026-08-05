import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

export const fetchNotifications = createAsyncThunk('notifications/fetch', async () => (await apiClient.get('/notifications', { params: { limit: 10 } })).data)
export const markNotificationRead = createAsyncThunk('notifications/read', async (id) => { await apiClient.patch(`/notifications/${id}/read`); return id })
export const markAllNotificationsRead = createAsyncThunk('notifications/readAll', async () => { await apiClient.patch('/notifications/read-all') })

const slice = createSlice({
  name: 'notifications', initialState: { items: [], status: 'idle', error: null }, reducers: {},
  extraReducers: (builder) => builder
    .addCase(fetchNotifications.pending, (state) => { state.status = 'loading'; state.error = null })
    .addCase(fetchNotifications.fulfilled, (state, action) => { state.status = 'succeeded'; state.items = action.payload; state.error = null })
    .addCase(fetchNotifications.rejected, (state, action) => { state.status = 'failed'; state.error = action.error.message })
    .addCase(markNotificationRead.fulfilled, (state, action) => { const item = state.items.find((entry) => entry.id === action.payload); if (item) item.read = true })
    .addCase(markAllNotificationsRead.fulfilled, (state) => { state.items.forEach((item) => { item.read = true }) })
})
export default slice.reducer
