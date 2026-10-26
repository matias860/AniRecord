import assert from 'node:assert/strict'
import test from 'node:test'
import { cleanAniListText, normalizeMediaId, parseAniListText } from './media.js'

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
