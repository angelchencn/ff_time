import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Proxy Fusion Cloud requests to bypass CORS
      '/fusion-proxy': {
        target: 'https://cptclwvqy.fusionapps.ocs.oc-test.com',
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
      '/cne-agent-proxy': {
        target: 'https://cptcneaqy.fusionapps.ocs.oc-test.com',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/cne-agent-proxy/, ''),
      },
      '/silver-resp-proxy': {
        target: 'https://cpdcgktqy-silver-manual.fre.vanity.facptest.com',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/silver-resp-proxy/, ''),
      },
    },
  },
})
