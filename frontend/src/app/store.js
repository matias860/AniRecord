import { combineReducers, configureStore } from '@reduxjs/toolkit'
import { persistReducer, persistStore } from 'redux-persist'
import libraryReducer from '../features/library/librarySlice.js'
import searchReducer from '../features/search/searchSlice.js'
import homeReducer from '../features/home/homeSlice.js'
import profileReducer from '../features/profile/profileSlice.js'
import catalogReducer from '../features/catalog/catalogSlice.js'
import historyReducer from '../features/history/historySlice.js'
import notificationReducer from '../features/notifications/notificationSlice.js'
import toastReducer from '../features/toast/toastSlice.js'
import { toastMiddleware } from './toastMiddleware.js'
import authReducer from '../features/auth/authSlice.js'
import peopleReducer from '../features/people/peopleSlice.js'
import favoritesReducer from '../features/favorites/favoritesSlice.js'
import recommendationsReducer from '../features/recommendations/recommendationsSlice.js'

const rootReducer = combineReducers({
  library: libraryReducer,
  search: searchReducer,
  home: homeReducer,
  profile: profileReducer,
  catalog: catalogReducer,
  history: historyReducer,
  notifications: notificationReducer,
  toast: toastReducer,
  auth: authReducer,
  people: peopleReducer,
  favorites: favoritesReducer,
  recommendations: recommendationsReducer,
})

const storage = {
  getItem: (key) => Promise.resolve(window.localStorage.getItem(key)),
  setItem: (key, value) => Promise.resolve(window.localStorage.setItem(key, value)),
  removeItem: (key) => Promise.resolve(window.localStorage.removeItem(key)),
}

const persistedReducer = persistReducer(
  { key: 'anime-list-v2', storage, whitelist: ['search'] },
  rootReducer,
)

export const store = configureStore({
  reducer: persistedReducer,
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({ serializableCheck: false }).concat(toastMiddleware),
})

export const persistor = persistStore(store)
