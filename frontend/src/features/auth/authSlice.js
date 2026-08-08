import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

export const fetchAuth = createAsyncThunk('auth/status', async () => (await apiClient.get('/auth/status')).data)
export const logout = createAsyncThunk('auth/logout', async () => { await apiClient.post('/auth/logout') })
export const localLogin = createAsyncThunk('auth/localLogin', async (credentials, { rejectWithValue }) => {
  try { return (await apiClient.post('/auth/local-login', credentials)).data }
  catch (error) { return rejectWithValue(error.response?.data?.message || 'No se pudo iniciar sesión') }
})
export const registerLocal = createAsyncThunk('auth/registerLocal', async (profile, { rejectWithValue }) => {
  try { return (await apiClient.post('/auth/register', profile)).data }
  catch (error) {
    const validation = error.response?.data?.errors
    return rejectWithValue(validation ? Object.values(validation)[0] : error.response?.data?.message || 'No se pudo crear la cuenta')
  }
})

const slice = createSlice({
  name: 'auth',
  initialState: { authenticated: false, userId: null, username: null, avatarUrl: null, accountType: null, needsImport: false, status: 'idle', mutationStatus: 'idle', error: null },
  reducers: { clearAuthError: (state) => { state.error = null } },
  extraReducers: (builder) => builder
    .addCase(fetchAuth.pending, (state) => { state.status = 'loading' })
    .addCase(fetchAuth.fulfilled, (state, action) => { Object.assign(state, action.payload); state.status = 'succeeded'; state.error = null })
    .addCase(fetchAuth.rejected, (state, action) => { state.status = 'failed'; state.error = action.error.message })
    .addCase(logout.fulfilled, (state) => { state.authenticated = false; state.status = 'succeeded' })
    .addCase(localLogin.pending, (state) => { state.mutationStatus = 'loading'; state.error = null })
    .addCase(localLogin.fulfilled, (state, action) => { Object.assign(state, action.payload); state.status = 'succeeded'; state.mutationStatus = 'idle' })
    .addCase(localLogin.rejected, (state, action) => { state.mutationStatus = 'idle'; state.error = action.payload })
    .addCase(registerLocal.pending, (state) => { state.mutationStatus = 'loading'; state.error = null })
    .addCase(registerLocal.fulfilled, (state, action) => { Object.assign(state, action.payload); state.status = 'succeeded'; state.mutationStatus = 'idle' })
    .addCase(registerLocal.rejected, (state, action) => { state.mutationStatus = 'idle'; state.error = action.payload }),
})
export const { clearAuthError } = slice.actions
export default slice.reducer
