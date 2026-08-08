/* global process */
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { execFileSync } from 'node:child_process'

const commit = process.env.VERCEL_GIT_COMMIT_SHA
  || process.env.GITHUB_SHA
  || execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim()
const synchronizationPaths = [
  'backend/src/main/resources/db/migration/',
  'database/schema.sql',
  'backend/src/main/java/com/animelist/backend/entity/Media.java',
  'backend/src/main/java/com/animelist/backend/dto/anilist/',
  'backend/src/main/java/com/animelist/backend/dto/library/',
  'backend/src/main/java/com/animelist/backend/repository/LibraryEntryRepository.java',
  'backend/src/main/java/com/animelist/backend/service/AniListClient.java',
  'backend/src/main/java/com/animelist/backend/service/LibraryService.java',
]

function synchronizationRequired(sha) {
  try {
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
export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: {
    'import.meta.env.VITE_APP_COMMIT': JSON.stringify(commit),
    'import.meta.env.VITE_SYNC_HISTORY': JSON.stringify(synchronizationHistory),
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
