import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 15000
})

api.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data.code !== 0) {
      console.error('API Error:', data.message)
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data
  },
  (error) => {
    console.error('Network Error:', error.message)
    return Promise.reject(error)
  }
)

export default api
