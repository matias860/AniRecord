import { useState } from 'react'
import { ChevronDown, ChevronUp, Eye, EyeOff } from 'lucide-react'
import { Link } from 'react-router-dom'

export default function MediaTags({ tags = [], genres = [], mediaType, compact = false }) {
  const [expanded, setExpanded] = useState(false)
  const [showSpoilers, setShowSpoilers] = useState(false)
  const limit = compact ? 3 : 12
  const regularTags = tags.filter((tag) => !tag.spoiler)
  const spoilerTags = compact ? [] : tags.filter((tag) => tag.spoiler)
  const regularVisible = compact ? prioritizeYuri(regularTags, limit) : expanded ? regularTags : regularTags.slice(0, limit)
  const visible = showSpoilers ? [...regularVisible, ...spoilerTags] : regularVisible
  const catalogPath = mediaType ? `/catalogo/${mediaType.toLowerCase()}` : null
  if (!visible.length && !genres.length && !spoilerTags.length) return null

  return <div className={`media-tags ${compact ? 'compact' : ''}`}>
    {!compact && genres.map((genre) => <Link className="media-genre-item" key={genre} to={`${catalogPath}?genre=${encodeURIComponent(genre)}`} title={`Ver ${genre} en el catálogo`}>{genre}</Link>)}
    {visible.map((tag) => {
      const TagWrapper = catalogPath ? Link : 'span'
      const wrapperProps = catalogPath ? { to: `${catalogPath}?tag=${encodeURIComponent(tag.name)}` } : { tabIndex: '0' }
      return <TagWrapper className={`media-tag-item${tag.spoiler ? ' spoiler' : ''}`} key={tag.name} {...wrapperProps} style={{ '--tag-rank': `${tag.rank}%` }}>
      <span className="media-tag-label"><b>{tag.name}</b><small>{tag.spoiler ? 'Spoiler' : `${tag.rank}%`}</small></span>
      <span className="media-tag-tooltip" role="tooltip">
        <span className="media-tag-tooltip-head"><strong>{tag.name}</strong><b>{tag.rank}%</b></span>
        <p>{tag.description || 'AniList no ofrece una descripción para esta categoría.'}</p>
        {tag.category && <small>{tag.category}</small>}
      </span>
      </TagWrapper>
    })}
    {!compact && regularTags.length > limit && <button className="media-tags-expand" type="button" onClick={() => setExpanded((current) => !current)}>{expanded ? <ChevronUp /> : <ChevronDown />}{expanded ? 'Ver menos' : `Ver ${regularTags.length - limit} más`}</button>}
    {!!spoilerTags.length && <button className={`media-tags-spoiler-toggle${showSpoilers ? ' active' : ''}`} type="button" aria-pressed={showSpoilers} onClick={() => setShowSpoilers((current) => !current)}>{showSpoilers ? <EyeOff /> : <Eye />}{showSpoilers ? 'Ocultar spoilers' : `Mostrar spoilers (${spoilerTags.length})`}</button>}
  </div>
}

function prioritizeYuri(tags, limit) {
  const visible = tags.slice(0, limit)
  const yuri = tags.find((tag) => tag.name?.toLocaleLowerCase('en') === 'yuri')
  if (!yuri || visible.some((tag) => tag.name?.toLocaleLowerCase('en') === 'yuri')) return visible
  return [...visible.slice(0, Math.max(0, limit - 1)), yuri]
}
