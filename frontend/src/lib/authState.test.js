import test from 'node:test'
import assert from 'node:assert/strict'
import authReducer, { fetchAuth, localLogin, registerLocal, sessionExpired } from '../features/auth/authSlice.js'
import apiClient from '../api/apiClient.js'

for (const login of [localLogin, registerLocal]) {
  test(`${login.typePrefix} cannot be overwritten by an earlier status response`, () => {
    let state = authReducer(undefined, fetchAuth.pending('old-status'))
    state = authReducer(state, login.pending('login'))
    state = authReducer(state, login.fulfilled({ authenticated: true, userId: 7 }, 'login'))
    state = authReducer(state, fetchAuth.fulfilled({ authenticated: false }, 'old-status'))
    assert.equal(state.authenticated, true)
    assert.equal(state.userId, 7)
    state = authReducer(state, fetchAuth.rejected(new Error('timeout'), 'old-status'))
    assert.equal(state.status, 'succeeded')
  })
}

test('only the latest status request changes authentication', () => {
  let state = authReducer(undefined, fetchAuth.pending('first'))
  state = authReducer(state, fetchAuth.pending('second'))
  state = authReducer(state, fetchAuth.fulfilled({ authenticated: false }, 'first'))
  assert.equal(state.status, 'loading')
  state = authReducer(state, fetchAuth.fulfilled({ authenticated: true }, 'second'))
  assert.equal(state.authenticated, true)
})

test('expired sessions cannot be revived by an old status response', () => {
  let state = authReducer(undefined, fetchAuth.pending('old-status'))
  state = authReducer(state, sessionExpired())
  state = authReducer(state, fetchAuth.fulfilled({ authenticated: true }, 'old-status'))
  assert.equal(state.authenticated, false)
  assert.equal(state.status, 'succeeded')
})

test('invalid credentials reject normally; protected 401 signals session expiry without navigation', async () => {
  const previousWindow = globalThis.window
  const previousAdapter = apiClient.defaults.adapter
  const events = []
  globalThis.window = { location: { origin: 'https://anirecord.com.ar' }, dispatchEvent: (event) => events.push(event.type) }
  apiClient.defaults.adapter = async (config) => {
    throw { config, response: { status: 401, data: { message: 'Usuario o contraseña incorrectos' } } }
  }
  try {
    for (const endpoint of ['/auth/local-login', '/auth/register', '/auth/claim', '/auth/status']) {
      await assert.rejects(apiClient.post(endpoint), /Usuario o contraseña incorrectos/)
    }
    assert.deepEqual(events, [])
    await assert.rejects(apiClient.get('/library'), /Usuario o contraseña incorrectos/)
    assert.deepEqual(events, ['anirecord:session-expired'])
  } finally {
    globalThis.window = previousWindow
    apiClient.defaults.adapter = previousAdapter
  }
})

test('failed manual login leaves the form available after invalidating a status check', () => {
  let state = authReducer(undefined, fetchAuth.pending('old-status'))
  state = authReducer(state, localLogin.pending('login'))
  state = authReducer(state, localLogin.rejected(null, 'login', undefined, 'Usuario o contraseña incorrectos'))
  state = authReducer(state, fetchAuth.fulfilled({ authenticated: false }, 'old-status'))
  assert.equal(state.status, 'succeeded')
  assert.equal(state.mutationStatus, 'idle')
  assert.equal(state.error, 'Usuario o contraseña incorrectos')
})
