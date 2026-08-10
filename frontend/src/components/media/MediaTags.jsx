export default function MediaTags({ tags = [], compact = false }) {
  const limit = compact ? 3 : 5
  const visible = prioritizeYuri(tags, limit)
  if (!visible.length) return null

  return <div className={`media-tags ${compact ? 'compact' : ''}`}>
    {visible.map((tag) => <span className="media-tag-item" key={tag.name} tabIndex="0" style={{ '--tag-rank': `${tag.rank}%` }}>
      <span className="media-tag-label"><b>{tag.name}</b><small>{tag.rank}%</small></span>
      <span className="media-tag-tooltip" role="tooltip">
        <span className="media-tag-tooltip-head"><strong>{tag.name}</strong><b>{tag.rank}%</b></span>
        <p>{tag.description || 'AniList no ofrece una descripción para esta categoría.'}</p>
        {tag.category && <small>{tag.category}</small>}
      </span>
    </span>)}
  </div>
}

function prioritizeYuri(tags, limit) {
  const visible = tags.slice(0, limit)
  const yuri = tags.find((tag) => tag.name?.toLocaleLowerCase('en') === 'yuri')
  if (!yuri || visible.some((tag) => tag.name?.toLocaleLowerCase('en') === 'yuri')) return visible
  return [...visible.slice(0, Math.max(0, limit - 1)), yuri]
}
