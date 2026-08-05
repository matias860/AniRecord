import { Check, ChevronDown } from 'lucide-react'

export default function GenreMultiSelect({ genres, value, onChange }) {
  const selected = Array.isArray(value) ? value : []
  const toggle = (genre) => onChange(selected.includes(genre)
    ? selected.filter((item) => item !== genre)
    : [...selected, genre])

  return <details className="genre-multi-select">
    <summary><span>{selected.length ? `${selected.length} categorías` : 'Categorías'}</span><ChevronDown /></summary>
    <div className="genre-multi-popover">
      <header><strong>Categorías</strong><small>Podés elegir varias</small></header>
      <div>{genres.map((genre) => <label key={genre} className={selected.includes(genre) ? 'selected' : ''}>
        <input type="checkbox" checked={selected.includes(genre)} onChange={() => toggle(genre)} />
        <span>{genre}</span><Check />
      </label>)}</div>
    </div>
  </details>
}
