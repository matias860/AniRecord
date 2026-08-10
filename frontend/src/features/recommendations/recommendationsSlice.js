import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

export const fetchWeeklyRecommendations = createAsyncThunk(
  'recommendations/fetchWeekly',
  async () => {
    const { data } = await apiClient.get('/recommendations')
    return data
  },
  { condition: (_, { getState }) => getState().recommendations.status !== 'loading' },
)

export const fetchMediaRecommendations = createAsyncThunk(
  'recommendations/fetchMedia',
  async (anilistId) => {
    const { data } = await apiClient.get(`/recommendations/media/${anilistId}`)
    return { anilistId: Number(anilistId), entries: data, fetchedAt: Date.now() }
  },
  {
    condition: (anilistId, { getState }) => {
      const cached = getState().recommendations.byMedia[Number(anilistId)]
      return !cached || Date.now() - cached.fetchedAt > 5 * 60 * 1000
    },
  },
)

export const createWeeklyRecommendation = createAsyncThunk(
  'recommendations/create',
  async (libraryEntryId) => {
    const { data } = await apiClient.post('/recommendations', { libraryEntryId })
    return data
  },
)

const recommendationsSlice = createSlice({
  name: 'recommendations',
  initialState: {
    items: [], status: 'idle', createStatus: 'idle', error: null, byMedia: {},
  },
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchWeeklyRecommendations.pending, (state) => { state.status = 'loading'; state.error = null })
      .addCase(fetchWeeklyRecommendations.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.items = action.payload
      })
      .addCase(fetchWeeklyRecommendations.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.error.message
      })
      .addCase(fetchMediaRecommendations.fulfilled, (state, action) => {
        state.byMedia[action.payload.anilistId] = {
          entries: action.payload.entries,
          fetchedAt: action.payload.fetchedAt,
        }
      })
      .addCase(createWeeklyRecommendation.pending, (state) => { state.createStatus = 'loading' })
      .addCase(createWeeklyRecommendation.fulfilled, (state, action) => {
        state.createStatus = 'succeeded'
        state.items.unshift(action.payload)
        state.byMedia[action.payload.anilistId] = {
          entries: [action.payload, ...(state.byMedia[action.payload.anilistId]?.entries ?? [])],
          fetchedAt: Date.now(),
        }
      })
      .addCase(createWeeklyRecommendation.rejected, (state) => { state.createStatus = 'failed' })
  },
})

export default recommendationsSlice.reducer
