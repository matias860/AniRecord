import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { PersistGate } from 'redux-persist/integration/react'
import { BrowserRouter } from 'react-router-dom'
import { MotionConfig } from 'framer-motion'
import '@fontsource-variable/manrope'
import '@fontsource-variable/sora'
import App from './App.jsx'
import { persistor, store } from './app/store.js'
import './index.css'
import './styles/mobile.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <Provider store={store}>
      <PersistGate loading={null} persistor={persistor}>
        <BrowserRouter>
          <MotionConfig reducedMotion="user">
            <App />
          </MotionConfig>
        </BrowserRouter>
      </PersistGate>
    </Provider>
  </StrictMode>,
)
