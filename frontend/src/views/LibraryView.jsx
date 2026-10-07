import { formatsFor } from '../lib/mediaFormats.js'
import { useDeferredValue, useEffect, useMemo, useState } from 'react'
import { Library, Search, X } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { useSearchParams } from 'react-router-dom'
import MediaCard from '../components/media/MediaCard.jsx'
import HoverMediaGrid from '../components/media/HoverMediaGrid.jsx'
import PageState from '../components/common/PageState.jsx'
import StyledSelect from '../components/common/StyledSelect.jsx'
import GenreMultiSelect from '../components/common/GenreMultiSelect.jsx'
import MediaTypeSwitch from '../components/common/MediaTypeSwitch.jsx'
import {
  fetchLibrary,
  incrementProgress,
  resetLibraryFilters,
  setLibraryFilter,
  setLibraryMediaType,
  setLibraryTrackingStatus,
} from '../features/library/librarySlice.js'
import { fetchCatalogCategories } from '../features/catalog/catalogSlice.js'

const statuses = [['ALL', 'Todos'], ['IN_PROGRESS', 'En curso'], ['PLANNED', 'Pendientes'], ['COMPLETED', 'Completados'], ['PAUSED', 'Pausados'], ['DROPPED', 'Abandonados']]
const sectionOrder = ['IN_PROGRESS', 'COMPLETED', 'PLANNED', 'PAUSED', 'DROPPED']
const sectionLabels = {
  IN_PROGRESS: { ANIME: 'Viendo', MANGA: 'Leyendo' },
  COMPLETED: { ANIME: 'Animes completados', MANGA: 'Mangas completados' },
  PLANNED: { ANIME: 'Animes pendientes', MANGA: 'Mangas pendientes' },
  PAUSED: { ANIME: 'Animes pausados', MANGA: 'Mangas pausados' },
  DROPPED: { ANIME: 'Animes abandonados', MANGA: 'Mangas abandonados' },
}
const seasons = [['WINTER', 'Invierno'], ['SPRING', 'Primavera'], ['SUMMER', 'Verano'], ['FALL', 'Otoño']]
const releaseStatuses = [['RELEASING', 'En emisión'], ['FINISHED', 'Finalizado'], ['NOT_YET_RELEASED', 'Próximamente'], ['HIATUS', 'En pausa'], ['CANCELLED', 'Cancelado']]
const publicationTypes = [{ value: '', label: 'Todos' }, { value: 'JP', label: 'Manga' }, { value: 'KR', label: 'Manhwa' }, { value: 'CN', label: 'Manhua' }]
const initialSectionSize = 48
const emptyGenres = []

const byPersonalScore = (a, b) => {
  const aScored = a.personalScore != null
  const bScored = b.personalScore != null
  if (aScored !== bScored) return aScored ? -1 : 1
  if (aScored) {
    const scoreDifference = Number(b.personalScore) - Number(a.personalScore)
    if (scoreDifference) return scoreDifference
  }
  return a.title.localeCompare(b.title, 'es', { sensitivity: 'base' })
}

