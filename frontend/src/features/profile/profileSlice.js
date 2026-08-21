import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import apiClient from '../../api/apiClient.js'

const defaults = {
  displayName: 'Mi perfil', bio: 'Coleccionando historias, un episodio a la vez.',
  avatarUrl: '', bannerUrl: '', backgroundColor: '#0a0c14', surfaceColor: '#171b29',
  accentColor: '#a88bff', dotsEnabled: true,
  accountType: null, anilistAvatarUrl: '', anilistBannerUrl: '', customAvatar: false, customBanner: false,
}
const editableKeys = ['displayName', 'bio', 'avatarUrl', 'bannerUrl', 'backgroundColor', 'surfaceColor', 'accentColor', 'dotsEnabled']

export const fetchProfile = createAsyncThunk('profile/fetch', async (_, { signal }) => {
  const { data } = await apiClient.get('/profile', { signal })
  return data
}, { condition: (_, { getState }) => !['loading', 'succeeded'].includes(getState().profile.status) })

export const saveProfile = createAsyncThunk('profile/save', async (_, { getState }) => {
  const current = getState().profile
  const profile = Object.fromEntries(editableKeys.map((key) => [key, current[key]]))
  const { data } = await apiClient.patch('/profile', profile)
  return data
})

export const uploadProfileAsset = createAsyncThunk('profile/uploadAsset', async ({ type, file }) => {
  const body = new FormData()
  body.append('file', file)
  return (await apiClient.post(`/profile/assets/${type}`, body)).data
})

export const deleteProfileAsset = createAsyncThunk('profile/deleteAsset', async (type) => (
  await apiClient.delete(`/profile/assets/${type}`)
).data)

const profileSlice = createSlice({
  name: 'profile',
  initialState: { ...defaults, status: 'idle', error: null, saved: false, assetStatus: 'idle' },
  reducers: {
    updateProfile: (state, action) => { Object.assign(state, action.payload); state.saved = false },
    resetTheme: (state) => {
      state.backgroundColor = defaults.backgroundColor
      state.surfaceColor = defaults.surfaceColor
      state.accentColor = defaults.accentColor
      state.dotsEnabled = defaults.dotsEnabled
      state.saved = false
    },
  },
  extraReducers: (builder) => builder
    .addCase(fetchProfile.pending, (state) => { state.status = 'loading' })
    .addCase(fetchProfile.fulfilled, (state, action) => { Object.assign(state, action.payload); state.status = 'succeeded'; state.error = null; state.saved = true })
    .addCase(fetchProfile.rejected, (state, action) => { state.status = 'failed'; state.error = action.error.message })
    .addCase(saveProfile.pending, (state) => { state.status = 'saving'; state.error = null })
    .addCase(saveProfile.fulfilled, (state, action) => { Object.assign(state, action.payload); state.status = 'succeeded'; state.saved = true })
    .addCase(saveProfile.rejected, (state, action) => { state.status = 'failed'; state.error = action.error.message })
    .addCase(uploadProfileAsset.pending, (state) => { state.assetStatus = 'uploading'; state.error = null })
    .addCase(uploadProfileAsset.fulfilled, (state, action) => { Object.assign(state, action.payload); state.assetStatus = 'idle'; state.saved = true })
    .addCase(uploadProfileAsset.rejected, (state, action) => { state.assetStatus = 'idle'; state.error = action.error.message })
    .addCase(deleteProfileAsset.pending, (state) => { state.assetStatus = 'uploading'; state.error = null })
    .addCase(deleteProfileAsset.fulfilled, (state, action) => { Object.assign(state, action.payload); state.assetStatus = 'idle'; state.saved = true })
    .addCase(deleteProfileAsset.rejected, (state, action) => { state.assetStatus = 'idle'; state.error = action.error.message }),
})

export const { updateProfile, resetTheme } = profileSlice.actions
export default profileSlice.reducer
