import { useEffect } from 'react'
import { Heart, Mic2 } from 'lucide-react'
import { useDispatch, useSelector } from 'react-redux'
import { Link, useParams } from 'react-router-dom'
import PageState from '../components/common/PageState.jsx'
import { fetchCharacter } from '../features/people/peopleSlice.js'
import { addPersonFavorite, fetchPersonFavorites, removePersonFavorite } from '../features/favorites/favoritesSlice.js'
import { cleanAniListText } from '../lib/media.js'

export default function CharacterDetailView() {
  const { anilistId } = useParams()
  const dispatch = useDispatch()
  const bucket = useSelector((state) => state.people.character)
  const person = bucket.cache[anilistId]
  const favorites = useSelector((state) => state.favorites)
  useEffect(() => { dispatch(fetchCharacter(anilistId)) }, [anilistId, dispatch])
  useEffect(() => { dispatch(fetchPersonFavorites()) }, [dispatch])
  if (!person) return <PageState loading={bucket.loadingId === anilistId || !bucket.error} error={bucket.error} />
  return <PersonPage person={person} personType="CHARACTER" favorites={favorites} eyebrow="Personaje" facts={[person.gender, person.age && `${person.age} años`, person.birthday && `Nacimiento: ${person.birthday}`, person.bloodType && `Grupo sanguíneo: ${person.bloodType}`]}>
    {!!person.voiceActors?.length && <section className="person-section"><span className="eyebrow"><Mic2 /> Actuación de voz</span><h2>Seiyūs</h2><div className="person-card-grid">{person.voiceActors.map((actor) => <Link className="person-card" key={actor.anilistId} to={`/seiyuus/${actor.anilistId}`}><img src={actor.image} alt={actor.name} /><span><strong>{actor.name}</strong><small>{actor.nativeName || actor.language}</small></span></Link>)}</div></section>}
    <MediaConnections items={person.media} />
  </PersonPage>
}

export function PersonPage({ person, personType, favorites, eyebrow, facts, children }) {
  const dispatch = useDispatch()
  const favorite = favorites.people.some((item) => item.anilistId === person.anilistId && item.personType === personType)
  const toggleFavorite = () => favorite
    ? dispatch(removePersonFavorite({ anilistId: person.anilistId, personType }))
    : dispatch(addPersonFavorite({ anilistId: person.anilistId, personType, name: person.name, nativeName: person.nativeName, imageUrl: person.image }))
  return <article className="person-detail"><header className="person-hero"><img src={person.image} alt={person.name} /><div><span className="eyebrow">{eyebrow}</span><h1>{person.name}</h1>{person.nativeName && <p className="person-native">{person.nativeName}</p>}<div className="person-facts">{facts.filter(Boolean).map((fact) => <span key={fact}>{fact}</span>)}{person.favourites != null && <span><Heart /> {person.favourites.toLocaleString('es-ES')} favoritos en AniList</span>}</div><button className={`person-favorite-button ${favorite ? 'active' : ''}`} disabled={favorites.mutationStatus === 'loading'} onClick={toggleFavorite}><Heart /> {favorite ? 'Quitar de favoritos' : 'Agregar a favoritos'}</button></div></header>{person.description && <section className="person-biography"><span className="eyebrow">Biografía</span><p>{cleanAniListText(person.description)}</p></section>}<div className="person-connections">{children}</div></article>
}

export function MediaConnections({ items = [] }) {
  return !!items.length && <section className="person-section"><span className="eyebrow">Conexiones</span><h2>Obras</h2><div className="person-media-grid">{items.map((media) => <Link key={`${media.anilistId}-${media.role}`} to={`/media/${media.anilistId}`}><img src={media.coverImage} alt={`Portada de ${media.title}`} /><span><strong>{media.title}</strong><small>{media.role === 'MAIN' ? 'Principal' : 'Secundario'}</small></span></Link>)}</div></section>
}
