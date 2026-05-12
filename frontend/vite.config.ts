import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  base: "/router",
  server: {
    port: 8888,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:9070',
        changeOrigin: true
      }
    },
    host: '0.0.0.0'
  }
})
