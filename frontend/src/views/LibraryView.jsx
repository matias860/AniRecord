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
  COMPLETED: { ANIME: 'Anime completado', MANGA: 'Manga completado' },
  PLANNED: { ANIME: 'Anime pendiente', MANGA: 'Manga pendiente' },
  PAUSED: { ANIME: 'Anime pausado', MANGA: 'Manga pausado' },
  DROPPED: { ANIME: 'Anime abandonado', MANGA: 'Manga abandonado' },
}
const seasons = [['WINTER', 'Invierno'], ['SPRING', 'Primavera'], ['SUMMER', 'Verano'], ['FALL', 'Otoño']]
const releaseStatuses = [['RELEASING', 'En emisión'], ['FINISHED', 'Finalizado'], ['NOT_YET_RELEASED', 'Próximamente'], ['HIATUS', 'En pausa'], ['CANCELLED', 'Cancelado']]
const publicationTypes = [{ value: '', label: 'Tipo' }, { value: 'JP', label: 'Manga' }, { value: 'KR', label: 'Manhwa' }, { value: 'CN', label: 'Manhua' }]

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
  useEffect(() => () => { dispatch(setLibraryFilter({ key: 'search', value: '' })) }, [dispatch])
  useEffect(() => {
    const requestedType = searchParams.get('tipo')?.toUpperCase()
    if (requestedType === 'ANIME' || requestedType === 'MANGA') dispatch(setLibraryMediaType(requestedType))
  }, [dispatch, searchParams])

  const filtered = useMemo(() => {
    const search = (filters.search ?? '').trim().toLocaleLowerCase('es')
    return items.filter((item) => {
      const score = item.personalScore == null ? null : Number(item.personalScore)
      const scoreMatches = !filters.score
        || (filters.score === 'SCORED' && score != null)
        || (filters.score === 'UNSCORED' && score == null)
        || (filters.score === '10' && score === 10)
        || (filters.score === '9' && score >= 9)
        || (filters.score === '8' && score >= 8)
      const selectedGenres = Array.isArray(filters.genre) ? filters.genre : []
      const itemGenres = item.genres ? item.genres.split('|') : []
      return item.mediaType === mediaType
        && (trackingStatus === 'ALL' || item.trackingStatus === trackingStatus)
        && (!search || [item.title, item.romajiTitle].some((title) => title?.toLocaleLowerCase('es').includes(search)))
        && (!filters.year || String(item.releaseYear) === filters.year)
        && (!filters.season || item.releaseSeason === filters.season)
        && (!selectedGenres.length || selectedGenres.every((genre) => itemGenres.includes(genre)))
        && (!filters.country || item.countryOfOrigin === filters.country)
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
        <div className="type-toggle">
          <button className={mediaType === 'ANIME' ? 'active' : ''} onClick={() => dispatch(setLibraryMediaType('ANIME'))}>Anime</button>
          <button className={mediaType === 'MANGA' ? 'active' : ''} onClick={() => dispatch(setLibraryMediaType('MANGA'))}>Manga</button>
        </div>
      </div>
      <div className="library-advanced-filters">
        <label className="library-search"><Search /><input value={filters.search ?? ''} onChange={(event) => updateFilter('search')(event.target.value)} placeholder="Buscar en tu biblioteca..." /></label>
        <StyledSelect ariaLabel="Año de la biblioteca" value={filters.year ?? ''} onChange={updateFilter('year')} options={[{ value: '', label: 'Año' }, ...availableYears.map((year) => ({ value: String(year), label: String(year) }))]} />
        {mediaType === 'ANIME' && <StyledSelect ariaLabel="Temporada de la biblioteca" value={filters.season ?? ''} onChange={updateFilter('season')} options={[{ value: '', label: 'Temporada' }, ...seasons.map(([value, label]) => ({ value, label }))]} />}
        <GenreMultiSelect genres={catalog.genres} value={filters.genre ?? []} onChange={updateFilter('genre')} />
        {mediaType === 'MANGA' && <StyledSelect ariaLabel="Tipo de publicación de la biblioteca" value={filters.country ?? ''} onChange={updateFilter('country')} options={publicationTypes} />}
        <StyledSelect ariaLabel="Estado de emisión de la biblioteca" value={filters.releaseStatus ?? ''} onChange={updateFilter('releaseStatus')} options={[{ value: '', label: 'Emisión' }, ...releaseStatuses.map(([value, label]) => ({ value, label }))]} />
        <StyledSelect ariaLabel="Puntaje de la biblioteca" value={filters.score ?? ''} onChange={updateFilter('score')} options={[{ value: '', label: 'Puntaje' }, { value: 'SCORED', label: 'Con puntaje' }, { value: 'UNSCORED', label: 'Sin puntaje' }, { value: '10', label: '10 puntos' }, { value: '9', label: '9 o más' }, { value: '8', label: '8 o más' }]} />
        <button className="clear-filters" type="button" onClick={() => dispatch(resetLibraryFilters())}><X /> Limpiar</button>
      </div>
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
