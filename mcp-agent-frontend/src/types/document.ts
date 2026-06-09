export interface DocumentGenerationParams {
    title: string
    content: string
    format: 'ppt' | 'docx' | 'pdf'
    style?: string
}

export interface DocumentGenerationResult {
    success: boolean
    downloadUrl: string
    message?: string
}