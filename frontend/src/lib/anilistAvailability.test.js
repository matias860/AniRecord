import test from 'node:test'
import assert from 'node:assert/strict'
import { isAniListServiceError } from '../api/anilistAvailability.js'

test('recognizes only backend errors explicitly attributed to AniList', () => {
  assert.equal(isAniListServiceError({
    status: 502,
    response: { data: { validationErrors: { service: 'anilist' } } },
  }), true)
  assert.equal(isAniListServiceError({ status: 502, response: { data: 'Bad Gateway' } }), false)
  assert.equal(isAniListServiceError({ status: 503, response: { data: { validationErrors: { service: 'anilist' } } } }), false)
})
