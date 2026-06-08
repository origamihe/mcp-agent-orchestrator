import axios from 'axios'

const http = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
    timeout: 30000,
    headers: {
        'Content-Type': 'application/json',
    },
})

http.interceptors.request.use(
    (config) => config,
    (error) => Promise.reject(error),
)

http.interceptors.response.use(
    (response) => response.data,
    (error) => {
        console.error('[Request Error]', error.message)
        return Promise.reject(error)
    },
)

export default http