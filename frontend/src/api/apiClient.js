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
    if (error.response?.status === 401 && !error.config?.url?.includes('/auth/status')) {
      window.localStorage.removeItem('persist:anime-list-v2')
      window.location.replace('/')
    }
    const message =
      error.response?.data?.message ||
      (error.code === 'ECONNABORTED'
        ? 'El servidor tardó demasiado en responder.'
        : 'No se pudo conectar con el backend.')
    const wrappedError = new Error(message, { cause: error })
    wrappedError.status = error.response?.status
    wrappedError.code = error.code
    wrappedError.response = error.response
    return Promise.reject(wrappedError)
  },
)

export default apiClient
