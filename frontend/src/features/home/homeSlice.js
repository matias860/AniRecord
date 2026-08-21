import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'
import { cachedAniListError, clearAniListOutage, isAniListTemporarilyUnavailable, rememberAniListOutage } from '../../api/anilistAvailability.js'

export const fetchAiringSchedule = createAsyncThunk('home/airing', async (_, { rejectWithValue, signal }) => {
  if (isAniListTemporarilyUnavailable()) return rejectWithValue(cachedAniListError())
  try {
    const { data } = await apiClient.get('/library/airing', { signal })
    clearAniListOutage()
    return data
  } catch (error) {
    if (error.status === 502) rememberAniListOutage()
    return rejectWithValue({ message: error.message, unavailable: error.status === 502 })
  }
}, { condition: (_, { getState }) => getState().home.status !== 'loading' })

export const fetchSeason = createAsyncThunk(
  'home/season',
  async ({ season, year }, { signal }) => {
    const { data } = await apiClient.get('/anilist/season', { params: { season, year, limit: 50 }, signal })
    return { entries: data, season, year }
  },
  {
    condition: ({ season, year }, { getState }) => {
      const home = getState().home
      const requestKey = `${season}-${year}`
      return !home.seasonCache[requestKey] && home.seasonRequestKey !== requestKey
    },
  },
)

const homeSlice = createSlice({
  name: 'home',
  initialState: {
    airing: [], season: [], seasonCache: {}, status: 'idle', seasonStatus: 'idle', error: null, unavailable: false,
    seasonOffset: 0, loadedSeason: null, loadedYear: null, watchingPage: 0, readingPage: 0,
  },
  reducers: {
    setSeasonOffset: (state, action) => { state.seasonOffset = action.payload },
    setWatchingPage: (state, action) => { state.watchingPage = action.payload },
    setReadingPage: (state, action) => { state.readingPage = action.payload },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchAiringSchedule.pending, (state, action) => { state.status = 'loading'; state.error = null; state.unavailable = false; state.airingRequestId = action.meta.requestId })
      .addCase(fetchAiringSchedule.fulfilled, (state, action) => { if (state.airingRequestId === action.meta.requestId) { state.status = 'succeeded'; state.airing = action.payload; state.unavailable = false } })
      .addCase(fetchAiringSchedule.rejected, (state, action) => {
        if (state.airingRequestId !== action.meta.requestId || action.meta.aborted) return
        state.status = 'failed'; state.error = action.payload?.message || action.error.message; state.unavailable = Boolean(action.payload?.unavailable)
      })
      .addCase(fetchSeason.pending, (state, action) => {
        state.seasonStatus = 'loading'
        state.seasonRequestKey = `${action.meta.arg.season}-${action.meta.arg.year}`
      })
      .addCase(fetchSeason.fulfilled, (state, action) => {
        state.seasonStatus = 'succeeded'
        state.season = action.payload.entries
        state.seasonCache[`${action.payload.season}-${action.payload.year}`] = action.payload.entries
        state.loadedSeason = action.payload.season
        state.loadedYear = action.payload.year
        state.seasonRequestKey = null
      })
      .addCase(fetchSeason.rejected, (state, action) => {
        if (state.seasonRequestKey !== `${action.meta.arg.season}-${action.meta.arg.year}` || action.meta.aborted) return
        state.seasonStatus = 'failed'
        state.seasonRequestKey = null
      })
  },
})

export const { setSeasonOffset, setWatchingPage, setReadingPage } = homeSlice.actions
export default homeSlice.reducer
