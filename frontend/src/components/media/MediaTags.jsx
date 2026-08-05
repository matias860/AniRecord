export default function MediaTags({ tags = [], compact = false }) {
  const visible = compact ? tags.slice(0, 3) : tags.slice(0, 5)
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
