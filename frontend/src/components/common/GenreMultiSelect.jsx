import { useEffect, useId, useRef, useState } from 'react'
import { Check, ChevronDown } from 'lucide-react'

export default function GenreMultiSelect({ genres, value, onChange }) {
  const dropdownId = useId()
  const details = useRef(null)
  const [open, setOpen] = useState(false)
  const selected = Array.isArray(value) ? value : []
  const toggle = (genre) => onChange(selected.includes(genre)
    ? selected.filter((item) => item !== genre)
    : [...selected, genre])

  useEffect(() => {
    const closeOther = (event) => {
      if (event.detail !== dropdownId) {
        setOpen(false)
        if (details.current) details.current.open = false
      }
    }
    window.addEventListener('anime-list:dropdown-open', closeOther)
    return () => window.removeEventListener('anime-list:dropdown-open', closeOther)
  }, [dropdownId])

  const handleToggle = (event) => {
    const nextOpen = event.currentTarget.open
    setOpen(nextOpen)
    if (nextOpen) window.dispatchEvent(new CustomEvent('anime-list:dropdown-open', { detail: dropdownId }))
  }

  return <details ref={details} open={open} onToggle={handleToggle} className="genre-multi-select" data-field-label="Categorías">
    <summary><span>{selected.length ? `${selected.length} seleccionadas` : 'Todas'}</span><ChevronDown /></summary>
    <div className="genre-multi-popover">
      <header><strong>Categorías</strong><small>Puedes elegir varias</small></header>
      <div>{genres.map((genre) => <label key={genre} className={selected.includes(genre) ? 'selected' : ''}>
        <input type="checkbox" checked={selected.includes(genre)} onChange={() => toggle(genre)} />
        <span>{genre}</span><Check />
      </label>)}</div>
    </div>
  </details>
}
