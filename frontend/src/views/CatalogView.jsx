import { useEffect, useMemo, useState } from 'react'
import { ArrowLeft, BookOpen, ChevronLeft, ChevronRight, Clapperboard, House, Search, SlidersHorizontal, Sparkles, WifiOff, X } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import { Link, Navigate, useParams } from 'react-router-dom'
import HoverMediaGrid from '../components/media/HoverMediaGrid.jsx'
import MediaCard from '../components/media/MediaCard.jsx'
import PageState from '../components/common/PageState.jsx'
import StyledSelect from '../components/common/StyledSelect.jsx'
import GenreMultiSelect from '../components/common/GenreMultiSelect.jsx'
import { fetchCatalog, fetchCatalogGenres } from '../features/catalog/catalogSlice.js'
import { fetchLibrary } from '../features/library/librarySlice.js'

const emptyFilters = { search: '', year: '', season: '', genre: [], country: '', format: '', status: '', sort: 'POPULARITY' }
const publicationTypes = [{ value: '', label: 'Todos' }, { value: 'JP', label: 'Manga' }, { value: 'KR', label: 'Manhwa' }, { value: 'CN', label: 'Manhua' }]
const mediaFormats = [{ value: '', label: 'Todos' }, { value: 'MANGA', label: 'Manga' }, { value: 'NOVEL', label: 'Light novel' }, { value: 'ONE_SHOT', label: 'One-shot' }]
const seasons = [['WINTER', 'Invierno'], ['SPRING', 'Primavera'], ['SUMMER', 'Verano'], ['FALL', 'Otoño']]
const statuses = [['RELEASING', 'En emisión'], ['FINISHED', 'Finalizado'], ['NOT_YET_RELEASED', 'Próximamente'], ['HIATUS', 'En pausa'], ['CANCELLED', 'Cancelado']]
const years = Array.from({ length: new Date().getFullYear() - 1939 }, (_, index) => new Date().getFullYear() - index)
const requestFilters = (filters) => {
  const active = Object.fromEntries(Object.entries(filters).filter(([key, value]) =>
    Array.isArray(value) ? value.length : value && (key !== 'search' || value.trim().length >= 1)))
  if (active.genre?.includes('Yuri')) {
    active.genre = active.genre.filter((category) => category !== 'Yuri')
    active.tag = ['Yuri']
    if (!active.genre.length) delete active.genre
  }
  return active
}

