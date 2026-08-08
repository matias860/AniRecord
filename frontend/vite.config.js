/* global process */
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { execSync } from 'node:child_process'

const commit = process.env.VERCEL_GIT_COMMIT_SHA
  || process.env.GITHUB_SHA
  || execSync('git rev-parse HEAD', { encoding: 'utf8' }).trim()

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: {
    'import.meta.env.VITE_APP_COMMIT': JSON.stringify(commit),
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
