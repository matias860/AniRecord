import assert from 'node:assert/strict'
import test from 'node:test'
import { adultPresentation, preferredTitle } from './contentPreferences.js'

const media = {
  title: 'Título automático',
  englishTitle: 'English title',
  romajiTitle: 'Romaji title',
  nativeTitle: '原題',
}

test('selects the preferred title and falls back when a variant is unavailable', () => {
  assert.equal(preferredTitle(media, 'ENGLISH'), 'English title')
  assert.equal(preferredTitle(media, 'ROMAJI'), 'Romaji title')
  assert.equal(preferredTitle(media, 'NATIVE'), '原題')
  assert.equal(preferredTitle({ ...media, englishTitle: null }, 'ENGLISH'), 'Título automático')
})

test('hides new adult works but keeps saved entries reachable through a blur', () => {
  assert.equal(adultPresentation({ adultContent: true }, 'HIDE'), 'hide')
  assert.equal(adultPresentation({ adultContent: true, trackingStatus: 'PLANNED' }, 'HIDE'), 'blur')
  assert.equal(adultPresentation({ adultContent: true }, 'BLUR'), 'blur')
  assert.equal(adultPresentation({ adultContent: true }, 'SHOW'), 'show')
  assert.equal(adultPresentation({ adultContent: false }, 'HIDE'), 'show')
})
