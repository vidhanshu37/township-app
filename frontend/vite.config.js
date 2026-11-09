import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the browser talks to Vite, and Vite forwards /api to the Spring Boot backend.
// That keeps the app on one origin, so no CORS setup is needed.
const target = process.env.VITE_API_TARGET || 'http://localhost:8085';
const proxy = { '/api': { target, changeOrigin: true } };

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy },
  preview: { port: 4173, proxy },
});
