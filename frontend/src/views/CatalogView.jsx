import { useEffect } from 'react'
import { BookOpen, ChevronLeft, ChevronRight, Clapperboard } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import HoverMediaGrid from '../components/media/HoverMediaGrid.jsx'
import MediaCard from '../components/media/MediaCard.jsx'
import PageState from '../components/common/PageState.jsx'
import { fetchCatalog, setCatalogType } from '../features/catalog/catalogSlice.js'

export default function CatalogView() {
  const dispatch = useDispatch()
  const catalog = useSelector((state) => state.catalog)
  useEffect(() => { if (catalog.status === 'idle') dispatch(fetchCatalog({ type: catalog.type })) }, [catalog.status, catalog.type, dispatch])
  const changeType = (type) => { dispatch(setCatalogType(type)); dispatch(fetchCatalog({ type, page: 1 })) }
  const changePage = (page) => { dispatch(fetchCatalog({ type: catalog.type, page })); window.scrollTo({ top: 0, behavior: 'smooth' }) }
  return (
    <>
      <div className="catalog-heading"><div><span className="eyebrow">Explorar AniList</span><h1>Catálogo</h1><p>Descubrí series ordenadas por popularidad.</p></div>
        <div className="catalog-type-switch"><button className={catalog.type === 'ANIME' ? 'active' : ''} onClick={() => changeType('ANIME')}><Clapperboard /> Anime</button><button className={catalog.type === 'MANGA' ? 'active' : ''} onClick={() => changeType('MANGA')}><BookOpen /> Manga</button></div>
      </div>
      <PageState loading={catalog.status === 'loading'} error={catalog.error} />
      {catalog.status === 'succeeded' && <><HoverMediaGrid className="library-grid">{catalog.items.map((media) => <MediaCard key={media.anilistId} media={media} />)}</HoverMediaGrid><nav className="pagination" aria-label="Páginas del catálogo"><button disabled={catalog.currentPage === 1} onClick={() => changePage(catalog.currentPage - 1)}><ChevronLeft /> Anterior</button><span>Página <b>{catalog.currentPage}</b></span><button disabled={!catalog.hasNextPage} onClick={() => changePage(catalog.currentPage + 1)}>Siguiente <ChevronRight /></button></nav></>}
    </>
  )
}
