import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

export const fetchCatalog = createAsyncThunk('catalog/fetch', async ({ type, page = 1 }) => {
  const { data } = await apiClient.get('/anilist/catalog', { params: { type, page, perPage: 24 } })
  return { ...data, type }
})

const catalogSlice = createSlice({
  name: 'catalog',
  initialState: { type: 'ANIME', items: [], currentPage: 1, hasNextPage: false, status: 'idle', error: null },
  reducers: {
    setCatalogType: (state, action) => { state.type = action.payload },
  },
  extraReducers: (builder) => builder
    .addCase(fetchCatalog.pending, (state) => { state.status = 'loading'; state.error = null })
    .addCase(fetchCatalog.fulfilled, (state, action) => {
      state.status = 'succeeded'; state.type = action.payload.type; state.items = action.payload.results
      state.currentPage = action.payload.currentPage; state.hasNextPage = action.payload.hasNextPage
    })
    .addCase(fetchCatalog.rejected, (state, action) => { state.status = 'failed'; state.error = action.error.message }),
})

export const { setCatalogType } = catalogSlice.actions
export default catalogSlice.reducer
