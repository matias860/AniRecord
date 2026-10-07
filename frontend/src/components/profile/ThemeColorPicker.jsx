import { safeDarkColor, safeAccentColor } from '../../lib/themeContrast.js'
import { ColorPicker } from '@heroui/react/color-picker'
import { ColorArea } from '@heroui/react/color-area'
import { ColorSlider } from '@heroui/react/color-slider'
import { ColorSwatch } from '@heroui/react/color-swatch'
import { Label } from '@heroui/react/label'

export default function ThemeColorPicker({ label, value, onChange, dark = false }) {
  return (
    <ColorPicker value={value} onChange={(color) => onChange(dark ? safeDarkColor(color.toString('hex')) : safeAccentColor(color.toString('hex')))}>
      <ColorPicker.Trigger className="theme-color-trigger"><ColorSwatch /><Label>{label}</Label><span>{value}</span></ColorPicker.Trigger>
      <ColorPicker.Popover className="theme-color-popover">
        <ColorArea colorSpace="hsb" xChannel="saturation" yChannel="brightness"><ColorArea.Thumb /></ColorArea>
        <ColorSlider channel="hue" colorSpace="hsb"><ColorSlider.Track><ColorSlider.Thumb /></ColorSlider.Track></ColorSlider>
      </ColorPicker.Popover>
    </ColorPicker>
  )
}
