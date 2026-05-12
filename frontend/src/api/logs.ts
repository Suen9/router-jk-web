import api from './index'

export function getLogs(params: Record<string, any>) {
  return api.get('/logs/list', { params })
}
export function updateLogStatus(id: number, data: { status: string; remark: string }) {
  return api.put(`/logs/${id}/status`, data)
}
export function exportLogs() {
  return api.get('/logs/export')
}
