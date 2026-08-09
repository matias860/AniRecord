import { useState } from 'react'
import { Search, Sparkles } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import MediaCard from '../components/media/MediaCard.jsx'
import HoverMediaGrid from '../components/media/HoverMediaGrid.jsx'
import PageState from '../components/common/PageState.jsx'
import { searchMedia } from '../features/search/searchSlice.js'

export default function SearchView() {
  const dispatch = useDispatch()
  const search = useSelector((state) => state.search)
  const [query, setQuery] = useState(search.query)
  const [type, setType] = useState(search.type)
  const submit = (event) => { event.preventDefault(); if (query.trim().length >= 2) dispatch(searchMedia({ query: query.trim(), type })) }
  return (
    <>
      <section className="search-hero"><Sparkles /><span className="eyebrow">Catálogo de AniList</span><h1>Encuentra tu próxima historia</h1><p>Busca cualquier anime o manga y agrégalo a tu biblioteca.</p>
        <form className="search-form" onSubmit={submit}><Search /><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Ej. One Piece, Frieren, Berserk..." /><select value={type} onChange={(e) => setType(e.target.value)}><option value="ANIME">Anime</option><option value="MANGA">Manga</option></select><button>Buscar</button></form>
      </section>
      <PageState loading={search.status === 'loading'} error={search.error} empty={search.status === 'idle'}>Escribe al menos dos caracteres para comenzar.</PageState>
      {search.status === 'succeeded' && <section className="content-section"><div className="section-heading"><h2>Resultados para “{search.query}”</h2><span>{search.results.length} títulos</span></div><HoverMediaGrid className="library-grid">{search.results.map((media) => <MediaCard key={media.anilistId} media={media} />)}</HoverMediaGrid></section>}
    </>
  )
}
