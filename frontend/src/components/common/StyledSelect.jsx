import { ChevronDown } from 'lucide-react'

export default function StyledSelect({ value, onChange, options, ariaLabel, fieldLabel, className = '' }) {
  const selectedValue = value == null ? '' : String(value)
  return (
    <div className={`styled-select ${className}`} data-field-label={fieldLabel || undefined}>
      <select className="styled-select-trigger" value={selectedValue} onChange={(event) => onChange(event.target.value)} aria-label={ariaLabel}>
        {options.map((option) => <option value={String(option.value)} key={option.value || '__empty__'}>{option.label}</option>)}
      </select>
      <ChevronDown className="styled-select-chevron" aria-hidden="true" />
    </div>
  )
}
