import api from './index'

export function getTerminalList(params: Record<string, any>) {
  return api.get('/terminal/list', { params })
}
export function getTerminalDetail(idx: number) {
  return api.get(`/terminal/detail/${idx}`)
}
export function setAccess(data: { idx: number; internetaccess: number }) {
  return api.post('/terminal/setAccess', data)
}
export function setSpeedLimit(data: Record<string, any>) {
  return api.post('/terminal/setSpeedLimit', data)
}
export function addToBlacklist(data: { mac: string; name: string }) {
  return api.post('/terminal/blacklist', data)
}
