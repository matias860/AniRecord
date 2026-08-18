import { useId } from 'react'
import { BookOpen, Clapperboard } from 'lucide-react'
import { motion } from 'framer-motion'

const options = [
  { value: 'ANIME', label: 'Anime', Icon: Clapperboard },
  { value: 'MANGA', label: 'Manga', Icon: BookOpen },
]

export default function MediaTypeSwitch({ value, onChange, ariaLabel = 'Cambiar tipo de contenido', className = '' }) {
  const switchId = useId()
  return <div className={`media-type-switch ${className}`} role="group" aria-label={ariaLabel}>
    {options.map(({ value: optionValue, label, Icon }) => {
      const active = value === optionValue
      return <button type="button" className={active ? 'active' : ''} onClick={() => !active && onChange(optionValue)} aria-pressed={active} key={optionValue}>
        {active && <motion.span className="media-type-switch-highlight" layoutId={`media-type-switch-${switchId}`} transition={{ type: 'spring', stiffness: 430, damping: 34 }} />}
        <Icon /><span>{label}</span>
      </button>
    })}
  </div>
}
