import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Necessaire pour que le telephone (QR code multi-appareils) puisse
    // atteindre ce serveur via l'IP locale du PC, pas seulement localhost.
    host: true,
    proxy: {
      // Le navigateur appelle /api/... en relatif ; Vite le redirige vers le
      // backend Spring Boot. Ca evite de configurer le CORS pour le developpement.
      '/api': 'http://localhost:8080',
    },
  },
})
