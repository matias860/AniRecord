import { formatsFor } from '../lib/mediaFormats.js'
import { useEffect, useMemo, useState } from 'react'
import { ArrowLeft, BookOpen, ChevronLeft, ChevronRight, Clapperboard, House, Search, SlidersHorizontal, WifiOff, X } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import HoverMediaGrid from '../components/media/HoverMediaGrid.jsx'
import MediaCard from '../components/media/MediaCard.jsx'
import PageState from '../components/common/PageState.jsx'
import StyledSelect from '../components/common/StyledSelect.jsx'
import GenreMultiSelect from '../components/common/GenreMultiSelect.jsx'
import MediaTypeSwitch from '../components/common/MediaTypeSwitch.jsx'
import { fetchCatalog, fetchCatalogCategories } from '../features/catalog/catalogSlice.js'
import { fetchLibrary } from '../features/library/librarySlice.js'
import { catalogRequestFilters, emptyCatalogFilters, filtersFromSearchParams, normalizeCatalogFilters, searchParamsFromFilters } from '../lib/catalogFilters.js'

const publicationTypes = [{ value: '', label: 'Todos' }, { value: 'JP', label: 'Manga' }, { value: 'KR', label: 'Manhwa' }, { value: 'CN', label: 'Manhua' }]
const seasons = [['WINTER', 'Invierno'], ['SPRING', 'Primavera'], ['SUMMER', 'Verano'], ['FALL', 'Otoño']]
const statuses = [['RELEASING', 'En emisión'], ['FINISHED', 'Finalizado'], ['NOT_YET_RELEASED', 'Próximamente'], ['HIATUS', 'En pausa'], ['CANCELLED', 'Cancelado']]
const years = Array.from({ length: 161 }, (_, index) => 2100 - index)
export default function CatalogView() {
  const { mediaType } = useParams()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const dispatch = useDispatch()
  const catalog = useSelector((state) => state.catalog)
  const library = useSelector((state) => state.library)
  const authenticated = useSelector((state) => state.auth.authenticated)
  const [filtersOpen, setFiltersOpen] = useState(true)
  const type = mediaType?.toLowerCase() === 'anime' ? 'ANIME' : mediaType?.toLowerCase() === 'manga' ? 'MANGA' : null
  const rawSearchParams = searchParams.toString()
  const filters = useMemo(() => filtersFromSearchParams(new URLSearchParams(rawSearchParams), type), [rawSearchParams, type])
  const [searchDraft, setSearchDraft] = useState(filters.search)

  const setFilters = (update) => {
    const nextFilters = typeof update === 'function' ? update(filters) : update
    setSearchParams(searchParamsFromFilters(normalizeCatalogFilters(nextFilters, type)), { replace: true })
  }

  const updateSearchDraft = (value) => {
    const nextFilters = normalizeCatalogFilters({ ...filters, search: value }, type)
    setSearchDraft(nextFilters.search)
    replaceCatalogUrl(type, nextFilters)
  }

  useEffect(() => {
    const timer = window.setTimeout(() => setSearchDraft(filters.search), 0)
    return () => window.clearTimeout(timer)
  }, [filters.search, type])

  useEffect(() => {
    if (searchDraft === filters.search) return undefined
    const timer = window.setTimeout(() => {
      const nextFilters = normalizeCatalogFilters({ ...filters, search: searchDraft }, type)
      setSearchParams(searchParamsFromFilters(nextFilters), { replace: true })
    }, 350)
    return () => window.clearTimeout(timer)
  }, [filters, searchDraft, setSearchParams, type])

  useEffect(() => {
    if (!type) return
    if (catalog.categoriesStatus === 'idle') dispatch(fetchCatalogCategories())
    if (authenticated && library.status === 'idle') dispatch(fetchLibrary())
  }, [authenticated, catalog.categoriesStatus, dispatch, library.status, type])

  useEffect(() => {
    if (!type) return
    const applicableFilters = type === 'MANGA'
      ? { ...filters, season: '', format: formatsFor(type).some((option) => option.value === filters.format) ? filters.format : '' }
      : { ...filters, country: '', format: formatsFor(type).some((option) => option.value === filters.format) ? filters.format : '' }
    const request = dispatch(fetchCatalog({ type, page: 1, filters: catalogRequestFilters(applicableFilters) }))
    return () => request.abort()
  }, [dispatch, filters, type])

  const displayedItems = useMemo(() => {
    const query = filters.search.trim().toLocaleLowerCase('es')
    if (!query) return catalog.items
    const localMatches = (authenticated ? library.items : []).filter((item) => item.mediaType === type
      && [item.title, item.romajiTitle].some((title) => title?.toLocaleLowerCase('es').includes(query))
      && (!filters.year || String(item.releaseYear) === filters.year)
      && (type !== 'ANIME' || !filters.season || item.releaseSeason === filters.season)
      && (!filters.status || item.releaseStatus === filters.status)
      && (!filters.country || item.countryOfOrigin === filters.country)
      && (!filters.format || item.mediaFormat === filters.format)
      && !filters.genre.length
      && !filters.tag.length)
    const localIds = new Set(localMatches.map((item) => item.anilistId))
    return [...localMatches, ...catalog.items.filter((item) => !localIds.has(item.anilistId))]
  }, [authenticated, catalog.items, filters.country, filters.format, filters.genre.length, filters.search, filters.season, filters.status, filters.tag.length, filters.year, library.items, type])

  if (mediaType && !type) return <Navigate to="/catalogo" replace />
  if (!type) return <CatalogLanding />
  if ((catalog.status === 'idle' || catalog.status === 'loading') && !catalog.items.length) return <PageState fullPage loading />

  const resetFilters = () => setFilters({ ...emptyCatalogFilters, genre: [], tag: [] })
  const changePage = (page) => { dispatch(fetchCatalog({ type, page, filters: catalog.filters })); window.scrollTo({ top: 0, behavior: 'smooth' }) }
  const update = (key) => (value) => setFilters((current) => ({ ...current, [key]: value }))

  return (
    <>
      <div className="catalog-heading"><div><Link className="catalog-back" to="/catalogo"><ArrowLeft /> Elegir otro catálogo</Link><span className="eyebrow">Catálogo de {type === 'ANIME' ? 'anime' : 'manga'}</span><h1>{type === 'ANIME' ? 'Anime' : 'Manga'}</h1><p>Explora AniList y encuentra tu próxima historia.</p></div><div className="catalog-heading-actions"><MediaTypeSwitch value={type} onChange={(nextType) => navigateToMediaType(navigate, nextType, { ...filters, search: searchDraft })} ariaLabel="Cambiar tipo de catálogo" />{!catalog.unavailable && <button className="filter-toggle" onClick={() => setFiltersOpen((open) => !open)}><SlidersHorizontal /> Filtros</button>}</div></div>

      <AnimatePresence mode="wait" initial={false}><motion.div className="catalog-type-content" key={type} initial={{ opacity: 0, x: type === 'ANIME' ? -18 : 18 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: type === 'ANIME' ? 18 : -18 }} transition={{ duration: .26, ease: 'easeOut' }}>
      {catalog.unavailable ? <AniListUnavailable /> : <>
      <form className={`catalog-filters ${filtersOpen ? 'open' : ''}`} onSubmit={(event) => event.preventDefault()}>
        <CatalogSearch value={searchDraft} onChange={updateSearchDraft} />
        <StyledSelect ariaLabel="Año" fieldLabel="Año" value={filters.year} onChange={update('year')} options={[{ value: '', label: 'Todos' }, ...years.map((year) => ({ value: String(year), label: String(year) }))]} />
        {type === 'ANIME' && <StyledSelect ariaLabel="Temporada" fieldLabel="Temporada" value={filters.season} onChange={update('season')} options={[{ value: '', label: 'Todas' }, ...seasons.map(([value, label]) => ({ value, label }))]} />}
        <GenreMultiSelect genres={catalog.genres} tags={catalog.tags} value={{ genre: filters.genre, tag: filters.tag }} onChange={({ genre, tag }) => setFilters((current) => ({ ...current, genre, tag }))} />
        {type === 'MANGA' && <StyledSelect ariaLabel="Origen de publicación" fieldLabel="Origen" value={filters.country} onChange={update('country')} options={publicationTypes} />}
        <StyledSelect ariaLabel="Formato" fieldLabel="Formato" value={filters.format} onChange={update('format')} options={formatsFor(type)} />
        <StyledSelect ariaLabel="Estado de emisión" fieldLabel="Emisión" value={filters.status} onChange={update('status')} options={[{ value: '', label: 'Todos' }, ...statuses.map(([value, label]) => ({ value, label }))]} />
        <StyledSelect ariaLabel="Orden" fieldLabel="Orden" value={filters.sort} onChange={update('sort')} options={[{ value: 'POPULARITY', label: 'Más populares' }, { value: 'SCORE', label: 'Mejor puntuados' }, { value: 'TRENDING', label: 'En tendencia' }, { value: 'NEWEST', label: 'Más recientes' }]} />
        <button className="clear-filters" type="button" onClick={resetFilters}><X /> Limpiar</button>
      </form>

      <PageState loading={catalog.status === 'loading'} error={catalog.error} />
      {catalog.status === 'succeeded' && <>{!displayedItems.length ? <PageState empty>No encontramos resultados con esos filtros.</PageState> : <HoverMediaGrid className="library-grid">{displayedItems.map((media) => <MediaCard key={media.anilistId} media={media} />)}</HoverMediaGrid>}<nav className="pagination" aria-label="Páginas del catálogo"><button disabled={catalog.currentPage === 1} onClick={() => changePage(catalog.currentPage - 1)}><ChevronLeft /> Anterior</button><span>Página <b>{catalog.currentPage}</b></span><button disabled={!catalog.hasNextPage} onClick={() => changePage(catalog.currentPage + 1)}>Siguiente <ChevronRight /></button></nav></>}
      </>}
      </motion.div></AnimatePresence>
    </>
  )
}

