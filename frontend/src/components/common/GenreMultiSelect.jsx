import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Check, ChevronDown, Search, X } from 'lucide-react'

export default function GenreMultiSelect({ genres = [], tags = [], value, onChange, className = '' }) {
  const dropdownId = useId()
  const details = useRef(null)
  const trigger = useRef(null)
  const popover = useRef(null)
  const searchInput = useRef(null)
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [popoverStyle, setPopoverStyle] = useState(undefined)
  const legacyValue = Array.isArray(value)
  const selectedGenres = legacyValue ? value : value?.genre ?? []
  const selectedTags = legacyValue ? [] : value?.tag ?? []
  const selectedCount = selectedGenres.length + selectedTags.length

  const filteredGenres = useMemo(() => filterByQuery(genres, query, (genre) => genre), [genres, query])
  const groupedTags = useMemo(() => {
    const filtered = filterByQuery(tags, query, (tag) => `${tag.name} ${tag.category ?? ''} ${tag.description ?? ''}`)
    return Object.entries(filtered.reduce((groups, tag) => {
      const category = tag.category || 'Otras'
      groups[category] = [...(groups[category] ?? []), tag]
      return groups
    }, {}))
  }, [query, tags])

  const emit = (nextGenres, nextTags) => onChange(legacyValue ? nextGenres : { genre: nextGenres, tag: nextTags })
  const toggleGenre = (genre) => emit(toggleValue(selectedGenres, genre), selectedTags)
  const toggleTag = (tag) => emit(selectedGenres, toggleValue(selectedTags, tag))
  const clear = () => emit([], [])

  useEffect(() => {
    const closeOther = (event) => {
      if (event.detail !== dropdownId) closeDropdown(details, setOpen, setQuery)
    }
    const closeOutside = (event) => {
      const insideTrigger = details.current?.contains(event.target)
      const insidePopover = popover.current?.contains(event.target)
      if (!insideTrigger && !insidePopover) closeDropdown(details, setOpen, setQuery)
    }
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') closeDropdown(details, setOpen, setQuery)
    }
    window.addEventListener('anime-list:dropdown-open', closeOther)
    document.addEventListener('pointerdown', closeOutside)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      window.removeEventListener('anime-list:dropdown-open', closeOther)
      document.removeEventListener('pointerdown', closeOutside)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [dropdownId])

  useEffect(() => {
    if (open) window.requestAnimationFrame(() => searchInput.current?.focus())
  }, [open])

  const positionPopover = useCallback(() => {
    const triggerRect = trigger.current?.getBoundingClientRect()
    if (!triggerRect) return

    const viewportWidth = document.documentElement.clientWidth
    const viewportHeight = window.innerHeight
    const margin = 12
    const gap = 8
    const triggerStyles = window.getComputedStyle(trigger.current)
    const theme = Object.fromEntries(
      ['--accent', '--accent-2', '--user-bg', '--user-surface']
        .map((property) => [property, triggerStyles.getPropertyValue(property).trim()])
        .filter(([, value]) => value),
    )

    if (viewportWidth <= 460) {
      setPopoverStyle({ ...theme, top: 72, right: margin, bottom: margin, left: margin, width: 'auto', maxHeight: 'none' })
      return
    }

    const width = Math.min(620, viewportWidth - margin * 2)
    const left = Math.min(Math.max(triggerRect.right - width, margin), viewportWidth - width - margin)
    const spaceBelow = viewportHeight - triggerRect.bottom - gap - margin
    const spaceAbove = triggerRect.top - gap - margin
    const placeAbove = spaceBelow < 360 && spaceAbove > spaceBelow
    const availableHeight = Math.max(200, placeAbove ? spaceAbove : spaceBelow)
    const maxHeight = Math.min(480, availableHeight)

    setPopoverStyle(placeAbove
      ? { ...theme, right: 'auto', bottom: viewportHeight - triggerRect.top + gap, left, width, maxHeight }
      : { ...theme, top: triggerRect.bottom + gap, right: 'auto', bottom: 'auto', left, width, maxHeight })
  }, [])

  useEffect(() => {
    if (!open) return undefined
    const frame = window.requestAnimationFrame(positionPopover)
    window.addEventListener('resize', positionPopover)
    window.addEventListener('scroll', positionPopover, true)
    return () => {
      window.cancelAnimationFrame(frame)
      window.removeEventListener('resize', positionPopover)
      window.removeEventListener('scroll', positionPopover, true)
    }
  }, [open, positionPopover])

  const handleToggle = (event) => {
    const nextOpen = event.currentTarget.open
    setOpen(nextOpen)
    if (nextOpen) window.dispatchEvent(new CustomEvent('anime-list:dropdown-open', { detail: dropdownId }))
    else setQuery('')
  }

  return <details ref={details} open={open} onToggle={handleToggle} className={`genre-multi-select ${className}`} data-field-label="Categorías">
    <summary ref={trigger}><span>{selectedCount ? `${selectedCount} seleccionada${selectedCount === 1 ? '' : 's'}` : 'Todas'}</span><ChevronDown /></summary>
    {open && createPortal(<div ref={popover} className="genre-multi-popover" style={popoverStyle}>
      <header><div><strong>Categorías</strong><small>Géneros y etiquetas de AniList</small></div>{selectedCount > 0 && <button type="button" onClick={clear}><X /> Limpiar</button>}</header>
      {tags.length > 0 && <label className="category-search"><Search /><input ref={searchInput} value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar categoría…" /></label>}
      <div className="category-options">
        {!!filteredGenres.length && <CategoryGroup title="Géneros">{filteredGenres.map((genre) => <CategoryOption key={genre} label={genre} selected={selectedGenres.includes(genre)} onClick={() => toggleGenre(genre)} />)}</CategoryGroup>}
        {groupedTags.map(([category, categoryTags]) => <CategoryGroup key={category} title={category}>{categoryTags.map((tag) => <CategoryOption key={tag.name} label={tag.name} description={tag.description} selected={selectedTags.includes(tag.name)} onClick={() => toggleTag(tag.name)} />)}</CategoryGroup>)}
        {!filteredGenres.length && !groupedTags.length && <p className="category-empty">No encontramos esa categoría.</p>}
      </div>
    </div>, document.body)}
  </details>
}

function CategoryGroup({ title, children }) {
  return <section className="category-group"><h4>{title}</h4><div>{children}</div></section>
}

function CategoryOption({ label, description, selected, onClick }) {
  return <button className={selected ? 'selected' : ''} type="button" onClick={onClick} title={description || label} aria-pressed={selected}><span>{label}</span><Check /></button>
}

function filterByQuery(items, query, text) {
  const normalized = query.trim().toLocaleLowerCase('es')
  if (!normalized) return items
  return items.filter((item) => text(item).toLocaleLowerCase('es').includes(normalized))
}

function toggleValue(values, value) {
  return values.includes(value) ? values.filter((item) => item !== value) : [...values, value]
}

function closeDropdown(details, setOpen, setQuery) {
  setOpen(false)
  setQuery('')
  if (details.current) details.current.open = false
}
