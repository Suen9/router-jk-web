import api from './index'

export function getSupervisionList() {
  return api.get('/supervision/list')
}
export function addSupervision(data: Record<string, any>) {
  return api.post('/supervision/add', data)
}
export function updateSupervisionRule(id: number, data: Record<string, any>) {
  return api.put(`/supervision/rule/${id}`, data)
}
export function extendSupervision(id: number, minutes: number) {
  return api.post(`/supervision/extend/${id}`, { minutes })
}
export function removeSupervision(id: number) {
  return api.delete(`/supervision/${id}`)
}
