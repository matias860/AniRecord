/* global process */
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { execFileSync } from 'node:child_process'

const commit = process.env.VERCEL_GIT_COMMIT_SHA
  || process.env.GITHUB_SHA
  || execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim()
let changedFiles = []
try {
  changedFiles = execFileSync('git', ['diff-tree', '--no-commit-id', '--name-only', '-r', commit], { encoding: 'utf8' })
    .trim()
    .split(/\r?\n/)
    .filter(Boolean)
} catch {
  // Some providers use a shallow checkout. The prompt remains optional if the
  // parent commit is unavailable instead of making the deployment fail.
}
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
const synchronizationRecommended = changedFiles.some((file) =>
  synchronizationPaths.some((path) => file === path || file.startsWith(path)))

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: {
    'import.meta.env.VITE_APP_COMMIT': JSON.stringify(commit),
    'import.meta.env.VITE_SYNC_RECOMMENDED': JSON.stringify(synchronizationRecommended),
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
