import { safeDarkColor, safeAccentColor } from '../../lib/themeContrast.js'

export default function ThemeColorPicker({ label, value, onChange, dark = false }) {
  const update = (event) => {
    const color = event.target.value
    onChange(dark ? safeDarkColor(color) : safeAccentColor(color))
  }
  return <label className="theme-color-trigger">
    <input type="color" value={value} onChange={update} aria-label={label} />
    <span className="theme-color-swatch" style={{ backgroundColor: value }} aria-hidden="true" />
    <strong>{label}</strong>
    <span>{value}</span>
  </label>
}
