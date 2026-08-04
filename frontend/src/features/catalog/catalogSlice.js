import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

export const fetchCatalog = createAsyncThunk(
  'catalog/fetch',
  async ({ type, page = 1, filters = {} }) => {
    const activeFilters = Object.fromEntries(
      Object.entries(filters).filter(([, value]) => value !== '' && value !== null && value !== undefined),
    )
    const params = { type, page, perPage: 24, ...activeFilters }
    const { data } = await apiClient.get('/anilist/catalog', { params })
    return { ...data, type, filters: activeFilters }
  },
  {
    condition: (_, { getState }) => getState().catalog.status !== 'loading',
  },
)

export const fetchCatalogGenres = createAsyncThunk('catalog/genres', async () => {
  const { data } = await apiClient.get('/anilist/catalog/genres')
  return data
})

const catalogSlice = createSlice({
  name: 'catalog',
  initialState: {
    type: null, items: [], genres: [], filters: {}, currentPage: 1,
    hasNextPage: false, status: 'idle', genresStatus: 'idle', error: null,
  },
  reducers: {
    clearCatalog: (state) => { state.type = null; state.items = []; state.status = 'idle'; state.error = null },
  },
  extraReducers: (builder) => builder
    .addCase(fetchCatalog.pending, (state, action) => {
      state.status = 'loading'; state.type = action.meta.arg.type; state.error = null
    })
    .addCase(fetchCatalog.fulfilled, (state, action) => {
      state.status = 'succeeded'; state.type = action.payload.type; state.filters = action.payload.filters
      state.items = action.payload.results; state.currentPage = action.payload.currentPage
      state.hasNextPage = action.payload.hasNextPage
    })
    .addCase(fetchCatalog.rejected, (state, action) => { state.status = 'failed'; state.error = action.error.message })
    .addCase(fetchCatalogGenres.pending, (state) => { state.genresStatus = 'loading' })
    .addCase(fetchCatalogGenres.fulfilled, (state, action) => { state.genresStatus = 'succeeded'; state.genres = action.payload })
    .addCase(fetchCatalogGenres.rejected, (state) => { state.genresStatus = 'failed' }),
})

export const { clearCatalog } = catalogSlice.actions
export default catalogSlice.reducer
