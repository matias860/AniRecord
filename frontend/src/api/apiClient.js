import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api',
  // Render Free suspends the backend after inactivity and a cold start can
  // take well over a minute. Keep the first request alive while it wakes up.
  timeout: 180000,
  withCredentials: true,
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
    return Promise.reject(new Error(message))
  },
)

export default apiClient
