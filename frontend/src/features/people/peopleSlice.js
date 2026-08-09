import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

const fetchPerson = (kind) => createAsyncThunk(
  `people/fetch${kind}`,
  async (id) => (await apiClient.get(`/anilist/${kind === 'character' ? 'characters' : 'staff'}/${id}`)).data,
  { condition: (id, { getState }) => !getState().people[kind].cache[id] && getState().people[kind].loadingId !== String(id) },
)

export const fetchCharacter = fetchPerson('character')
export const fetchStaff = fetchPerson('staff')

const initialBucket = () => ({ cache: {}, loadingId: null, error: null })
const peopleSlice = createSlice({
  name: 'people',
  initialState: { character: initialBucket(), staff: initialBucket() },
  reducers: {},
  extraReducers: (builder) => {
    for (const [thunk, kind] of [[fetchCharacter, 'character'], [fetchStaff, 'staff']]) {
      builder
        .addCase(thunk.pending, (state, action) => { state[kind].loadingId = String(action.meta.arg); state[kind].error = null })
        .addCase(thunk.fulfilled, (state, action) => { state[kind].cache[action.payload.anilistId] = action.payload; state[kind].loadingId = null })
        .addCase(thunk.rejected, (state, action) => { state[kind].loadingId = null; state[kind].error = action.error.message })
    }
  },
})

export default peopleSlice.reducer
