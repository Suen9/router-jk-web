import api from './index'

export function getDashboardStats() {
  return api.get('/dashboard/stats')
}
