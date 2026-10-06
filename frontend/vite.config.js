/* global process */
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'
import { execFileSync } from 'node:child_process'

const commit = process.env.VERCEL_GIT_COMMIT_SHA
  || process.env.GITHUB_SHA
  || execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim()
const synchronizationPaths = [
  'backend/src/main/resources/db/migration/',
  'database/schema.sql',
  'database/sync-version.txt',
  'backend/src/main/java/com/animelist/backend/entity/Media.java',
  'backend/src/main/java/com/animelist/backend/dto/anilist/',
  'backend/src/main/java/com/animelist/backend/dto/library/',
  'backend/src/main/java/com/animelist/backend/service/AniListClient.java',
]

function synchronizationRequired(sha) {
  try {
    const subject = execFileSync('git', ['show', '-s', '--format=%s', sha], { encoding: 'utf8' }).trim()
    if (subject.includes('[no-sync]')) return false
    const files = execFileSync('git', ['diff-tree', '--no-commit-id', '--name-only', '-r', sha], { encoding: 'utf8' })
      .trim()
      .split(/\r?\n/)
      .filter(Boolean)
    return files.some((file) => synchronizationPaths.some((path) => file === path || file.startsWith(path)))
  } catch {
    return false
  }
}

let recentCommits = [commit]
try {
  recentCommits = execFileSync('git', ['rev-list', '--max-count=40', commit], { encoding: 'utf8' })
    .trim()
    .split(/\r?\n/)
    .filter(Boolean)
} catch {
  // A shallow deployment still includes the current commit as a safe fallback.
}
const synchronizationHistory = recentCommits.map((sha) => ({
  commit: sha,
  recommended: synchronizationRequired(sha),
}))

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [react(), tailwindcss(), VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.js',
      registerType: 'prompt',
      injectManifest: {
        globPatterns: ['**/*.{js,css,html,png,svg,woff2}'],
        maximumFileSizeToCacheInBytes: 4 * 1024 * 1024,
      },
      manifest: {
        id: '/',
        name: 'AniRecord',
        short_name: 'AniRecord',
        description: 'Tu biblioteca personal de anime y manga.',
        lang: 'es-AR',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        background_color: '#080a12',
        theme_color: '#1a172c',
        categories: ['entertainment', 'lifestyle'],
        related_applications: [{ platform: 'webapp', url: '/manifest.webmanifest', id: '/' }],
        prefer_related_applications: false,
        icons: [
          { src: '/pwa-192x192.png?v=2', sizes: '192x192', type: 'image/png', purpose: 'any maskable' },
          { src: '/pwa-512x512.png?v=2', sizes: '512x512', type: 'image/png', purpose: 'any maskable' },
        ],
      },
      devOptions: { enabled: true, type: 'module' },
    })],
    define: {
      'import.meta.env.VITE_APP_COMMIT': JSON.stringify(commit),
      'import.meta.env.VITE_SYNC_HISTORY': JSON.stringify(synchronizationHistory),
    },
    server: {
      proxy: {
        '/api': apiProxyTarget,
      },
    },
  }
})