function navigateToMediaType(navigate, nextType, filters) {
  const nextFilters = normalizeCatalogFilters({ ...filters, format: '', country: '', season: '' }, nextType)
  const params = searchParamsFromFilters(nextFilters).toString()
  navigate(`/catalogo/${nextType.toLowerCase()}${params ? `?${params}` : ''}`)
}

function replaceCatalogUrl(type, filters) {
  const params = searchParamsFromFilters(filters).toString()
  const url = `/catalogo/${type.toLowerCase()}${params ? `?${params}` : ''}`
  window.history.replaceState(window.history.state, '', url)
}

function CatalogSearch({ value, onChange }) {
  return <label className="catalog-search" data-field-label="Buscar"><Search /><input value={value} maxLength={100} onChange={(event) => onChange(event.target.value)} placeholder="Título..." /></label>
}

function AniListUnavailable() {
  return <section className="anilist-unavailable" role="status"><span><WifiOff /></span><small>Servicio externo no disponible</small><h2>El catálogo está temporalmente pausado</h2><p>AniList deshabilitó momentáneamente su API pública. Tu biblioteca y el resto de las funciones locales siguen disponibles.</p><Link className="primary-button" to="/"><House /> Volver al inicio</Link></section>
}

function CatalogLanding() {
  return <motion.section className="catalog-landing" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .3 }}><div className="catalog-landing-title"><span className="eyebrow">¿Qué deseas descubrir?</span><h1>Elige un catálogo</h1><p>Después podrás buscar y filtrar todo el contenido de AniList.</p></div><div className="catalog-choices"><Link to="/catalogo/anime" className="catalog-choice anime-choice"><span><Clapperboard /></span><div><small>Series y películas</small><h2>Anime</h2><p>Temporadas, emisiones, episodios y más.</p></div><ChevronRight /></Link><Link to="/catalogo/manga" className="catalog-choice manga-choice"><span><BookOpen /></span><div><small>Manga y novelas</small><h2>Manga</h2><p>Publicaciones, capítulos, estados y géneros.</p></div><ChevronRight /></Link></div></motion.section>
}
