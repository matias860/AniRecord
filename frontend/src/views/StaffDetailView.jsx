import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { useDispatch, useSelector } from 'react-redux'
import { Link, useParams } from 'react-router-dom'
import PageState from '../components/common/PageState.jsx'
import { fetchStaff } from '../features/people/peopleSlice.js'
import { fetchPersonFavorites } from '../features/favorites/favoritesSlice.js'
import { PersonPage } from './CharacterDetailView.jsx'

export default function StaffDetailView() {
  const { anilistId } = useParams()
  const dispatch = useDispatch()
  const bucket = useSelector((state) => state.people.staff)
  const person = bucket.cache[anilistId]
  const favorites = useSelector((state) => state.favorites)
  const [page, setPage] = useState(0)
  useEffect(() => { setPage(0); dispatch(fetchStaff(anilistId)) }, [anilistId, dispatch])
  useEffect(() => { dispatch(fetchPersonFavorites()) }, [dispatch])
  if (!person) return <PageState loading={bucket.loadingId === anilistId || !bucket.error} error={bucket.error} />
  const facts = [person.language, person.age && `${person.age} años`, person.birthday && `Nacimiento: ${person.birthday}`, person.homeTown, ...(person.occupations || [])]
  const pageCount = Math.max(1, Math.ceil(person.characters.length / 8))
  const visibleCharacters = person.characters.slice(page * 8, page * 8 + 8)
  return <PersonPage person={person} personType="STAFF" favorites={favorites} eyebrow="Seiyū" facts={facts}>
    <section className="person-section staff-roles"><span className="eyebrow">Trayectoria</span><h2>Personajes y obras</h2><AnimatePresence mode="wait" initial={false}><motion.div className="staff-role-grid" key={page} initial={{ opacity: 0, x: 22 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -22 }} transition={{ duration: .22, ease: 'easeOut' }}>{visibleCharacters.map((character) => <article key={character.anilistId}><Link className="staff-character" to={`/personajes/${character.anilistId}`}><img src={character.image} alt={character.name} /><span><strong>{character.name}</strong><small>{character.role === 'MAIN' ? 'Principal' : 'Secundario'}</small></span></Link><div className="staff-media-links">{character.media.map((media) => <Link key={media.anilistId} to={`/media/${media.anilistId}`} title={media.title}><img src={media.coverImage} alt={`Portada de ${media.title}`} /></Link>)}</div></article>)}</motion.div></AnimatePresence>{pageCount > 1 && <div className="staff-role-controls"><button type="button" disabled={page === 0} onClick={() => setPage((current) => current - 1)} aria-label="Ocho personajes anteriores"><ChevronLeft /></button><span>{page + 1} / {pageCount}</span><button type="button" disabled={page === pageCount - 1} onClick={() => setPage((current) => current + 1)} aria-label="Ocho personajes siguientes"><ChevronRight /></button></div>}</section>
  </PersonPage>
}
