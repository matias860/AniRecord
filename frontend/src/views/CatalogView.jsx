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
import { fetchLibrary } from '../features/library/librarySlice.js'
import { catalogRequestFilters, emptyCatalogFilters, filtersFromSearchParams, normalizeCatalogFilters, searchParamsFromFilters } from '../lib/catalogFilters.js'
import { useGetCatalogCategoriesQuery, useGetCatalogQuery } from '../api/anirecordApi.js'

const publicationTypes = [{ value: '', label: 'Todos' }, { value: 'JP', label: 'Manga' }, { value: 'KR', label: 'Manhwa' }, { value: 'CN', label: 'Manhua' }]
const seasons = [['WINTER', 'Invierno'], ['SPRING', 'Primavera'], ['SUMMER', 'Verano'], ['FALL', 'Otoño']]
const statuses = [['RELEASING', 'En emisión'], ['FINISHED', 'Finalizado'], ['NOT_YET_RELEASED', 'Próximamente'], ['HIATUS', 'En pausa'], ['CANCELLED', 'Cancelado']]
const maximumCatalogYear = new Date().getFullYear() + 3
const years = Array.from({ length: maximumCatalogYear - 1939 }, (_, index) => maximumCatalogYear - index)
const emptyCatalogItems = []
export default function CatalogView() {
  const { mediaType } = useParams()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const dispatch = useDispatch()
  const libraryItems = useSelector((state) => state.library.items)
  const libraryStatus = useSelector((state) => state.library.status)
  const authenticated = useSelector((state) => state.auth.authenticated)
  const adultContentMode = useSelector((state) => state.profile.adultContentMode)
  const [filtersOpen, setFiltersOpen] = useState(true)
  const type = mediaType?.toLowerCase() === 'anime' ? 'ANIME' : mediaType?.toLowerCase() === 'manga' ? 'MANGA' : null
  const rawSearchParams = searchParams.toString()
  const filters = useMemo(() => filtersFromSearchParams(new URLSearchParams(rawSearchParams), type), [rawSearchParams, type])
  const [searchDraft, setSearchDraft] = useState(filters.search)
  const [requestSearch, setRequestSearch] = useState(filters.search)
  const currentPage = Math.max(1, Number.parseInt(searchParams.get('pagina') || '1', 10) || 1)
  const applicableFilters = useMemo(() => type === 'MANGA'
    ? { ...filters, search: requestSearch, season: '', format: formatsFor(type).some((option) => option.value === filters.format) ? filters.format : '' }
    : { ...filters, search: requestSearch, country: '', format: formatsFor(type).some((option) => option.value === filters.format) ? filters.format : '' }, [filters, requestSearch, type])
  const requestFilters = useMemo(() => ({ ...catalogRequestFilters(applicableFilters), includeAdult: adultContentMode !== 'HIDE' }), [adultContentMode, applicableFilters])
  const catalogQuery = useGetCatalogQuery({ type, page: currentPage, filters: requestFilters }, { skip: !type })
  const categoriesQuery = useGetCatalogCategoriesQuery(undefined, { skip: !type })
  const catalogData = catalogQuery.currentData ?? (catalogQuery.data?.results?.every((item) => item.mediaType === type) ? catalogQuery.data : null)
  const catalogItems = catalogData?.results ?? emptyCatalogItems
  const categories = categoriesQuery.data ?? { genres: [], tags: [] }
  const unavailable = Boolean(catalogQuery.error?.data?.unavailable || categoriesQuery.error?.data?.unavailable)
  const catalogError = catalogQuery.error?.data?.message

  const setFilters = (update) => {
    const nextFilters = typeof update === 'function' ? update(filters) : update
    setSearchParams(searchParamsFromFilters(normalizeCatalogFilters(nextFilters, type)), { replace: true })
  }

  const updateSearchDraft = (value) => {
    const nextFilters = normalizeCatalogFilters({ ...filters, search: value }, type)
    setSearchDraft(nextFilters.search)
    setSearchParams(searchParamsFromFilters(nextFilters), { replace: true })
  }

  useEffect(() => {
    const timer = window.setTimeout(() => setSearchDraft(filters.search), 0)
    return () => window.clearTimeout(timer)
  }, [filters.search, type])

  useEffect(() => {
    const timer = window.setTimeout(() => setRequestSearch(filters.search), 350)
    return () => window.clearTimeout(timer)
  }, [filters.search])

  useEffect(() => {
    if (!type) return
    if (authenticated && libraryStatus === 'idle') dispatch(fetchLibrary())
  }, [authenticated, dispatch, libraryStatus, type])

  const displayedItems = useMemo(() => {
    const query = filters.search.trim().toLocaleLowerCase('es')
    if (!query) return catalogItems
    const localMatches = (authenticated ? libraryItems : []).filter((item) => item.mediaType === type
      && [item.title, item.romajiTitle, item.englishTitle, item.nativeTitle].some((title) => title?.toLocaleLowerCase('es').includes(query))
      && (!filters.year || String(item.releaseYear) === filters.year)
      && (type !== 'ANIME' || !filters.season || item.releaseSeason === filters.season)
      && (!filters.status || item.releaseStatus === filters.status)
      && (!filters.country || item.countryOfOrigin === filters.country)
      && (!filters.format || item.mediaFormat === filters.format)
      && !filters.genre.length
      && !filters.tag.length)
    const localIds = new Set(localMatches.map((item) => item.anilistId))
    return [...localMatches, ...catalogItems.filter((item) => !localIds.has(item.anilistId))]
  }, [authenticated, catalogItems, filters.country, filters.format, filters.genre.length, filters.search, filters.season, filters.status, filters.tag.length, filters.year, libraryItems, type])

  if (mediaType && !type) return <Navigate to="/catalogo" replace />
  if (!type) return <CatalogLanding />
  if (catalogQuery.isLoading && !catalogItems.length) return <PageState fullPage loading />

  const resetFilters = () => {
    setSearchDraft('')
    setRequestSearch('')
    setFilters({ ...emptyCatalogFilters, genre: [], tag: [] })
  }
  const changePage = (page) => {
    const params = searchParamsFromFilters(filters)
    if (page > 1) params.set('pagina', String(page))
    setSearchParams(params)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  const update = (key) => (value) => setFilters((current) => ({ ...current, [key]: value }))

  return (
    <>
      <div className="catalog-heading"><div><Link className="catalog-back" to="/catalogo"><ArrowLeft /> Elegir otro catálogo</Link><span className="eyebrow">Catálogo de {type === 'ANIME' ? 'anime' : 'manga'}</span><h1>{type === 'ANIME' ? 'Anime' : 'Manga'}</h1><p>Explora AniList y encuentra tu próxima historia.</p></div><div className="catalog-heading-actions"><MediaTypeSwitch value={type} onChange={(nextType) => navigateToMediaType(navigate, nextType, { ...filters, search: searchDraft })} ariaLabel="Cambiar tipo de catálogo" />{!unavailable && <button className="filter-toggle" aria-expanded={filtersOpen} aria-controls="catalog-filters" onClick={() => setFiltersOpen((open) => !open)}><SlidersHorizontal /> Filtros</button>}</div></div>

      <AnimatePresence mode="wait" initial={false}><motion.div className="catalog-type-content" key={type} initial={{ opacity: 0, x: type === 'ANIME' ? -18 : 18 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: type === 'ANIME' ? 18 : -18 }} transition={{ duration: .26, ease: 'easeOut' }}>
      {unavailable ? <AniListUnavailable /> : <>
      <form id="catalog-filters" className={`catalog-filters ${filtersOpen ? 'open' : ''}`} data-media-type={type.toLowerCase()} onSubmit={(event) => event.preventDefault()}>
        <CatalogSearch value={searchDraft} onChange={updateSearchDraft} />
        <StyledSelect className="catalog-filter-year" ariaLabel="Año" fieldLabel="Año" value={filters.year} onChange={update('year')} options={[{ value: '', label: 'Todos' }, ...years.map((year) => ({ value: String(year), label: String(year) }))]} />
        {type === 'ANIME' && <StyledSelect className="catalog-filter-season" ariaLabel="Temporada" fieldLabel="Temporada" value={filters.season} onChange={update('season')} options={[{ value: '', label: 'Todas' }, ...seasons.map(([value, label]) => ({ value, label }))]} />}
        <GenreMultiSelect className="catalog-filter-categories" genres={categories.genres} tags={categories.tags} value={{ genre: filters.genre, tag: filters.tag }} onChange={({ genre, tag }) => setFilters((current) => ({ ...current, genre, tag }))} />
        {type === 'MANGA' && <StyledSelect className="catalog-filter-country" ariaLabel="Origen de publicación" fieldLabel="Origen" value={filters.country} onChange={update('country')} options={publicationTypes} />}
        <StyledSelect className="catalog-filter-format" ariaLabel="Formato" fieldLabel="Formato" value={filters.format} onChange={update('format')} options={formatsFor(type)} />
        <StyledSelect className="catalog-filter-status" ariaLabel="Estado de emisión" fieldLabel="Emisión" value={filters.status} onChange={update('status')} options={[{ value: '', label: 'Todos' }, ...statuses.map(([value, label]) => ({ value, label }))]} />
        <StyledSelect className="catalog-filter-sort" ariaLabel="Orden" fieldLabel="Orden" value={filters.sort} onChange={update('sort')} options={[{ value: 'POPULARITY', label: 'Más populares' }, { value: 'SCORE', label: 'Mejor puntuados' }, { value: 'TRENDING', label: 'En tendencia' }, { value: 'NEWEST', label: 'Más recientes' }]} />
        <button className="clear-filters" type="button" onClick={resetFilters}><X /> Limpiar</button>
      </form>

      <PageState error={catalogError} onRetry={catalogQuery.refetch} />
      {catalogQuery.isFetching && catalogData && <div className="refresh-indicator" role="status"><i /> Actualizando resultados…</div>}
      {catalogData && <>{!displayedItems.length ? <PageState empty title="No encontramos coincidencias" description="Probá quitando algún filtro o usando un título más corto." actionLabel="Limpiar filtros" onAction={resetFilters} /> : <HoverMediaGrid className="library-grid">{displayedItems.map((media) => <MediaCard key={media.anilistId} media={media} />)}</HoverMediaGrid>}<nav className="pagination" aria-label="Páginas del catálogo"><button disabled={currentPage === 1} onClick={() => changePage(currentPage - 1)}><ChevronLeft /> Anterior</button><span>Página <b>{currentPage}</b></span><button disabled={!catalogData.hasNextPage} onClick={() => changePage(currentPage + 1)}>Siguiente <ChevronRight /></button></nav></>}
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

function CatalogSearch({ value, onChange }) {
  return <label className="catalog-search" data-field-label="Buscar"><Search /><input aria-label="Buscar títulos en el catálogo" value={value} maxLength={100} onChange={(event) => onChange(event.target.value)} placeholder="Título..." /></label>
}

function AniListUnavailable() {
  return <section className="anilist-unavailable" role="status"><span><WifiOff /></span><small>Servicio externo no disponible</small><h2>El catálogo está temporalmente pausado</h2><p>AniList deshabilitó momentáneamente su API pública. Tu biblioteca y el resto de las funciones locales siguen disponibles.</p><Link className="primary-button" to="/"><House /> Volver al inicio</Link></section>
}

function CatalogLanding() {
  return <motion.section className="catalog-landing" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .3 }}><div className="catalog-landing-title"><span className="eyebrow">¿Qué deseas descubrir?</span><h1>Elige un catálogo</h1><p>Después podrás buscar y filtrar todo el contenido de AniList.</p></div><div className="catalog-choices"><Link to="/catalogo/anime" className="catalog-choice anime-choice"><span><Clapperboard /></span><div><small>Series y películas</small><h2>Anime</h2><p>Temporadas, emisiones, episodios y más.</p></div><ChevronRight /></Link><Link to="/catalogo/manga" className="catalog-choice manga-choice"><span><BookOpen /></span><div><small>Manga y novelas</small><h2>Manga</h2><p>Publicaciones, capítulos, estados y géneros.</p></div><ChevronRight /></Link></div></motion.section>
}
