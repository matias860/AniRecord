import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

export const fetchPersonFavorites = createAsyncThunk('favorites/fetchPeople', async () => (await apiClient.get('/favorites/people')).data, {
  condition: (_, { getState }) => getState().favorites.status !== 'loading' && getState().favorites.status !== 'succeeded',
})
export const addPersonFavorite = createAsyncThunk('favorites/addPerson', async (person) => (await apiClient.post('/favorites/people', person)).data)
export const removePersonFavorite = createAsyncThunk('favorites/removePerson', async ({ anilistId, personType }) => {
  await apiClient.delete(`/favorites/people/${personType}/${anilistId}`)
  return { anilistId, personType }
})

const favoritesSlice = createSlice({
  name: 'favorites',
  initialState: { people: [], status: 'idle', mutationStatus: 'idle', error: null },
  reducers: {},
  extraReducers: (builder) => builder
    .addCase(fetchPersonFavorites.pending, (state) => { state.status = 'loading'; state.error = null })
    .addCase(fetchPersonFavorites.fulfilled, (state, action) => { state.status = 'succeeded'; state.people = action.payload })
    .addCase(fetchPersonFavorites.rejected, (state, action) => { state.status = 'failed'; state.error = action.error.message })
    .addCase(addPersonFavorite.pending, (state) => { state.mutationStatus = 'loading' })
    .addCase(addPersonFavorite.fulfilled, (state, action) => { state.mutationStatus = 'succeeded'; if (!state.people.some((item) => item.anilistId === action.payload.anilistId && item.personType === action.payload.personType)) state.people.unshift(action.payload) })
    .addCase(addPersonFavorite.rejected, (state, action) => { state.mutationStatus = 'failed'; state.error = action.error.message })
    .addCase(removePersonFavorite.pending, (state) => { state.mutationStatus = 'loading' })
    .addCase(removePersonFavorite.fulfilled, (state, action) => { state.mutationStatus = 'succeeded'; state.people = state.people.filter((item) => item.anilistId !== action.payload.anilistId || item.personType !== action.payload.personType) })
    .addCase(removePersonFavorite.rejected, (state, action) => { state.mutationStatus = 'failed'; state.error = action.error.message }),
})
export default favoritesSlice.reducer
