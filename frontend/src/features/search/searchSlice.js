import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

export const searchMedia = createAsyncThunk(
  'search/query',
  async ({ query, type = 'ANIME', page = 1 }, { signal }) => {
    const { data } = await apiClient.get('/anilist/search', {
      params: { query, type, page, perPage: 20 },
      signal,
    })
    return { ...data, query, type }
  },
)

const searchSlice = createSlice({
  name: 'search',
  initialState: {
    query: '', type: 'ANIME', results: [], currentPage: 1, hasNextPage: false,
    status: 'idle', error: null,
  },
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(searchMedia.pending, (state, action) => { state.status = 'loading'; state.error = null; state.requestId = action.meta.requestId })
      .addCase(searchMedia.fulfilled, (state, action) => {
        if (state.requestId !== action.meta.requestId) return
        Object.assign(state, {
          status: 'succeeded', query: action.payload.query, type: action.payload.type,
          results: action.payload.results, currentPage: action.payload.currentPage,
          hasNextPage: action.payload.hasNextPage,
        })
      })
      .addCase(searchMedia.rejected, (state, action) => { if (state.requestId === action.meta.requestId && !action.meta.aborted) { state.status = 'failed'; state.error = action.error.message } })
  },
})

export default searchSlice.reducer
