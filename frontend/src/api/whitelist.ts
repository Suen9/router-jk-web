import api from './index'

export function getWhitelist() {
  return api.get('/whitelist/list')
}
export function addWhitelist(data: { hostname: string; mac: string; remark: string }) {
  return api.post('/whitelist/add', data)
}
export function removeWhitelist(id: number) {
  return api.delete(`/whitelist/${id}`)
}
