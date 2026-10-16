import test from 'node:test'
import assert from 'node:assert/strict'
import { hasPendingEpisode, isUpcomingPremiere, scheduleDate, shouldShowInCurrentSchedule } from './schedule.js'

test('a premiere that has not aired stays current and is not marked pending', () => {
  const entry = {
    progress: 0,
    releaseStatus: 'NOT_YET_RELEASED',
    releaseStartDate: '2026-10-10',
    airing: { nextAiringEpisode: { episode: 1, airingAt: 1791590400 } },
  }
  const now = new Date('2026-10-03T12:00:00')

  assert.equal(isUpcomingPremiere(entry, now), true)
  assert.equal(hasPendingEpisode(entry, now), false)
})

test('an aired episode from a planned title is marked pending', () => {
  const entry = {
    progress: 0,
    releaseStatus: 'RELEASING',
    releaseStartDate: '2026-10-01',
    airing: { nextAiringEpisode: { episode: 2, airingAt: 1791763200 } },
  }

  assert.equal(isUpcomingPremiere(entry, new Date('2026-10-03T12:00:00')), false)
  assert.equal(hasPendingEpisode(entry, new Date('2026-10-03T12:00:00')), true)
})

test('calendar grouping and premiere label use the same airing date', () => {
  const airingAt = Date.parse('2026-11-19T23:30:00Z') / 1000
  const entry = {
    releaseStartDate: '2026-11-20',
    airing: { nextAiringEpisode: { episode: 1, airingAt } },
  }

  assert.equal(scheduleDate(entry).toISOString(), '2026-11-19T23:30:00.000Z')
  assert.equal(scheduleDate({ releaseStartDate: '2026-11-20' }).getHours(), 12)
})

test('a finished movie disappears from the current schedule even on its listed release date', () => {
  const movie = {
    releaseStatus: 'FINISHED',
    releaseStartDate: '2026-10-16',
    airing: undefined,
  }

  assert.equal(shouldShowInCurrentSchedule(movie, true, new Date('2026-10-16T10:00:00')), false)
})

test('upcoming premieres and series with a next episode remain in the current schedule', () => {
  const premiere = { releaseStatus: 'NOT_YET_RELEASED', releaseStartDate: '2026-10-20' }
  const series = {
    releaseStatus: 'RELEASING',
    airing: { nextAiringEpisode: { episode: 4, airingAt: 1792454400 } },
  }
  const now = new Date('2026-10-16T10:00:00')

  assert.equal(shouldShowInCurrentSchedule(premiere, true, now), true)
  assert.equal(shouldShowInCurrentSchedule(series, true, now), true)
})

test('saved schedule is preserved when live AniList data is unavailable', () => {
  const movie = { releaseStatus: 'FINISHED', releaseStartDate: '2026-10-16' }
  assert.equal(shouldShowInCurrentSchedule(movie, false), true)
})
