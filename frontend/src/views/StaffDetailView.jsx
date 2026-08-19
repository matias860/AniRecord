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
  const authenticated = useSelector((state) => state.auth.authenticated)
  const [page, setPage] = useState(0)
  useEffect(() => { dispatch(fetchStaff(anilistId)) }, [anilistId, dispatch])
  useEffect(() => { if (authenticated) dispatch(fetchPersonFavorites()) }, [authenticated, dispatch])
  if (!person) return <PageState loading={bucket.loadingId === anilistId || !bucket.error} error={bucket.error} />
  const facts = [person.language, person.age && `${person.age} años`, person.birthday && `Nacimiento: ${person.birthday}`, person.homeTown, ...(person.occupations || [])]
  const pageCount = Math.max(1, Math.ceil(person.characters.length / 8))
  const currentPage = Math.min(page, pageCount - 1)
  const visibleCharacters = person.characters.slice(currentPage * 8, currentPage * 8 + 8)
  return <PersonPage person={person} personType="STAFF" favorites={favorites} authenticated={authenticated} eyebrow={professionalLabel(person.occupations)} facts={facts}>
    <section className="person-section staff-roles"><span className="eyebrow">Trayectoria</span><h2>Personajes y obras</h2><AnimatePresence mode="wait" initial={false}><motion.div className="staff-role-grid" key={currentPage} initial={{ opacity: 0, x: 22 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -22 }} transition={{ duration: .22, ease: 'easeOut' }}>{visibleCharacters.map((character) => <StaffRoleCard key={character.anilistId} character={character} />)}</motion.div></AnimatePresence>{pageCount > 1 && <div className="staff-role-controls"><button type="button" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)} aria-label="Ocho personajes anteriores"><ChevronLeft /></button><span>{currentPage + 1} / {pageCount}</span><button type="button" disabled={currentPage === pageCount - 1} onClick={() => setPage(currentPage + 1)} aria-label="Ocho personajes siguientes"><ChevronRight /></button></div>}</section>
  </PersonPage>
}

function professionalLabel(occupations = []) {
  const normalized = (occupations || []).map((occupation) => occupation.toLowerCase())
  if (normalized.some((occupation) => occupation.includes('mangaka') || occupation.includes('writer') || occupation.includes('illustrator'))) return 'Autoría'
  if (normalized.some((occupation) => occupation.includes('voice actor'))) return 'Seiyū'
  return 'Profesional'
}

function StaffRoleCard({ character }) {
  const [mediaPage, setMediaPage] = useState(0)
  const pageCount = Math.max(1, Math.ceil(character.media.length / 3))
  const visibleMedia = character.media.slice(mediaPage * 3, mediaPage * 3 + 3)
  return <article>
    <Link className="staff-character" to={`/personajes/${character.anilistId}`}><img src={character.image} alt={character.name} /><span><strong>{character.name}</strong><small>{character.role === 'MAIN' ? 'Principal' : 'Secundario'}</small></span></Link>
    <div className="staff-media-carousel"><AnimatePresence mode="wait" initial={false}><motion.div className="staff-media-links" key={mediaPage} initial={{ opacity: 0, x: 12 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -12 }} transition={{ duration: .18 }}>{visibleMedia.map((media) => <Link key={media.anilistId} to={`/media/${media.anilistId}`} title={media.title}><img src={media.coverImage} alt={`Portada de ${media.title}`} /></Link>)}</motion.div></AnimatePresence>{pageCount > 1 && <div className="staff-media-controls"><button type="button" disabled={mediaPage === 0} onClick={() => setMediaPage((current) => current - 1)} aria-label="Obras anteriores"><ChevronLeft /></button><span>{mediaPage + 1}/{pageCount}</span><button type="button" disabled={mediaPage === pageCount - 1} onClick={() => setMediaPage((current) => current + 1)} aria-label="Obras siguientes"><ChevronRight /></button></div>}</div>
  </article>
}
