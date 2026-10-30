import assert from 'node:assert/strict'
import test from 'node:test'
import { cleanAniListText, formatRuntime, normalizeMediaId, parseAniListText, watchTimeSummary } from './media.js'

test('normalizes route and API media identifiers to the same numeric cache key', () => {
  assert.equal(normalizeMediaId('123'), 123)
  assert.equal(normalizeMediaId(123), 123)
  assert.equal(normalizeMediaId('12.5'), null)
  assert.equal(normalizeMediaId('invalid'), null)
  assert.equal(normalizeMediaId(0), null)
})

test('preserves character biography spoiler boundaries and links', () => {
  const segments = parseAniListText('Dato público.\n\n~!Secreto sobre [Hero](https://anilist.co/character/12/hero).!~\n\nFinal público.')

  assert.equal(segments.filter((segment) => segment.spoiler).map((segment) => segment.text).join(''), 'Secreto sobre Hero.')
  assert.deepEqual(segments.find((segment) => segment.to), {
    text: 'Hero',
    to: '/personajes/12',
    spoiler: true,
  })
  assert.equal(segments.filter((segment) => !segment.spoiler).map((segment) => segment.text).join(''), 'Dato público.\n\n\n\nFinal público.')
})

test('plain text cleaning keeps its previous behavior for spoiler markup', () => {
  assert.equal(cleanAniListText('Antes ~!secreto!~ después'), 'Antes secreto después')
})

test('formats useful episode, total and remaining watch times', () => {
  const media = { mediaType: 'ANIME', mediaFormat: 'TV', totalUnits: 12, insights: { durationMinutes: 24 } }
  assert.equal(formatRuntime(288), '4 h 48 min')
  assert.deepEqual(watchTimeSummary(media, 7), {
    perUnit: '24 min',
    duration: null,
    total: '4 h 48 min',
    remaining: '2 h',
  })
})

test('treats a movie duration as one complete runtime', () => {
  const movie = { mediaType: 'ANIME', mediaFormat: 'MOVIE', totalUnits: 1, insights: { durationMinutes: 103 } }
  assert.deepEqual(watchTimeSummary(movie), {
    perUnit: null,
    duration: '1 h 43 min',
    total: null,
    remaining: null,
  })
  assert.equal(watchTimeSummary({ mediaType: 'MANGA', insights: { durationMinutes: 20 } }), null)
})
