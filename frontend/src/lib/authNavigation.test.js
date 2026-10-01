import test from 'node:test'
import assert from 'node:assert/strict'
import { oauthLoginUrl } from './authNavigation.js'

test('local development starts AniList OAuth through the local API proxy', () => {
  assert.equal(oauthLoginUrl('localhost'), '/api/auth/login')
  assert.equal(oauthLoginUrl('127.0.0.1'), '/api/auth/login')
})

test('production starts AniList OAuth on the callback domain', () => {
  assert.equal(
    oauthLoginUrl('anirecord.com.ar'),
    'https://p01--anime-list--244mm2wyvsvc.code.run/api/auth/login',
  )
})
