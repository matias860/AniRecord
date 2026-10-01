import test from 'node:test'
import assert from 'node:assert/strict'
import { oauthLoginUrl } from './authNavigation.js'

test('local development starts AniList OAuth through Vite on every hostname', () => {
  assert.equal(oauthLoginUrl({ DEV: true }), '/api/auth/login')
})

test('production starts AniList OAuth on the callback domain', () => {
  assert.equal(
    oauthLoginUrl({ DEV: false }),
    'https://p01--anime-list--244mm2wyvsvc.code.run/api/auth/login',
  )
})

test('an explicit OAuth origin supports previews and dedicated environments', () => {
  assert.equal(
    oauthLoginUrl({ DEV: false, VITE_OAUTH_ORIGIN: ' https://preview-api.example.com/ ' }),
    'https://preview-api.example.com/api/auth/login',
  )
})
