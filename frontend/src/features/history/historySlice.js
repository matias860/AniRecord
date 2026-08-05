import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

export const fetchHistory = createAsyncThunk('history/fetch', async (limit = 80) => {
  const { data } = await apiClient.get('/history', { params: { limit } })
  return data
})

const historySlice = createSlice({
  name: 'history',
  initialState: { items: [], status: 'idle', error: null },
  reducers: {},
  extraReducers: (builder) => builder
    .addCase(fetchHistory.pending, (state) => { state.status = 'loading'; state.error = null })
    .addCase(fetchHistory.fulfilled, (state, action) => { state.status = 'succeeded'; state.items = action.payload })
    .addCase(fetchHistory.rejected, (state, action) => { state.status = 'failed'; state.error = action.error.message })
})

export default historySlice.reducer
