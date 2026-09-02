export interface PaginatedResponse<T> {
    data: T[]
    total: number
    limit: number
    offset: number
}

export interface ApiResponse<T> {
    success: boolean
    data: T
    message?: string
    error?: string
}

export interface ApiError {
    code: string
    message: string
    details?: Record<string, unknown>
}