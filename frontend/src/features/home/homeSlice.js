import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

export const fetchAiringSchedule = createAsyncThunk('home/airing', async () => {
  const { data } = await apiClient.get('/library/airing')
  return data
})

export const fetchCurrentSeason = createAsyncThunk('home/season', async () => {
  const { data } = await apiClient.get('/anilist/season/current', { params: { limit: 20 } })
  return data
})

const homeSlice = createSlice({
  name: 'home',
  initialState: { airing: [], season: [], status: 'idle', seasonStatus: 'idle', error: null },
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchAiringSchedule.pending, (state) => { state.status = 'loading'; state.error = null })
      .addCase(fetchAiringSchedule.fulfilled, (state, action) => { state.status = 'succeeded'; state.airing = action.payload })
      .addCase(fetchAiringSchedule.rejected, (state, action) => { state.status = 'failed'; state.error = action.error.message })
      .addCase(fetchCurrentSeason.pending, (state) => { state.seasonStatus = 'loading' })
      .addCase(fetchCurrentSeason.fulfilled, (state, action) => { state.seasonStatus = 'succeeded'; state.season = action.payload })
      .addCase(fetchCurrentSeason.rejected, (state) => { state.seasonStatus = 'failed' })
  },
})

export default homeSlice.reducer
