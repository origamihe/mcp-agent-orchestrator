import { ref, type Ref } from 'vue'

interface ToastEntry {
    id: string
    type: 'success' | 'error' | 'warning' | 'info'
    message: string
    title?: string
    duration: number
}

let toastContainerRef: any = null

export function registerToastContainer(ref: any) {
    toastContainerRef = ref
}

export interface ToastApi {
    success: (message: string, title?: string) => void
    error: (message: string, title?: string) => void
    warning: (message: string, title?: string) => void
    info: (message: string, title?: string) => void
}

export function useToast(): ToastApi {
    return {
        success: (message: string, title?: string) => {
            toastContainerRef?.success(message, title)
        },
        error: (message: string, title?: string) => {
            toastContainerRef?.error(message, title)
        },
        warning: (message: string, title?: string) => {
            toastContainerRef?.warning(message, title)
        },
        info: (message: string, title?: string) => {
            toastContainerRef?.info(message, title)
        },
    }
}