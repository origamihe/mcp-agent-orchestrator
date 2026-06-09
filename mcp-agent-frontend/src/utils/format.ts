export function formatTimestamp(ts: number): string {
    const date = new Date(ts)
    const pad = (n: number) => n.toString().padStart(2, '0')
    return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function truncateText(text: string, maxLength = 100): string {
    if (text.length <= maxLength) return text
    return text.slice(0, maxLength) + '...'
}