import type { LogEntry, FileLogEntry } from '@/types/log'

function downloadBlob(content: string, filename: string, mimeType: string) {
    const blob = new Blob(['\uFEFF' + content], { type: mimeType })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
}

export function exportLogsAsJSON(logs: LogEntry[], filename?: string) {
    const name = filename || `logs-${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.json`
    const content = JSON.stringify(logs, null, 2)
    downloadBlob(content, name, 'application/json')
}

export function exportLogsAsCSV(logs: LogEntry[], filename?: string) {
    const name = filename || `logs-${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.csv`
    const headers = ['id', 'level', 'module', 'message', 'agentId', 'sessionId', 'runId', 'timestamp']
    const rows = logs.map(log =>
        headers.map(h => {
            const val = (log as any)[h]
            if (val === null || val === undefined) return ''
            const str = String(val)
            return str.includes(',') || str.includes('"') || str.includes('\n')
                ? `"${str.replace(/"/g, '""')}"`
                : str
        })
    )
    const content = [headers.join(','), ...rows.map(r => r.join(','))].join('\n')
    downloadBlob(content, name, 'text/csv')
}

export function exportFileLogsAsJSON(entries: FileLogEntry[], filename?: string) {
    const name = filename || `file-logs-${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.json`
    const content = JSON.stringify(entries, null, 2)
    downloadBlob(content, name, 'application/json')
}

export function exportFileLogsAsCSV(entries: FileLogEntry[], filename?: string) {
    const name = filename || `file-logs-${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.csv`
    const headers = ['timestamp', 'level', 'thread', 'logger', 'message']
    const rows = entries.map(e =>
        headers.map(h => {
            const val = (e as any)[h]
            if (val === null || val === undefined) return ''
            const str = String(val)
            return str.includes(',') || str.includes('"') || str.includes('\n')
                ? `"${str.replace(/"/g, '""')}"`
                : str
        })
    )
    const content = [headers.join(','), ...rows.map(r => r.join(','))].join('\n')
    downloadBlob(content, name, 'text/csv')
}