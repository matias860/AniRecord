import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

export const fetchCatalog = createAsyncThunk(
  'catalog/fetch',
  async ({ type, page = 1, filters = {} }, { signal, rejectWithValue }) => {
    try {
      const activeFilters = Object.fromEntries(
        Object.entries(filters).filter(([, value]) => value !== '' && value !== null && value !== undefined),
      )
      const params = { type, page, perPage: 24, ...activeFilters }
      const { data } = await apiClient.get('/anilist/catalog', { params, signal, paramsSerializer: { indexes: null } })
      return { ...data, type, filters: activeFilters }
    } catch (error) {
      return rejectWithValue({ message: error.message, unavailable: error.status === 502 })
    }
  },
)

export const fetchCatalogGenres = createAsyncThunk('catalog/genres', async (_, { rejectWithValue }) => {
  try {
    const { data } = await apiClient.get('/anilist/catalog/genres')
    return data
  } catch (error) {
    return rejectWithValue({ message: error.message, unavailable: error.status === 502 })
  }
})

const catalogSlice = createSlice({
  name: 'catalog',
  initialState: {
    type: null, items: [], genres: [], filters: {}, currentPage: 1,
    hasNextPage: false, status: 'idle', genresStatus: 'idle', error: null, unavailable: false, requestId: null,
  },
  reducers: {
    clearCatalog: (state) => { state.type = null; state.items = []; state.status = 'idle'; state.error = null },
  },
  extraReducers: (builder) => builder
    .addCase(fetchCatalog.pending, (state, action) => {
      state.status = 'loading'; state.type = action.meta.arg.type; state.error = null; state.unavailable = false; state.requestId = action.meta.requestId
    })
    .addCase(fetchCatalog.fulfilled, (state, action) => {
      if (state.requestId !== action.meta.requestId) return
      state.status = 'succeeded'; state.type = action.payload.type; state.filters = action.payload.filters
      state.items = action.payload.results; state.currentPage = action.payload.currentPage
      state.hasNextPage = action.payload.hasNextPage
    })
    .addCase(fetchCatalog.rejected, (state, action) => {
      if (state.requestId !== action.meta.requestId || action.meta.aborted) return
      state.status = 'failed'; state.error = action.payload?.message || action.error.message; state.unavailable = Boolean(action.payload?.unavailable)
    })
    .addCase(fetchCatalogGenres.pending, (state) => { state.genresStatus = 'loading' })
    .addCase(fetchCatalogGenres.fulfilled, (state, action) => { state.genresStatus = 'succeeded'; state.genres = action.payload })
    .addCase(fetchCatalogGenres.rejected, (state, action) => {
      state.genresStatus = 'failed'
      if (action.payload?.unavailable) state.unavailable = true
    }),
})

export const { clearCatalog } = catalogSlice.actions
export default catalogSlice.reducer
