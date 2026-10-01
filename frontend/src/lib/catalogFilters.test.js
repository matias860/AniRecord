import test from 'node:test'
import assert from 'node:assert/strict'
import { filtersFromSearchParams, searchParamsFromFilters } from './catalogFilters.js'

test('invalid anime URL filters fall back to safe defaults', () => {
  const params = new URLSearchParams('year=abc&season=INVALID&sort=INVALID&format=NOVEL&country=JP&status=NOPE')
  params.set('search', 'x'.repeat(200))
  const filters = filtersFromSearchParams(params, 'ANIME')

  assert.equal(filters.year, '')
  assert.equal(filters.season, '')
  assert.equal(filters.sort, 'POPULARITY')
  assert.equal(filters.format, '')
  assert.equal(filters.country, '')
  assert.equal(filters.status, '')
  assert.ok(filters.search.length <= 120)
})

test('manga URL filters preserve valid values and remove anime-only values', () => {
  const params = new URLSearchParams('year=2050&season=WINTER&country=KR&format=NOVEL&status=HIATUS&sort=SCORE&genre=Drama&tag=Time%20Loop')
  const filters = filtersFromSearchParams(params, 'MANGA')

  assert.deepEqual(filters, {
    search: '',
    year: '2050',
    season: '',
    genre: ['Drama'],
    tag: ['Time Loop'],
    country: 'KR',
    format: 'NOVEL',
    status: 'HIATUS',
    sort: 'SCORE',
  })
})

test('serializing normalized filters produces a stable shareable URL', () => {
  const original = new URLSearchParams('tag=Ninja&genre=Action&sort=TRENDING&year=2002')
  const filters = filtersFromSearchParams(original, 'ANIME')
  const serialized = searchParamsFromFilters(filters)

  assert.deepEqual(filtersFromSearchParams(serialized, 'ANIME'), filters)
  assert.equal(serialized.toString(), 'year=2002&genre=Action&tag=Ninja&sort=TRENDING')
})
