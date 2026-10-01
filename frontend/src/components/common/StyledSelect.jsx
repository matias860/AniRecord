import { useEffect, useId, useState } from 'react'
import { ListBox, Select } from '@heroui/react'

const emptyKey = '__empty__'

export default function StyledSelect({ value, onChange, options, ariaLabel, fieldLabel, className = '' }) {
  const dropdownId = useId()
  const [open, setOpen] = useState(false)
  const selectedValue = value === '' || value == null ? emptyKey : String(value)
  useEffect(() => {
    const closeOther = (event) => { if (event.detail !== dropdownId) setOpen(false) }
    window.addEventListener('anime-list:dropdown-open', closeOther)
    return () => window.removeEventListener('anime-list:dropdown-open', closeOther)
  }, [dropdownId])
  const changeOpen = (nextOpen) => {
    setOpen(nextOpen)
    if (nextOpen) window.dispatchEvent(new CustomEvent('anime-list:dropdown-open', { detail: dropdownId }))
  }
  return (
    <Select className={`styled-select ${className}`} data-field-label={fieldLabel || undefined} data-open={open || undefined} value={selectedValue} onChange={(next) => onChange(next === emptyKey ? '' : String(next))} aria-label={ariaLabel} isOpen={open} onOpenChange={changeOpen}>
      <Select.Trigger className="styled-select-trigger">
        <Select.Value />
        <Select.Indicator />
      </Select.Trigger>
      <Select.Popover className="styled-select-popover" placement="bottom start" containerPadding={12} shouldFlip>
        <ListBox className="styled-select-list">
          {options.map((option) => <ListBox.Item className="styled-select-option" id={option.value === '' ? emptyKey : String(option.value)} textValue={option.label} key={option.value || emptyKey}>
            <span>{option.label}</span><ListBox.ItemIndicator />
          </ListBox.Item>)}
        </ListBox>
      </Select.Popover>
    </Select>
  )
}
