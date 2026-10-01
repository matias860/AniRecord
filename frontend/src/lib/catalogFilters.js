import { formatsFor } from './mediaFormats.js'

export const emptyCatalogFilters = Object.freeze({
  search: '',
  year: '',
  season: '',
  genre: [],
  tag: [],
  country: '',
  format: '',
  status: '',
  sort: 'POPULARITY',
})

const seasons = new Set(['WINTER', 'SPRING', 'SUMMER', 'FALL'])
const statuses = new Set(['RELEASING', 'FINISHED', 'NOT_YET_RELEASED', 'HIATUS', 'CANCELLED'])
const sorts = new Set(['POPULARITY', 'SCORE', 'TRENDING', 'NEWEST'])
const countries = new Set(['JP', 'KR', 'CN'])
const searchLimit = 120
const categoryLimit = 20
const categoryLengthLimit = 80

export function filtersFromSearchParams(searchParams, type) {
  return normalizeCatalogFilters({
    search: searchParams.get('search'),
    year: searchParams.get('year'),
    season: searchParams.get('season'),
    genre: searchParams.getAll('genre'),
    tag: searchParams.getAll('tag'),
    country: searchParams.get('country'),
    format: searchParams.get('format'),
    status: searchParams.get('status'),
    sort: searchParams.get('sort'),
  }, type)
}

export function normalizeCatalogFilters(filters, type) {
  const allowedFormats = new Set(formatsFor(type).map((option) => option.value).filter(Boolean))
  return {
    search: cleanSearch(filters.search),
    year: cleanYear(filters.year),
    season: type === 'ANIME' ? allowedValue(filters.season, seasons) : '',
    genre: cleanCategories(filters.genre),
    tag: cleanCategories(filters.tag),
    country: type === 'MANGA' ? allowedValue(filters.country, countries) : '',
    format: allowedValue(filters.format, allowedFormats),
    status: allowedValue(filters.status, statuses),
    sort: allowedValue(filters.sort, sorts) || emptyCatalogFilters.sort,
  }
}

export function searchParamsFromFilters(filters) {
  const params = new URLSearchParams()
  if (filters.search) params.set('search', filters.search)
  if (filters.year) params.set('year', filters.year)
  if (filters.season) params.set('season', filters.season)
  filters.genre.forEach((value) => params.append('genre', value))
  filters.tag.forEach((value) => params.append('tag', value))
  if (filters.country) params.set('country', filters.country)
  if (filters.format) params.set('format', filters.format)
  if (filters.status) params.set('status', filters.status)
  if (filters.sort && filters.sort !== emptyCatalogFilters.sort) params.set('sort', filters.sort)
  return params
}

function cleanSearch(value) {
  return typeof value === 'string' ? value.trim().slice(0, searchLimit) : ''
}

function cleanYear(value) {
  if (typeof value !== 'string' || !/^\d{4}$/.test(value)) return ''
  const year = Number(value)
  return year >= 1940 && year <= 2100 ? String(year) : ''
}

function allowedValue(value, allowed) {
  return typeof value === 'string' && allowed.has(value) ? value : ''
}

function cleanCategories(values) {
  if (!Array.isArray(values)) return []
  return [...new Set(values
    .filter((value) => typeof value === 'string')
    .map((value) => value.trim().slice(0, categoryLengthLimit))
    .filter(Boolean))].slice(0, categoryLimit)
}
