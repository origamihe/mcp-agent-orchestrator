import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig } from 'axios'

const http: AxiosInstance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
    timeout: 30000,
    headers: {
        'Content-Type': 'application/json',
    },
})

http.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('auth_token')
        if (token) {
            config.headers.Authorization = `Bearer ${token}`
        }
        return config
    },
    (error) => Promise.reject(error),
)

http.interceptors.response.use(
    (response) => response.data,
    (error) => {
        const message = error.response?.data?.message || error.message || '请求失败'
        console.error('[API Error]', message, error.response?.status)
        return Promise.reject(error)
    },
)

export function setAuthToken(token: string | null) {
    if (token) {
        localStorage.setItem('auth_token', token)
    } else {
        localStorage.removeItem('auth_token')
    }
}

export default http