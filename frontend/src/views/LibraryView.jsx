import { formatsFor } from '../lib/mediaFormats.js'
import { useCallback, useDeferredValue, useEffect, useMemo, useState } from 'react'
import { Library, Search, SlidersHorizontal, X } from 'lucide-react'
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
import { useGetCatalogCategoriesQuery } from '../api/anirecordApi.js'

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
  const items = useSelector((state) => state.library.items)
  const status = useSelector((state) => state.library.status)
  const error = useSelector((state) => state.library.error)
  const filters = useSelector((state) => state.library.filters) ?? {}
  const { data: categories = { genres: [], tags: [] } } = useGetCatalogCategoriesQuery()
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
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [pagination, setPagination] = useState({ key: '', counts: {} })
  const handleIncrement = useCallback((id) => dispatch(incrementProgress(id)), [dispatch])
  const updateFilter = (key) => (value) => dispatch(setLibraryFilter({ key, value }))
  const availableYears = useMemo(() => [...new Set(items
    .filter((item) => item.mediaType === mediaType && item.releaseYear)
    .map((item) => item.releaseYear))].sort((a, b) => b - a), [items, mediaType])

  useEffect(() => { if (status === 'idle') dispatch(fetchLibrary()) }, [dispatch, status])
  useEffect(() => {
    if (filters.format && !formatsFor(mediaType).some((option) => option.value === filters.format)) {
      dispatch(setLibraryFilter({ key: 'format', value: '' }))
    }
  }, [dispatch, filters.format, mediaType])
  useEffect(() => {
    if (!filtersOpen) return undefined
    const close = (event) => { if (event.key === 'Escape') setFiltersOpen(false) }
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [filtersOpen])
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
  const activeFilters = useMemo(() => {
    const labels = []
    if (filters.search) labels.push({ key: 'search', label: `“${filters.search}”` })
    if (selectedYear) labels.push({ key: 'year', label: selectedYear })
    if (selectedSeason) labels.push({ key: 'season', label: seasons.find(([value]) => value === selectedSeason)?.[1] || selectedSeason })
    selectedGenres.forEach((genre) => labels.push({ key: 'genre', value: genre, label: genre }))
    if (selectedCountry) labels.push({ key: 'country', label: publicationTypes.find((item) => item.value === selectedCountry)?.label || selectedCountry })
    if (selectedFormat) labels.push({ key: 'format', label: formatsFor(mediaType).find((item) => item.value === selectedFormat)?.label || selectedFormat })
    if (selectedReleaseStatus) labels.push({ key: 'releaseStatus', label: releaseStatuses.find(([value]) => value === selectedReleaseStatus)?.[1] || selectedReleaseStatus })
    if (selectedScore) labels.push({ key: 'score', label: `${selectedScore}/10` })
    return labels
  }, [filters.search, mediaType, selectedCountry, selectedFormat, selectedGenres, selectedReleaseStatus, selectedScore, selectedSeason, selectedYear])
  const removeFilter = ({ key, value }) => updateFilter(key)(key === 'genre' ? selectedGenres.filter((genre) => genre !== value) : '')

  if ((status === 'idle' || status === 'loading') && !items.length) return <PageState fullPage loading />

  return (
    <>
      <div className="page-title">
        <div><span className="eyebrow">Colección personal</span><h1><Library /> Mi biblioteca</h1><p>Sigue tu progreso sin perder el hilo.</p></div>
        <div className="library-heading-actions"><MediaTypeSwitch value={mediaType} onChange={(nextType) => dispatch(setLibraryMediaType(nextType))} ariaLabel="Cambiar tipo de biblioteca" /><button className="library-filter-toggle" type="button" aria-expanded={filtersOpen} aria-controls="library-filters" onClick={() => setFiltersOpen((open) => !open)}><SlidersHorizontal /> Filtros{activeFilters.length ? <span>{activeFilters.length}</span> : null}</button></div>
      </div>
      {filtersOpen && <button className="library-filter-backdrop" type="button" aria-label="Cerrar filtros" onClick={() => setFiltersOpen(false)} />}
      <motion.div id="library-filters" className={`library-advanced-filters${filtersOpen ? ' open' : ''}`} key={mediaType} initial={{ opacity: 0, x: mediaType === 'ANIME' ? -14 : 14 }} animate={{ opacity: 1, x: 0 }} transition={{ duration: .25, ease: 'easeOut' }}>
        <div className="library-filter-sheet-heading"><div><span className="eyebrow">Biblioteca</span><strong>Filtrar resultados</strong></div><button type="button" onClick={() => setFiltersOpen(false)} aria-label="Cerrar filtros"><X /></button></div>
        <label className="library-search" data-field-label="Buscar"><Search /><input aria-label="Buscar títulos en mi biblioteca" value={filters.search ?? ''} onChange={(event) => updateFilter('search')(event.target.value)} placeholder="Título..." /></label>
        <StyledSelect ariaLabel="Año de la biblioteca" fieldLabel="Año" value={filters.year ?? ''} onChange={updateFilter('year')} options={[{ value: '', label: 'Todos' }, ...availableYears.map((year) => ({ value: String(year), label: String(year) }))]} />
        {mediaType === 'ANIME' && <StyledSelect ariaLabel="Temporada de la biblioteca" fieldLabel="Temporada" value={filters.season ?? ''} onChange={updateFilter('season')} options={[{ value: '', label: 'Todas' }, ...seasons.map(([value, label]) => ({ value, label }))]} />}
        <GenreMultiSelect genres={[...categories.genres, 'Yuri']} value={filters.genre ?? []} onChange={updateFilter('genre')} />
        {mediaType === 'MANGA' && <StyledSelect ariaLabel="Origen de publicación de la biblioteca" fieldLabel="Origen" value={filters.country ?? ''} onChange={updateFilter('country')} options={publicationTypes} />}
        <StyledSelect ariaLabel="Formato de la biblioteca" fieldLabel="Formato" value={filters.format ?? ''} onChange={updateFilter('format')} options={formatsFor(mediaType)} />
        <StyledSelect ariaLabel="Estado de emisión de la biblioteca" fieldLabel="Emisión" value={filters.releaseStatus ?? ''} onChange={updateFilter('releaseStatus')} options={[{ value: '', label: 'Todas' }, ...releaseStatuses.map(([value, label]) => ({ value, label }))]} />
        <label className="library-score-range"><span>Puntaje <b>{Number(filters.score) ? filters.score : 'Todos'}</b></span><input type="range" min="0" max="10" step="1" value={Number(filters.score) || 0} onChange={(event) => updateFilter('score')(event.target.value === '0' ? '' : event.target.value)} /></label>
        <button className="clear-filters" type="button" onClick={() => dispatch(resetLibraryFilters())}><X /> Limpiar</button>
      </motion.div>
      <div className="library-filter-summary"><span><b>{filtered.length}</b> {filtered.length === 1 ? 'título' : 'títulos'}</span>{activeFilters.length > 0 && <div className="active-filter-chips" aria-label="Filtros activos">{activeFilters.map((filter) => <button type="button" key={`${filter.key}-${filter.value || filter.label}`} onClick={() => removeFilter(filter)}>{filter.label}<X /></button>)}<button className="clear-all-filter-chip" type="button" onClick={() => dispatch(resetLibraryFilters())}>Limpiar todo</button></div>}</div>
      <div className="filter-row">{statuses.map(([value, label]) => <button key={value} className={trackingStatus === value ? 'active' : ''} onClick={() => dispatch(setLibraryTrackingStatus(value))}>{label}</button>)}</div>
      <PageState error={error} onRetry={() => dispatch(fetchLibrary())} empty={status === 'succeeded' && !filtered.length} title={items.length ? 'No encontramos coincidencias' : 'Tu biblioteca todavía está vacía'} description={items.length ? 'Quitá algún filtro para volver a ver tus títulos.' : 'Explorá el catálogo y guardá lo próximo que quieras ver o leer.'} actionLabel={items.length ? 'Limpiar filtros' : 'Explorar catálogo'} onAction={items.length ? () => dispatch(resetLibraryFilters()) : undefined} actionTo={items.length ? undefined : `/catalogo/${mediaType.toLowerCase()}`} />
      {status === 'loading' && items.length > 0 && <div className="refresh-indicator" role="status"><i /> Actualizando biblioteca…</div>}
      <AnimatePresence mode="wait" initial={false}>
        <motion.div className="library-sections" key={mediaType} initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }} transition={{ duration: .28, ease: 'easeOut' }}>
          <AnimatePresence initial={false} mode="sync">
            {sections.map((section, sectionIndex) => <motion.section className="library-status-section" key={section.status} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: .2, ease: 'easeOut', delay: sectionIndex * .035 }}>
              <div className="library-section-heading"><h2>{section.title}</h2><span>{section.items.length}</span></div>
              <HoverMediaGrid className="library-grid" animateItems>{section.items.slice(0, visibleCounts[section.status] ?? initialSectionSize).map((media) => <MediaCard key={media.id} media={media} onIncrement={handleIncrement} />)}</HoverMediaGrid>
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
