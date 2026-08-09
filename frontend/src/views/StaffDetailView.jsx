import { useEffect } from 'react'
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
  useEffect(() => { dispatch(fetchStaff(anilistId)) }, [anilistId, dispatch])
  useEffect(() => { dispatch(fetchPersonFavorites()) }, [dispatch])
  if (!person) return <PageState loading={bucket.loadingId === anilistId || !bucket.error} error={bucket.error} />
  const facts = [person.language, person.age && `${person.age} años`, person.birthday && `Nacimiento: ${person.birthday}`, person.homeTown, ...(person.occupations || [])]
  return <PersonPage person={person} personType="STAFF" favorites={favorites} eyebrow="Seiyū" facts={facts}>
    <section className="person-section staff-roles"><span className="eyebrow">Trayectoria</span><h2>Personajes y obras</h2><div className="staff-role-grid">{person.characters.map((character) => <article key={character.anilistId}><Link className="staff-character" to={`/personajes/${character.anilistId}`}><img src={character.image} alt={character.name} /><span><strong>{character.name}</strong><small>{character.role === 'MAIN' ? 'Principal' : 'Secundario'}</small></span></Link><div className="staff-media-links">{character.media.map((media) => <Link key={media.anilistId} to={`/media/${media.anilistId}`} title={media.title}><img src={media.coverImage} alt="" /></Link>)}</div></article>)}</div></section>
  </PersonPage>
}
