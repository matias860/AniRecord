import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'
import { fetchPersonFavorites } from '../favorites/favoritesSlice.js'

export const fetchLibrary = createAsyncThunk(
  'library/fetchAll',
  async (filters = {}) => {
    const params = Object.fromEntries(
      Object.entries(filters).filter(([, value]) => value),
    )
    const { data } = await apiClient.get('/library', { params })
    return data
  },
  {
    condition: (_, { getState }) => getState().library.status !== 'loading',
  },
)

export const addToLibrary = createAsyncThunk(
  'library/add',
  async ({ anilistId, trackingStatus = 'PLANNED' }) => {
    const { data } = await apiClient.post('/library', { anilistId, trackingStatus })
    return data
  },
)

export const updateLibraryEntry = createAsyncThunk(
  'library/update',
  async ({ id, changes }) => {
    const { data } = await apiClient.patch(`/library/${id}`, changes)
    return data
  },
)

export const incrementProgress = createAsyncThunk(
  'library/increment',
  async (id) => {
    const { data } = await apiClient.post(`/library/${id}/progress/increment`)
    return data
  },
)

export const removeFromLibrary = createAsyncThunk(
  'library/remove',
  async (id) => {
    await apiClient.delete(`/library/${id}`)
    return id
  },
)

export const synchronizeAniListLibrary = createAsyncThunk(
  'library/synchronizeAniList',
  async (_, { dispatch }) => {
    const { data } = await apiClient.post('/library/import/anilist')
    await Promise.all([
      dispatch(fetchLibrary()).unwrap(),
      dispatch(fetchPersonFavorites(true)).unwrap(),
    ])
    return data
  },
)

const replaceEntry = (state, entry) => {
  const index = state.items.findIndex((item) => item.id === entry.id)
  if (index >= 0) state.items[index] = entry
  else state.items.unshift(entry)
}

const librarySlice = createSlice({
  name: 'library',
  initialState: {
    items: [], status: 'idle', mutationStatus: 'idle', syncStatus: 'idle', syncResult: null, error: null,
    filters: { mediaType: 'ANIME', trackingStatus: 'ALL', search: '', year: '', season: '', genre: [], releaseStatus: '', score: '' },
  },
  reducers: {
    setLibraryMediaType: (state, action) => {
      state.filters.mediaType = action.payload
      if (action.payload === 'MANGA') state.filters.season = ''
    },
    setLibraryTrackingStatus: (state, action) => { state.filters.trackingStatus = action.payload },
    setLibraryFilter: (state, action) => { state.filters[action.payload.key] = action.payload.value },
    resetLibraryFilters: (state) => {
      state.filters.search = ''; state.filters.year = ''; state.filters.season = ''
      state.filters.genre = []; state.filters.releaseStatus = ''; state.filters.score = ''
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchLibrary.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(fetchLibrary.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.items = action.payload
      })
      .addCase(fetchLibrary.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.error.message
      })
      .addCase(removeFromLibrary.fulfilled, (state, action) => {
        state.mutationStatus = 'succeeded'
        state.items = state.items.filter((item) => item.id !== action.payload)
      })
      .addCase(synchronizeAniListLibrary.pending, (state) => {
        state.syncStatus = 'loading'
        state.syncResult = null
        state.error = null
      })
      .addCase(synchronizeAniListLibrary.fulfilled, (state, action) => {
        state.syncStatus = 'succeeded'
        state.syncResult = action.payload
      })
      .addCase(synchronizeAniListLibrary.rejected, (state, action) => {
        state.syncStatus = 'failed'
        state.error = action.error.message
      })
      .addMatcher(
        (action) =>
          [addToLibrary.pending.type, updateLibraryEntry.pending.type, incrementProgress.pending.type, removeFromLibrary.pending.type].includes(action.type),
        (state) => {
          state.mutationStatus = 'loading'
          state.error = null
        },
      )
      .addMatcher(
        (action) =>
          [addToLibrary.fulfilled.type, updateLibraryEntry.fulfilled.type, incrementProgress.fulfilled.type].includes(action.type),
        (state, action) => {
          state.mutationStatus = 'succeeded'
          replaceEntry(state, action.payload)
        },
      )
      .addMatcher(
        (action) =>
          [addToLibrary.rejected.type, updateLibraryEntry.rejected.type, incrementProgress.rejected.type, removeFromLibrary.rejected.type].includes(action.type),
        (state, action) => {
          state.mutationStatus = 'failed'
          state.error = action.error.message
        },
      )
  },
})

export const { resetLibraryFilters, setLibraryFilter, setLibraryMediaType, setLibraryTrackingStatus } = librarySlice.actions
export default librarySlice.reducer
