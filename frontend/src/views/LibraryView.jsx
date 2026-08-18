import { useEffect, useMemo } from 'react'
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
import { fetchCatalogGenres } from '../features/catalog/catalogSlice.js'

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
const mediaFormats = [{ value: '', label: 'Todos' }, { value: 'MANGA', label: 'Manga' }, { value: 'NOVEL', label: 'Light novel' }, { value: 'ONE_SHOT', label: 'One-shot' }]

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
  const updateFilter = (key) => (value) => dispatch(setLibraryFilter({ key, value }))
  const availableYears = useMemo(() => [...new Set(items
    .filter((item) => item.mediaType === mediaType && item.releaseYear)
    .map((item) => item.releaseYear))].sort((a, b) => b - a), [items, mediaType])

  useEffect(() => { if (status === 'idle') dispatch(fetchLibrary()) }, [dispatch, status])
  useEffect(() => { if (catalog.genresStatus === 'idle') dispatch(fetchCatalogGenres()) }, [catalog.genresStatus, dispatch])
  useEffect(() => {
    if (filters.format && !mediaFormats.some((option) => option.value === filters.format)) {
      dispatch(setLibraryFilter({ key: 'format', value: '' }))
    }
  }, [dispatch, filters.format])
  useEffect(() => () => { dispatch(setLibraryFilter({ key: 'search', value: '' })) }, [dispatch])
  useEffect(() => {
    const requestedType = searchParams.get('tipo')?.toUpperCase()
    if (requestedType === 'ANIME' || requestedType === 'MANGA') dispatch(setLibraryMediaType(requestedType))
  }, [dispatch, searchParams])

  const filtered = useMemo(() => {
    const search = (filters.search ?? '').trim().toLocaleLowerCase('es')
    return items.filter((item) => {
      const score = item.personalScore == null ? null : Number(item.personalScore)
      const selectedScore = Number(filters.score) || 0
      const scoreMatches = !selectedScore || score === selectedScore
      const selectedGenres = Array.isArray(filters.genre) ? filters.genre : []
      const itemGenres = item.genres ? item.genres.split('|') : []
      const itemTags = item.tags ? item.tags.split('|') : []
      return item.mediaType === mediaType
        && (trackingStatus === 'ALL' || item.trackingStatus === trackingStatus)
        && (!search || [item.title, item.romajiTitle].some((title) => title?.toLocaleLowerCase('es').includes(search)))
        && (!filters.year || String(item.releaseYear) === filters.year)
        && (!filters.season || item.releaseSeason === filters.season)
        && (!selectedGenres.length || selectedGenres.every((category) => category === 'Yuri' ? itemTags.includes(category) : itemGenres.includes(category)))
        && (!filters.country || item.countryOfOrigin === filters.country)
        && (!filters.format || item.mediaFormat === filters.format)
        && (!filters.releaseStatus || item.releaseStatus === filters.releaseStatus)
        && scoreMatches
    })
  }, [filters, items, mediaType, trackingStatus])

  const sections = useMemo(() => sectionOrder
    .filter((sectionStatus) => trackingStatus === 'ALL' || trackingStatus === sectionStatus)
    .map((sectionStatus) => ({
      status: sectionStatus,
      title: sectionLabels[sectionStatus][mediaType],
      items: filtered.filter((item) => item.trackingStatus === sectionStatus).sort(byPersonalScore),
    }))
    .filter((section) => section.items.length), [filtered, mediaType, trackingStatus])

  return (
    <>
      <div className="page-title">
        <div><span className="eyebrow">Colección personal</span><h1><Library /> Mi biblioteca</h1><p>Sigue tu progreso sin perder el hilo.</p></div>
        <MediaTypeSwitch value={mediaType} onChange={(nextType) => dispatch(setLibraryMediaType(nextType))} ariaLabel="Cambiar tipo de biblioteca" />
      </div>
      <motion.div className="library-advanced-filters" key={mediaType} initial={{ opacity: 0, x: mediaType === 'ANIME' ? -14 : 14 }} animate={{ opacity: 1, x: 0 }} transition={{ duration: .25, ease: 'easeOut' }}>
        <label className="library-search" data-field-label="Buscar"><Search /><input value={filters.search ?? ''} onChange={(event) => updateFilter('search')(event.target.value)} placeholder="Título..." /></label>
        <StyledSelect ariaLabel="Año de la biblioteca" fieldLabel="Año" value={filters.year ?? ''} onChange={updateFilter('year')} options={[{ value: '', label: 'Todos' }, ...availableYears.map((year) => ({ value: String(year), label: String(year) }))]} />
        {mediaType === 'ANIME' && <StyledSelect ariaLabel="Temporada de la biblioteca" fieldLabel="Temporada" value={filters.season ?? ''} onChange={updateFilter('season')} options={[{ value: '', label: 'Todas' }, ...seasons.map(([value, label]) => ({ value, label }))]} />}
        <GenreMultiSelect genres={[...catalog.genres, 'Yuri']} value={filters.genre ?? []} onChange={updateFilter('genre')} />
        {mediaType === 'MANGA' && <><StyledSelect ariaLabel="Origen de publicación de la biblioteca" fieldLabel="Origen" value={filters.country ?? ''} onChange={updateFilter('country')} options={publicationTypes} /><StyledSelect ariaLabel="Formato de publicación de la biblioteca" fieldLabel="Formato" value={filters.format ?? ''} onChange={updateFilter('format')} options={mediaFormats} /></>}
        <StyledSelect ariaLabel="Estado de emisión de la biblioteca" fieldLabel="Emisión" value={filters.releaseStatus ?? ''} onChange={updateFilter('releaseStatus')} options={[{ value: '', label: 'Todas' }, ...releaseStatuses.map(([value, label]) => ({ value, label }))]} />
        <label className="library-score-range"><span>Puntaje <b>{Number(filters.score) ? filters.score : 'Todos'}</b></span><input type="range" min="0" max="10" step="1" value={Number(filters.score) || 0} onChange={(event) => updateFilter('score')(event.target.value === '0' ? '' : event.target.value)} /></label>
        <button className="clear-filters" type="button" onClick={() => dispatch(resetLibraryFilters())}><X /> Limpiar</button>
      </motion.div>
      <div className="filter-row">{statuses.map(([value, label]) => <button key={value} className={trackingStatus === value ? 'active' : ''} onClick={() => dispatch(setLibraryTrackingStatus(value))}>{label}</button>)}</div>
      <PageState loading={status === 'loading'} error={error} empty={status === 'succeeded' && !filtered.length}>No hay títulos para mostrar con estos filtros.</PageState>
      <AnimatePresence mode="wait" initial={false}>
        <motion.div className="library-sections" key={mediaType} initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }} transition={{ duration: .28, ease: 'easeOut' }}>
          {sections.map((section, sectionIndex) => <motion.section className="library-status-section" key={section.status} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .25, delay: sectionIndex * .045 }}>
            <div className="library-section-heading"><h2>{section.title}</h2><span>{section.items.length}</span></div>
            <HoverMediaGrid className="library-grid">{section.items.map((media) => <MediaCard key={media.id} media={media} onIncrement={(id) => dispatch(incrementProgress(id))} />)}</HoverMediaGrid>
          </motion.section>)}
        </motion.div>
      </AnimatePresence>
    </>
  )
}
