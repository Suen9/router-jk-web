import api from './index'

export function getPcDeviceList(params?: any) {
  return api.get('/pc/device/list', { params })
}

export function getPcDeviceDetail(id: number) {
  return api.get(`/pc/device/${id}`)
}

export function addPcDevice(data: { mac: string; hostname?: string; remark?: string }) {
  return api.post('/pc/device/add', data)
}

export function updatePcDevice(id: number, data: { hostname?: string; remark?: string }) {
  return api.put(`/pc/device/${id}`, data)
}

export function removePcDevice(id: number) {
  return api.delete(`/pc/device/${id}`)
}

export function sendCommand(deviceId: number, commandType: string, params?: string) {
  return api.post(`/pc/device/${deviceId}/sendCommand`, { commandType, params })
}

export function getPcCommandList(params?: any) {
  return api.get('/pc/command/list', { params })
}

export function getPcCommandDetail(id: number) {
  return api.get(`/pc/command/${id}`)
}

// 弹窗消息模板
export function getMessageTemplates() {
  return api.get('/pc/message-template/list')
}

export function addMessageTemplate(data: { content: string }) {
  return api.post('/pc/message-template/add', data)
}

export function removeMessageTemplate(id: number) {
  return api.delete(`/pc/message-template/${id}`)
}
