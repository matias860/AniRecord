import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

export const fetchAuth = createAsyncThunk('auth/status', async () => (await apiClient.get('/auth/status')).data)
export const logout = createAsyncThunk('auth/logout', async () => { await apiClient.post('/auth/logout') })

const slice = createSlice({
  name: 'auth',
  initialState: { authenticated: false, userId: null, username: null, avatarUrl: null, needsImport: false, status: 'idle', error: null },
  reducers: {},
  extraReducers: (builder) => builder
    .addCase(fetchAuth.pending, (state) => { state.status = 'loading' })
    .addCase(fetchAuth.fulfilled, (state, action) => { Object.assign(state, action.payload); state.status = 'succeeded'; state.error = null })
    .addCase(fetchAuth.rejected, (state, action) => { state.status = 'failed'; state.error = action.error.message })
    .addCase(logout.fulfilled, (state) => { state.authenticated = false; state.status = 'succeeded' }),
})
export default slice.reducer
