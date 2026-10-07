import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api',
  // Render Free suspends the backend after inactivity and a cold start can
  // take well over a minute. Keep the first request alive while it wakes up.
  timeout: 180000,
  withCredentials: true,
  headers: { 'X-Anime-List-Request': '1' },
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const path = new URL(error.config?.url || '/', window.location.origin).pathname.replace(/^\/api/, '')
    const accessEndpoints = ['/auth/status', '/auth/local-login', '/auth/register', '/auth/claim', '/auth/logout']
    if (error.response?.status === 401 && !accessEndpoints.includes(path)) {
      window.dispatchEvent(new Event('anirecord:session-expired'))
    }
    const status = error.response?.status
    let fallbackMessage = 'No se pudo conectar con el backend.'
    if (error.code === 'ECONNABORTED') fallbackMessage = 'El servidor tardó demasiado en responder.'
    else if ([502, 503, 504].includes(status)) fallbackMessage = `El backend no está disponible temporalmente (HTTP ${status}).`
    else if (status === 404) fallbackMessage = 'No se encontró la ruta del backend (HTTP 404).'
    else if (status) fallbackMessage = `El backend respondió con un error (HTTP ${status}).`
    const message = error.response?.data?.message || fallbackMessage
    const wrappedError = new Error(message, { cause: error })
    wrappedError.status = error.response?.status
    wrappedError.code = error.code
    wrappedError.response = error.response
    return Promise.reject(wrappedError)
  },
)

export default apiClient