export default function LibraryView() {
  const dispatch = useDispatch()
  const [searchParams] = useSearchParams()
  const { items, status, error, filters = {} } = useSelector((state) => state.library)
  const catalog = useSelector((state) => state.catalog)
  const mediaType = filters.mediaType === 'MANGA' ? 'MANGA' : 'ANIME'
  const trackingStatus = filters.trackingStatus ?? 'ALL'
  const deferredSearch = useDeferredValue(filters.search ?? '')
  const selectedYear = filters.year ?? ''
  const selectedSeason = filters.season ?? ''
  const selectedGenres = Array.isArray(filters.genre) ? filters.genre : emptyGenres
  const selectedCountry = filters.country ?? ''
  const selectedFormat = filters.format ?? ''
  const selectedReleaseStatus = filters.releaseStatus ?? ''
  const selectedScore = Number(filters.score) || 0
  const [pagination, setPagination] = useState({ key: '', counts: {} })
  const updateFilter = (key) => (value) => dispatch(setLibraryFilter({ key, value }))
  const availableYears = useMemo(() => [...new Set(items
    .filter((item) => item.mediaType === mediaType && item.releaseYear)
    .map((item) => item.releaseYear))].sort((a, b) => b - a), [items, mediaType])

  useEffect(() => { if (status === 'idle') dispatch(fetchLibrary()) }, [dispatch, status])
  useEffect(() => { if (catalog.categoriesStatus === 'idle') dispatch(fetchCatalogCategories()) }, [catalog.categoriesStatus, dispatch])
  useEffect(() => {
    if (filters.format && !formatsFor(mediaType).some((option) => option.value === filters.format)) {
      dispatch(setLibraryFilter({ key: 'format', value: '' }))
    }
  }, [dispatch, filters.format, mediaType])
  useEffect(() => () => { dispatch(setLibraryFilter({ key: 'search', value: '' })) }, [dispatch])
  useEffect(() => {
    const requestedType = searchParams.get('tipo')?.toUpperCase()
    if (requestedType === 'ANIME' || requestedType === 'MANGA') dispatch(setLibraryMediaType(requestedType))
  }, [dispatch, searchParams])

  const preparedItems = useMemo(() => items.map((item) => ({
    item,
    genres: item.genres ? item.genres.split('|') : [],
    tags: item.tags ? item.tags.split('|') : [],
    searchText: `${item.title ?? ''}\n${item.romajiTitle ?? ''}`.toLocaleLowerCase('es'),
  })), [items])

  const filtered = useMemo(() => {
    const search = deferredSearch.trim().toLocaleLowerCase('es')
    return preparedItems.filter(({ item, genres, tags, searchText }) => {
      const score = item.personalScore == null ? null : Number(item.personalScore)
      const scoreMatches = !selectedScore || score === selectedScore
      return item.mediaType === mediaType
        && (trackingStatus === 'ALL' || item.trackingStatus === trackingStatus)
        && (!search || searchText.includes(search))
        && (!selectedYear || String(item.releaseYear) === selectedYear)
        && (!selectedSeason || item.releaseSeason === selectedSeason)
        && (!selectedGenres.length || selectedGenres.every((category) => category === 'Yuri' ? tags.includes(category) : genres.includes(category)))
        && (!selectedCountry || item.countryOfOrigin === selectedCountry)
        && (!selectedFormat || item.mediaFormat === selectedFormat)
        && (!selectedReleaseStatus || item.releaseStatus === selectedReleaseStatus)
        && scoreMatches
    }).map(({ item }) => item)
  }, [deferredSearch, mediaType, preparedItems, selectedCountry, selectedFormat, selectedGenres,
    selectedReleaseStatus, selectedScore, selectedSeason, selectedYear, trackingStatus])

  const sections = useMemo(() => {
    const buckets = Object.fromEntries(sectionOrder.map((sectionStatus) => [sectionStatus, []]))
    for (const item of filtered) buckets[item.trackingStatus]?.push(item)
    return sectionOrder
      .filter((sectionStatus) => trackingStatus === 'ALL' || trackingStatus === sectionStatus)
      .map((sectionStatus) => ({
        status: sectionStatus,
        title: sectionLabels[sectionStatus][mediaType],
        items: buckets[sectionStatus].sort(byPersonalScore),
      }))
      .filter((section) => section.items.length)
  }, [filtered, mediaType, trackingStatus])

  const paginationKey = [deferredSearch, mediaType, selectedCountry, selectedFormat, selectedGenres.join('|'),
    selectedReleaseStatus, selectedScore, selectedSeason, selectedYear, trackingStatus].join('::')
  const visibleCounts = pagination.key === paginationKey ? pagination.counts : {}

  if ((status === 'idle' || status === 'loading') && !items.length) return <PageState fullPage loading />

  return (
    <>
      <div className="page-title">
        <div><span className="eyebrow">Colección personal</span><h1><Library /> Mi biblioteca</h1><p>Sigue tu progreso sin perder el hilo.</p></div>
        <MediaTypeSwitch value={mediaType} onChange={(nextType) => dispatch(setLibraryMediaType(nextType))} ariaLabel="Cambiar tipo de biblioteca" />
      </div>
      <motion.div className="library-advanced-filters" key={mediaType} initial={{ opacity: 0, x: mediaType === 'ANIME' ? -14 : 14 }} animate={{ opacity: 1, x: 0 }} transition={{ duration: .25, ease: 'easeOut' }}>
        <label className="library-search" data-field-label="Buscar"><Search /><input aria-label="Buscar títulos en mi biblioteca" value={filters.search ?? ''} onChange={(event) => updateFilter('search')(event.target.value)} placeholder="Título..." /></label>
        <StyledSelect ariaLabel="Año de la biblioteca" fieldLabel="Año" value={filters.year ?? ''} onChange={updateFilter('year')} options={[{ value: '', label: 'Todos' }, ...availableYears.map((year) => ({ value: String(year), label: String(year) }))]} />
        {mediaType === 'ANIME' && <StyledSelect ariaLabel="Temporada de la biblioteca" fieldLabel="Temporada" value={filters.season ?? ''} onChange={updateFilter('season')} options={[{ value: '', label: 'Todas' }, ...seasons.map(([value, label]) => ({ value, label }))]} />}
        <GenreMultiSelect genres={[...catalog.genres, 'Yuri']} value={filters.genre ?? []} onChange={updateFilter('genre')} />
        {mediaType === 'MANGA' && <StyledSelect ariaLabel="Origen de publicación de la biblioteca" fieldLabel="Origen" value={filters.country ?? ''} onChange={updateFilter('country')} options={publicationTypes} />}
        <StyledSelect ariaLabel="Formato de la biblioteca" fieldLabel="Formato" value={filters.format ?? ''} onChange={updateFilter('format')} options={formatsFor(mediaType)} />
        <StyledSelect ariaLabel="Estado de emisión de la biblioteca" fieldLabel="Emisión" value={filters.releaseStatus ?? ''} onChange={updateFilter('releaseStatus')} options={[{ value: '', label: 'Todas' }, ...releaseStatuses.map(([value, label]) => ({ value, label }))]} />
        <label className="library-score-range"><span>Puntaje <b>{Number(filters.score) ? filters.score : 'Todos'}</b></span><input type="range" min="0" max="10" step="1" value={Number(filters.score) || 0} onChange={(event) => updateFilter('score')(event.target.value === '0' ? '' : event.target.value)} /></label>
        <button className="clear-filters" type="button" onClick={() => dispatch(resetLibraryFilters())}><X /> Limpiar</button>
      </motion.div>
      <div className="filter-row">{statuses.map(([value, label]) => <button key={value} className={trackingStatus === value ? 'active' : ''} onClick={() => dispatch(setLibraryTrackingStatus(value))}>{label}</button>)}</div>
      <PageState loading={status === 'loading'} error={error} onRetry={() => dispatch(fetchLibrary())} empty={status === 'succeeded' && !filtered.length}>No hay títulos para mostrar con estos filtros.</PageState>
      <AnimatePresence mode="wait" initial={false}>
        <motion.div className="library-sections" key={mediaType} initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }} transition={{ duration: .28, ease: 'easeOut' }}>
          <AnimatePresence initial={false} mode="popLayout">
            {sections.map((section, sectionIndex) => <motion.section className="library-status-section" key={section.status} layout initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: .2, ease: 'easeOut', delay: sectionIndex * .035, layout: { type: 'spring', stiffness: 430, damping: 38 } }}>
              <div className="library-section-heading"><h2>{section.title}</h2><span>{section.items.length}</span></div>
              <HoverMediaGrid className="library-grid" animateItems>{section.items.slice(0, visibleCounts[section.status] ?? initialSectionSize).map((media) => <MediaCard key={media.id} media={media} onIncrement={(id) => dispatch(incrementProgress(id))} />)}</HoverMediaGrid>
              {(visibleCounts[section.status] ?? initialSectionSize) < section.items.length && <button className="secondary-button library-show-more" type="button" onClick={() => setPagination((current) => {
                const counts = current.key === paginationKey ? current.counts : {}
                return { key: paginationKey, counts: { ...counts, [section.status]: (counts[section.status] ?? initialSectionSize) + initialSectionSize } }
              })}>Mostrar más</button>}
            </motion.section>)}
          </AnimatePresence>
        </motion.div>
      </AnimatePresence>
    </>
  )
}
