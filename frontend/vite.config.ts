import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Proxy Fusion Cloud requests to bypass CORS
      '/fusion-proxy': {
        target: 'https://cptchuhqy.fusionapps.ocs.oc-test.com',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/fusion-proxy/, ''),
      },
      '/cookie-cutter-proxy': {
        target: 'https://cptcmqzqy.fusionapps.ocs.oc-test.com',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/cookie-cutter-proxy/, ''),
      },
    },
  },
})