export default function CatalogView() {
  const { mediaType } = useParams()
  const dispatch = useDispatch()
  const catalog = useSelector((state) => state.catalog)
  const library = useSelector((state) => state.library)
  const authenticated = useSelector((state) => state.auth.authenticated)
  const [filters, setFilters] = useState(emptyFilters)
  const [filtersOpen, setFiltersOpen] = useState(true)
  const type = mediaType?.toLowerCase() === 'anime' ? 'ANIME' : mediaType?.toLowerCase() === 'manga' ? 'MANGA' : null

  useEffect(() => {
    if (!type) return
    if (catalog.genresStatus === 'idle') dispatch(fetchCatalogGenres())
    if (authenticated && library.status === 'idle') dispatch(fetchLibrary())
  }, [authenticated, catalog.genresStatus, dispatch, library.status, type])

  useEffect(() => {
    if (!type) return
    let request
    const timer = window.setTimeout(() => {
      const applicableFilters = type === 'MANGA' ? { ...filters, season: '' } : filters
      request = dispatch(fetchCatalog({ type, page: 1, filters: requestFilters(applicableFilters) }))
    }, filters.search.trim() ? 400 : 0)
    return () => { window.clearTimeout(timer); request?.abort() }
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
      && !filters.genre.length)
    const localIds = new Set(localMatches.map((item) => item.anilistId))
    return [...localMatches, ...catalog.items.filter((item) => !localIds.has(item.anilistId))]
  }, [authenticated, catalog.items, filters.country, filters.format, filters.genre.length, filters.search, filters.season, filters.status, filters.year, library.items, type])

  if (mediaType && !type) return <Navigate to="/catalogo" replace />
  if (!type) return <CatalogLanding />

  const resetFilters = () => setFilters({ ...emptyFilters, genre: [] })
  const changePage = (page) => { dispatch(fetchCatalog({ type, page, filters: catalog.filters })); window.scrollTo({ top: 0, behavior: 'smooth' }) }
  const update = (key) => (value) => setFilters((current) => ({ ...current, [key]: value }))

  return (
    <>
      <div className="catalog-heading"><div><Link className="catalog-back" to="/catalogo"><ArrowLeft /> Elegir otro catálogo</Link><span className="eyebrow">Catálogo de {type === 'ANIME' ? 'anime' : 'manga'}</span><h1>{type === 'ANIME' ? 'Anime' : 'Manga'}</h1><p>Explora AniList y encuentra tu próxima historia.</p></div>{!catalog.unavailable && <button className="filter-toggle" onClick={() => setFiltersOpen((open) => !open)}><SlidersHorizontal /> Filtros</button>}</div>

      {catalog.unavailable ? <AniListUnavailable /> : <>
      <form className={`catalog-filters ${filtersOpen ? 'open' : ''}`} onSubmit={(event) => event.preventDefault()}>
        <label className="catalog-search" data-field-label="Buscar"><Search /><input value={filters.search} onChange={(event) => update('search')(event.target.value)} placeholder={type === 'ANIME' ? 'Anime...' : 'Manga...'} /></label>
        <StyledSelect ariaLabel="Año" fieldLabel="Año" value={filters.year} onChange={update('year')} options={[{ value: '', label: 'Todos' }, ...years.map((year) => ({ value: String(year), label: String(year) }))]} />
        {type === 'ANIME' && <StyledSelect ariaLabel="Temporada" fieldLabel="Temporada" value={filters.season} onChange={update('season')} options={[{ value: '', label: 'Todas' }, ...seasons.map(([value, label]) => ({ value, label }))]} />}
        <GenreMultiSelect genres={[...catalog.genres, 'Yuri']} value={filters.genre} onChange={update('genre')} />
        {type === 'MANGA' && <><StyledSelect ariaLabel="Origen de publicación" fieldLabel="Origen" value={filters.country} onChange={update('country')} options={publicationTypes} /><StyledSelect ariaLabel="Formato de publicación" fieldLabel="Formato" value={filters.format} onChange={update('format')} options={mediaFormats} /></>}
        <StyledSelect ariaLabel="Estado de emisión" fieldLabel="Estado" value={filters.status} onChange={update('status')} options={[{ value: '', label: 'Todos' }, ...statuses.map(([value, label]) => ({ value, label }))]} />
        <StyledSelect ariaLabel="Orden" fieldLabel="Orden" value={filters.sort} onChange={update('sort')} options={[{ value: 'POPULARITY', label: 'Más populares' }, { value: 'SCORE', label: 'Mejor puntuados' }, { value: 'TRENDING', label: 'En tendencia' }, { value: 'NEWEST', label: 'Más recientes' }]} />
        <button className="clear-filters" type="button" onClick={resetFilters}><X /> Limpiar</button>
      </form>

      <PageState loading={catalog.status === 'loading'} error={catalog.error} />
      {catalog.status === 'succeeded' && <>{!displayedItems.length ? <PageState empty>No encontramos resultados con esos filtros.</PageState> : <HoverMediaGrid className="library-grid">{displayedItems.map((media) => <MediaCard key={media.anilistId} media={media} />)}</HoverMediaGrid>}<nav className="pagination" aria-label="Páginas del catálogo"><button disabled={catalog.currentPage === 1} onClick={() => changePage(catalog.currentPage - 1)}><ChevronLeft /> Anterior</button><span>Página <b>{catalog.currentPage}</b></span><button disabled={!catalog.hasNextPage} onClick={() => changePage(catalog.currentPage + 1)}>Siguiente <ChevronRight /></button></nav></>}
      </>}
    </>
  )
}

function AniListUnavailable() {
  return <section className="anilist-unavailable" role="status"><span><WifiOff /></span><small>Servicio externo no disponible</small><h2>El catálogo está temporalmente pausado</h2><p>AniList deshabilitó momentáneamente su API pública. Tu biblioteca y el resto de las funciones locales siguen disponibles.</p><Link className="primary-button" to="/"><House /> Volver al inicio</Link></section>
}

function CatalogLanding() {
  return <section className="catalog-landing"><div className="catalog-landing-title"><Sparkles /><span className="eyebrow">¿Qué deseas descubrir?</span><h1>Elige un catálogo</h1><p>Después podrás buscar y filtrar todo el contenido de AniList.</p></div><div className="catalog-choices"><Link to="/catalogo/anime" className="catalog-choice anime-choice"><span><Clapperboard /></span><div><small>Series y películas</small><h2>Anime</h2><p>Temporadas, emisiones, episodios y más.</p></div><ChevronRight /></Link><Link to="/catalogo/manga" className="catalog-choice manga-choice"><span><BookOpen /></span><div><small>Manga y novelas</small><h2>Manga</h2><p>Publicaciones, capítulos, estados y géneros.</p></div><ChevronRight /></Link></div></section>
}
