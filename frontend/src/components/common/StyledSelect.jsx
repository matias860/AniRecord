import { ListBox, Select } from '@heroui/react'

const emptyKey = '__empty__'

export default function StyledSelect({ value, onChange, options, ariaLabel, className = '' }) {
  const selectedValue = value === '' || value == null ? emptyKey : String(value)
  return (
    <Select className={`styled-select ${className}`} value={selectedValue} onChange={(next) => onChange(next === emptyKey ? '' : String(next))} aria-label={ariaLabel}>
      <Select.Trigger className="styled-select-trigger">
        <Select.Value />
        <Select.Indicator />
      </Select.Trigger>
      <Select.Popover className="styled-select-popover" placement="bottom start">
        <ListBox className="styled-select-list">
          {options.map((option) => <ListBox.Item className="styled-select-option" id={option.value === '' ? emptyKey : String(option.value)} textValue={option.label} key={option.value || emptyKey}>
            <span>{option.label}</span><ListBox.ItemIndicator />
          </ListBox.Item>)}
        </ListBox>
      </Select.Popover>
    </Select>
  )
}
