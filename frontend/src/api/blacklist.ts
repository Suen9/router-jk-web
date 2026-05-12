import api from './index'

export function getBlacklist() {
  return api.get('/blacklist/list')
}
export function addBlacklist(data: { mac: string; name: string }) {
  return api.post('/blacklist/add', data)
}
export function deleteBlacklist(data: { macList: string[] }) {
  return api.post('/blacklist/delete', data)
}
