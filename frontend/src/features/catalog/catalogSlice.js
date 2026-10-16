import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'
import { cachedAniListError, clearAniListOutage, isAniListServiceError, isAniListTemporarilyUnavailable, rememberAniListOutage } from '../../api/anilistAvailability.js'

export const fetchCatalog = createAsyncThunk(
  'catalog/fetch',
  async ({ type, page = 1, filters = {} }, { signal, rejectWithValue }) => {
    if (isAniListTemporarilyUnavailable()) return rejectWithValue(cachedAniListError())
    try {
      const activeFilters = Object.fromEntries(
        Object.entries(filters).filter(([, value]) => value !== '' && value !== null && value !== undefined),
      )
      const params = { type, page, perPage: 24, ...activeFilters }
      const { data } = await apiClient.get('/anilist/catalog', { params, signal, paramsSerializer: { indexes: null } })
      clearAniListOutage()
      return { ...data, type, filters: activeFilters }
    } catch (error) {
      const unavailable = isAniListServiceError(error)
      if (unavailable) rememberAniListOutage()
      return rejectWithValue({ message: error.message, unavailable })
    }
  },
)

export const fetchCatalogCategories = createAsyncThunk('catalog/categories', async (_, { rejectWithValue, signal }) => {
  if (isAniListTemporarilyUnavailable()) return rejectWithValue(cachedAniListError())
  try {
    const { data } = await apiClient.get('/anilist/catalog/categories', { signal })
    clearAniListOutage()
    return data
  } catch (error) {
    const unavailable = isAniListServiceError(error)
    if (unavailable) rememberAniListOutage()
    return rejectWithValue({ message: error.message, unavailable })
  }
}, { condition: (_, { getState }) => !['loading', 'succeeded'].includes(getState().catalog.categoriesStatus) })

const catalogSlice = createSlice({
  name: 'catalog',
  initialState: {
    type: null, items: [], genres: [], tags: [], filters: {}, currentPage: 1,
    hasNextPage: false, status: 'idle', categoriesStatus: 'idle', error: null, unavailable: false, requestId: null,
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
    .addCase(fetchCatalogCategories.pending, (state) => { state.categoriesStatus = 'loading' })
    .addCase(fetchCatalogCategories.fulfilled, (state, action) => {
      state.categoriesStatus = 'succeeded'; state.genres = action.payload.genres; state.tags = action.payload.tags
    })
    .addCase(fetchCatalogCategories.rejected, (state, action) => {
      state.categoriesStatus = 'failed'
      if (action.payload?.unavailable) state.unavailable = true
    }),
})

export const { clearCatalog } = catalogSlice.actions
export default catalogSlice.reducer
