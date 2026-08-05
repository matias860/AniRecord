import { combineReducers, configureStore } from '@reduxjs/toolkit'
import { persistReducer, persistStore } from 'redux-persist'
import libraryReducer from '../features/library/librarySlice.js'
import searchReducer from '../features/search/searchSlice.js'
import homeReducer from '../features/home/homeSlice.js'
import profileReducer from '../features/profile/profileSlice.js'
import catalogReducer from '../features/catalog/catalogSlice.js'
import historyReducer from '../features/history/historySlice.js'

const rootReducer = combineReducers({
  library: libraryReducer,
  search: searchReducer,
  home: homeReducer,
  profile: profileReducer,
  catalog: catalogReducer,
  history: historyReducer,
})

const storage = {
  getItem: (key) => Promise.resolve(window.localStorage.getItem(key)),
  setItem: (key, value) => Promise.resolve(window.localStorage.setItem(key, value)),
  removeItem: (key) => Promise.resolve(window.localStorage.removeItem(key)),
}

const persistedReducer = persistReducer(
  { key: 'anime-list', storage, whitelist: ['search', 'profile'] },
  rootReducer,
)

export const store = configureStore({
  reducer: persistedReducer,
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({ serializableCheck: false }),
})

export const persistor = persistStore(store)
