import assert from 'node:assert/strict'
import test from 'node:test'
import { cleanAniListText, parseAniListText } from './media.js'

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
