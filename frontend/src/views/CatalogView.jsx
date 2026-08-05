import { useEffect, useState } from 'react'
import { ArrowLeft, BookOpen, ChevronLeft, ChevronRight, Clapperboard, Search, SlidersHorizontal, Sparkles, X } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import { Link, Navigate, useParams } from 'react-router-dom'
import HoverMediaGrid from '../components/media/HoverMediaGrid.jsx'
import MediaCard from '../components/media/MediaCard.jsx'
import PageState from '../components/common/PageState.jsx'
import StyledSelect from '../components/common/StyledSelect.jsx'
import GenreMultiSelect from '../components/common/GenreMultiSelect.jsx'
import { fetchCatalog, fetchCatalogGenres } from '../features/catalog/catalogSlice.js'

const emptyFilters = { search: '', year: '', season: '', genre: [], status: '', sort: 'POPULARITY' }
const seasons = [['WINTER', 'Invierno'], ['SPRING', 'Primavera'], ['SUMMER', 'Verano'], ['FALL', 'Otoño']]
const statuses = [['RELEASING', 'En emisión'], ['FINISHED', 'Finalizado'], ['NOT_YET_RELEASED', 'Próximamente'], ['HIATUS', 'En pausa'], ['CANCELLED', 'Cancelado']]
const years = Array.from({ length: new Date().getFullYear() - 1939 }, (_, index) => new Date().getFullYear() - index)
const requestFilters = (filters) => Object.fromEntries(Object.entries(filters).filter(([key, value]) =>
  Array.isArray(value) ? value.length : value && (key !== 'search' || value.trim().length >= 1)))

export default function CatalogView() {
  const { mediaType } = useParams()
  const dispatch = useDispatch()
  const catalog = useSelector((state) => state.catalog)
  const [filters, setFilters] = useState(emptyFilters)
  const [filtersOpen, setFiltersOpen] = useState(true)
  const type = mediaType?.toLowerCase() === 'anime' ? 'ANIME' : mediaType?.toLowerCase() === 'manga' ? 'MANGA' : null

  useEffect(() => {
    if (!type) return
    if (catalog.genresStatus === 'idle') dispatch(fetchCatalogGenres())
  }, [catalog.genresStatus, dispatch, type])

  useEffect(() => {
    if (!type) return
    let request
    const timer = window.setTimeout(() => {
      request = dispatch(fetchCatalog({ type, page: 1, filters: requestFilters(filters) }))
    }, filters.search.trim() ? 400 : 0)
    return () => { window.clearTimeout(timer); request?.abort() }
  }, [dispatch, filters, type])

  if (mediaType && !type) return <Navigate to="/catalogo" replace />
  if (!type) return <CatalogLanding />

  const resetFilters = () => setFilters({ ...emptyFilters, genre: [] })
  const changePage = (page) => { dispatch(fetchCatalog({ type, page, filters: catalog.filters })); window.scrollTo({ top: 0, behavior: 'smooth' }) }
  const update = (key) => (value) => setFilters((current) => ({ ...current, [key]: value }))

  return (
    <>
      <div className="catalog-heading"><div><Link className="catalog-back" to="/catalogo"><ArrowLeft /> Elegir otro catálogo</Link><span className="eyebrow">Catálogo de {type === 'ANIME' ? 'anime' : 'manga'}</span><h1>{type === 'ANIME' ? 'Anime' : 'Manga'}</h1><p>Explorá AniList y encontrá tu próxima historia.</p></div><button className="filter-toggle" onClick={() => setFiltersOpen((open) => !open)}><SlidersHorizontal /> Filtros</button></div>

      <form className={`catalog-filters ${filtersOpen ? 'open' : ''}`} onSubmit={(event) => event.preventDefault()}>
        <label className="catalog-search"><Search /><input value={filters.search} onChange={(event) => update('search')(event.target.value)} placeholder={`Buscar ${type === 'ANIME' ? 'anime' : 'manga'}...`} /></label>
        <StyledSelect ariaLabel="Año" value={filters.year} onChange={update('year')} options={[{ value: '', label: 'Año' }, ...years.map((year) => ({ value: String(year), label: String(year) }))]} />
        <StyledSelect ariaLabel="Temporada" value={filters.season} onChange={update('season')} options={[{ value: '', label: 'Temporada' }, ...seasons.map(([value, label]) => ({ value, label }))]} />
        <GenreMultiSelect genres={catalog.genres} value={filters.genre} onChange={update('genre')} />
        <StyledSelect ariaLabel="Estado de emisión" value={filters.status} onChange={update('status')} options={[{ value: '', label: 'Estado' }, ...statuses.map(([value, label]) => ({ value, label }))]} />
        <StyledSelect ariaLabel="Orden" value={filters.sort} onChange={update('sort')} options={[{ value: 'POPULARITY', label: 'Más populares' }, { value: 'SCORE', label: 'Mejor puntuados' }, { value: 'TRENDING', label: 'En tendencia' }, { value: 'NEWEST', label: 'Más recientes' }]} />
        <button className="clear-filters" type="button" onClick={resetFilters}><X /> Limpiar</button>
      </form>

      <PageState loading={catalog.status === 'loading'} error={catalog.error} />
      {catalog.status === 'succeeded' && <>{!catalog.items.length ? <PageState empty>No encontramos resultados con esos filtros.</PageState> : <HoverMediaGrid className="library-grid">{catalog.items.map((media) => <MediaCard key={media.anilistId} media={media} />)}</HoverMediaGrid>}<nav className="pagination" aria-label="Páginas del catálogo"><button disabled={catalog.currentPage === 1} onClick={() => changePage(catalog.currentPage - 1)}><ChevronLeft /> Anterior</button><span>Página <b>{catalog.currentPage}</b></span><button disabled={!catalog.hasNextPage} onClick={() => changePage(catalog.currentPage + 1)}>Siguiente <ChevronRight /></button></nav></>}
    </>
  )
}

function CatalogLanding() {
  return <section className="catalog-landing"><div className="catalog-landing-title"><Sparkles /><span className="eyebrow">¿Qué querés descubrir?</span><h1>Elegí un catálogo</h1><p>Después vas a poder buscar y filtrar todo el contenido de AniList.</p></div><div className="catalog-choices"><Link to="/catalogo/anime" className="catalog-choice anime-choice"><span><Clapperboard /></span><div><small>Series y películas</small><h2>Anime</h2><p>Temporadas, emisiones, episodios y más.</p></div><ChevronRight /></Link><Link to="/catalogo/manga" className="catalog-choice manga-choice"><span><BookOpen /></span><div><small>Manga y novelas</small><h2>Manga</h2><p>Publicaciones, capítulos, estados y géneros.</p></div><ChevronRight /></Link></div></section>
}
