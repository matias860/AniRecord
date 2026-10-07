import { useEffect, useState } from 'react'
import { ArrowLeft, ChevronLeft, ChevronRight, Heart, Mic2 } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import PageState from '../components/common/PageState.jsx'
import { fetchCharacter } from '../features/people/peopleSlice.js'
import { addPersonFavorite, fetchPersonFavorites, removePersonFavorite } from '../features/favorites/favoritesSlice.js'
import { parseAniListText } from '../lib/media.js'
import { requestLogin } from '../lib/authNavigation.js'

export default function CharacterDetailView() {
  const { anilistId } = useParams()
  const dispatch = useDispatch()
  const bucket = useSelector((state) => state.people.character)
  const person = bucket.cache[anilistId]
  const favorites = useSelector((state) => state.favorites)
  const authenticated = useSelector((state) => state.auth.authenticated)
  useEffect(() => { dispatch(fetchCharacter(anilistId)) }, [anilistId, dispatch])
  useEffect(() => { if (authenticated) dispatch(fetchPersonFavorites()) }, [authenticated, dispatch])
  if (!person) return <PageState fullPage loading={bucket.loadingId === anilistId || !bucket.error} error={bucket.error} onRetry={() => dispatch(fetchCharacter(anilistId))} />
  return <PersonPage person={person} personType="CHARACTER" favorites={favorites} authenticated={authenticated} eyebrow="Personaje" facts={[person.gender, person.age && `${person.age} años`, person.birthday && `Nacimiento: ${person.birthday}`, person.bloodType && `Grupo sanguíneo: ${person.bloodType}`]}>
    {!!person.voiceActors?.length && <section className="person-section"><span className="eyebrow"><Mic2 /> Actuación de voz</span><h2>Seiyūs</h2><div className="person-card-grid">{person.voiceActors.map((actor) => <Link className="person-card" key={actor.anilistId} to={`/seiyuus/${actor.anilistId}`}><img src={actor.image} alt={actor.name} loading="lazy" decoding="async" /><span><strong>{actor.name}</strong><small>{actor.nativeName || actor.language}</small></span></Link>)}</div></section>}
    <MediaConnections items={person.media} />
  </PersonPage>
}

export function PersonPage({ person, personType, favorites, authenticated, eyebrow, facts, favoriteEnabled = true, children }) {
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const favorite = favorites.people.some((item) => item.anilistId === person.anilistId && item.personType === personType)
  const goBack = () => navigate(location.key === 'default' ? '/' : -1)
  const toggleFavorite = () => {
    if (!authenticated) return requestLogin(navigate, location)
    return favorite
      ? dispatch(removePersonFavorite({ anilistId: person.anilistId, personType }))
      : dispatch(addPersonFavorite({ anilistId: person.anilistId, personType, name: person.name, nativeName: person.nativeName, imageUrl: person.image }))
  }
  return <article className="person-detail"><button className="person-back-link" type="button" onClick={goBack}><ArrowLeft /> Volver</button><header className="person-hero"><img src={person.image} alt={person.name} decoding="async" /><div><span className="eyebrow">{eyebrow}</span><h1>{person.name}</h1>{person.nativeName && <p className="person-native">{person.nativeName}</p>}<div className="person-facts">{facts.filter(Boolean).map((fact) => <span key={fact}>{fact}</span>)}{favoriteEnabled && person.favourites != null && <span><Heart /> {person.favourites.toLocaleString('es-ES')} favoritos en AniList</span>}</div>{favoriteEnabled && <button className={`person-favorite-button ${favorite ? 'active' : ''}`} disabled={favorites.mutationStatus === 'loading'} onClick={toggleFavorite}><Heart /> {favorite ? 'Quitar de favoritos' : 'Agregar a favoritos'}</button>}</div></header>{person.description && <section className="person-biography"><span className="eyebrow">Biografía</span><p>{renderAniListText(person.description)}</p></section>}<div className="person-connections">{children}</div></article>
}

function renderAniListText(value) {
  return parseAniListText(value).map((segment, index) => {
    if (segment.to) return <Link key={`${segment.to}-${index}`} to={segment.to}>{segment.text}</Link>
    if (segment.href) return <a key={`${segment.href}-${index}`} href={segment.href} target="_blank" rel="noreferrer">{segment.text}</a>
    return segment.text
  })
}

export function MediaConnections({ items = [], expanded = false }) {
  const pageSize = expanded ? 12 : items.length
  const pageCount = Math.max(1, Math.ceil(items.length / pageSize))
  const [page, setPage] = useState(0)
  const currentPage = Math.min(page, pageCount - 1)
  const visibleItems = expanded ? items.slice(currentPage * pageSize, currentPage * pageSize + pageSize) : items
  return !!items.length && <section className={`person-section${expanded ? ' person-section--expanded' : ''}`}><span className="eyebrow">Conexiones</span><div className="person-media-heading"><h2>Obras</h2>{expanded && pageCount > 1 && <span>{currentPage + 1} / {pageCount}</span>}</div><AnimatePresence mode="wait" initial={false}><motion.div className="person-media-grid" key={currentPage} initial={{ opacity: 0, x: 22 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -22 }} transition={{ duration: .22, ease: 'easeOut' }}>{visibleItems.map((media) => <Link key={`${media.anilistId}-${media.role}`} to={`/media/${media.anilistId}`}><img src={media.coverImage} alt={`Portada de ${media.title}`} loading="lazy" decoding="async" /><span><strong>{media.title}</strong><small>{roleLabel(media.role)}</small></span></Link>)}</motion.div></AnimatePresence>{expanded && pageCount > 1 && <div className="person-media-controls"><button type="button" disabled={currentPage === 0} onClick={() => setPage((value) => Math.max(0, value - 1))} aria-label="Obras anteriores"><ChevronLeft /></button><button type="button" disabled={currentPage === pageCount - 1} onClick={() => setPage((value) => Math.min(pageCount - 1, value + 1))} aria-label="Obras siguientes"><ChevronRight /></button></div>}</section>
}

function roleLabel(role) {
  if (role === 'MAIN') return 'Principal'
  if (role === 'SUPPORTING') return 'Secundario'
  return {
    Director: 'Dirección', 'Original Creator': 'Obra original', 'Original Story': 'Historia original',
    'Series Composition': 'Composición de serie', 'Character Design': 'Diseño de personajes',
    Story: 'Historia', Art: 'Dibujo', 'Story & Art': 'Historia y dibujo',
  }[role] || role?.replaceAll('_', ' ') || 'Participación'
}
