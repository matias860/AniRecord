import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

const wait = (milliseconds, signal) => new Promise((resolve, reject) => {
  const handleAbort = () => {
    window.clearTimeout(timer)
    reject(new DOMException('Solicitud cancelada', 'AbortError'))
  }
  const timer = window.setTimeout(() => {
    signal?.removeEventListener('abort', handleAbort)
    resolve()
  }, milliseconds)
  signal?.addEventListener('abort', handleAbort, { once: true })
})

export const fetchAuth = createAsyncThunk('auth/status', async (_, { signal }) => {
  let lastError
  for (let attempt = 0; attempt < 4; attempt += 1) {
    try {
      return (await apiClient.get('/auth/status', { timeout: 18000, signal })).data
    } catch (error) {
      lastError = error
      const serverCanRecover = !error.status || error.status >= 500
      if (!serverCanRecover || attempt === 3 || signal.aborted) throw error
      await wait(2500, signal)
    }
  }
  throw lastError
})
export const logout = createAsyncThunk('auth/logout', async (pushEndpoint = null) => { await apiClient.post('/auth/logout', { pushEndpoint }) })
export const localLogin = createAsyncThunk('auth/localLogin', async (credentials, { rejectWithValue }) => {
  try { return (await apiClient.post('/auth/local-login', credentials)).data }
  catch (error) { return rejectWithValue(error.response?.data?.message || 'No se pudo iniciar sesión') }
})
export const registerLocal = createAsyncThunk('auth/registerLocal', async (profile, { rejectWithValue }) => {
  try { return (await apiClient.post('/auth/register', profile)).data }
  catch (error) {
    const validation = error.response?.data?.validationErrors
    return rejectWithValue(validation ? Object.values(validation)[0] : error.response?.data?.message || 'No se pudo crear la cuenta')
  }
})

export const acknowledgeSyncPrompt = createAsyncThunk('auth/acknowledgeSyncPrompt', async (commit) => {
  const { data } = await apiClient.patch('/auth/sync-prompt', { commit })
  return data.lastSyncPromptCommit
})

const slice = createSlice({
  name: 'auth',
  initialState: { authenticated: false, userId: null, username: null, avatarUrl: null, accountType: null, needsImport: false, lastSyncPromptCommit: null, status: 'idle', statusRequestId: null, mutationStatus: 'idle', error: null },
  reducers: {
    clearAuthError: (state) => { state.error = null },
    sessionExpired: (state) => {
      Object.assign(state, { authenticated: false, userId: null, username: null, avatarUrl: null, accountType: null, needsImport: false, status: 'succeeded', statusRequestId: null, mutationStatus: 'idle', error: null })
    },
    oauthFailed: (state, action) => { state.status = 'succeeded'; state.statusRequestId = null; state.error = action.payload },
  },
  extraReducers: (builder) => builder
    .addCase(fetchAuth.pending, (state, action) => { state.status = 'loading'; state.statusRequestId = action.meta.requestId })
    .addCase(fetchAuth.fulfilled, (state, action) => { if (state.statusRequestId !== action.meta.requestId) return; state.statusRequestId = null; Object.assign(state, action.payload); state.status = 'succeeded'; state.error = null })
    .addCase(fetchAuth.rejected, (state, action) => { if (state.statusRequestId !== action.meta.requestId) return; state.statusRequestId = null; state.status = 'failed'; state.error = action.error.message })
    .addCase(logout.fulfilled, (state) => { state.authenticated = false; state.status = 'succeeded' })
    .addCase(localLogin.pending, (state) => { state.statusRequestId = null; state.status = 'succeeded'; state.mutationStatus = 'loading'; state.error = null })
    .addCase(localLogin.fulfilled, (state, action) => { Object.assign(state, action.payload); state.status = 'succeeded'; state.mutationStatus = 'idle' })
    .addCase(localLogin.rejected, (state, action) => { state.mutationStatus = 'idle'; state.error = action.payload })
    .addCase(registerLocal.pending, (state) => { state.statusRequestId = null; state.status = 'succeeded'; state.mutationStatus = 'loading'; state.error = null })
    .addCase(registerLocal.fulfilled, (state, action) => { Object.assign(state, action.payload); state.status = 'succeeded'; state.mutationStatus = 'idle' })
    .addCase(registerLocal.rejected, (state, action) => { state.mutationStatus = 'idle'; state.error = action.payload })
    .addCase(acknowledgeSyncPrompt.fulfilled, (state, action) => { state.lastSyncPromptCommit = action.payload }),
})
export const { clearAuthError, sessionExpired, oauthFailed } = slice.actions
export default slice.reducer
