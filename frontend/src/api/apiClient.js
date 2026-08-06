import axios from 'axios'

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  // Render Free suspends the backend after inactivity and a cold start can
  // take well over a minute. Keep the first request alive while it wakes up.
  timeout: 180000,
  headers: { 'Content-Type': 'application/json' },
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const message =
      error.response?.data?.message ||
      (error.code === 'ECONNABORTED'
        ? 'El servidor tardó demasiado en responder.'
        : 'No se pudo conectar con el backend.')
    return Promise.reject(new Error(message))
  },
)

export default apiClient
