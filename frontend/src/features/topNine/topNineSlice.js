import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

export const fetchCurrentTopNine = createAsyncThunk(
  'topNine/fetchCurrent',
  async () => {
    const { data } = await apiClient.get('/top-nine')
    return data
  },
  {
    condition: (_, { getState }) => {
      const state = getState().topNine
      return state.status !== 'loading' && (!state.fetchedAt || Date.now() - state.fetchedAt > 2 * 60 * 1000)
    },
  },
)

export const publishTopNine = createAsyncThunk(
  'topNine/publish',
  async (libraryEntryIds) => {
    const { data } = await apiClient.post('/top-nine', { libraryEntryIds })
    return data
  },
)

const topNineSlice = createSlice({
  name: 'topNine',
  initialState: { prompt: null, mine: null, submissions: [], status: 'idle', publishStatus: 'idle', error: null, fetchedAt: null },
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchCurrentTopNine.pending, (state) => { state.status = 'loading'; state.error = null })
      .addCase(fetchCurrentTopNine.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.prompt = action.payload.prompt
        state.mine = action.payload.mine
        state.submissions = action.payload.submissions
        state.fetchedAt = Date.now()
      })
      .addCase(fetchCurrentTopNine.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.error.message
      })
      .addCase(publishTopNine.pending, (state) => { state.publishStatus = 'loading'; state.error = null })
      .addCase(publishTopNine.fulfilled, (state, action) => {
        state.publishStatus = 'succeeded'
        state.mine = action.payload
        state.submissions = [action.payload, ...state.submissions.filter((entry) => entry.userId !== action.payload.userId)]
        state.fetchedAt = Date.now()
      })
      .addCase(publishTopNine.rejected, (state, action) => {
        state.publishStatus = 'failed'
        state.error = action.error.message
      })
  },
})

export default topNineSlice.reducer
