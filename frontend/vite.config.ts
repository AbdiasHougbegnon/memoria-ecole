import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      // Le navigateur appelle /api/... en relatif ; Vite le redirige vers le
      // backend Spring Boot. Ca evite de configurer le CORS pour le developpement.
      '/api': 'http://localhost:8080',
    },
  },
})
