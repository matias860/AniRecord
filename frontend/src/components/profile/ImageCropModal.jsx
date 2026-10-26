import useModalFocus from '../../lib/useModalFocus.js'
import { useEffect, useMemo, useState } from 'react'
import Cropper from 'react-easy-crop'
import { Check, X } from 'lucide-react'
import { motion } from 'framer-motion'
import { createPortal } from 'react-dom'
import { backdropMotion, modalMotion } from '../../lib/motionVariants.js'

export default function ImageCropModal({ file, type, onCancel, onConfirm }) {
  const dialogRef = useModalFocus(true)
  const imageUrl = useMemo(() => URL.createObjectURL(file), [file])
  const [crop, setCrop] = useState({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  const [pixels, setPixels] = useState(null)
  const [busy, setBusy] = useState(false)
  const isAvatar = type === 'avatar'
  useEffect(() => () => URL.revokeObjectURL(imageUrl), [imageUrl])
  useEffect(() => {
    const close = (event) => { if (event.key === 'Escape') onCancel() }
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [onCancel])

  const save = async () => {
    if (!pixels || busy) return
    setBusy(true)
    try { onConfirm(await cropImage(imageUrl, pixels, type)) }
    finally { setBusy(false) }
  }

  return createPortal(<motion.div {...backdropMotion} className="image-crop-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onCancel() }}>
    <motion.section {...modalMotion} ref={dialogRef} className="image-crop-modal" role="dialog" aria-modal="true" aria-labelledby="crop-title">
      <button className="modal-close" type="button" onClick={onCancel} aria-label="Cerrar editor"><X /></button>
      <span className="eyebrow">Ajustar imagen</span><h2 id="crop-title">Recortar {isAvatar ? 'avatar' : 'banner'}</h2>
      <p>Arrastra la imagen y usa el zoom hasta que se vea como quieres.</p>
      <div className={`image-crop-stage ${isAvatar ? 'avatar' : 'banner'}`}><Cropper image={imageUrl} crop={crop} zoom={zoom} aspect={isAvatar ? 1 : 16 / 5} cropShape={isAvatar ? 'round' : 'rect'} showGrid={!isAvatar} onCropChange={setCrop} onZoomChange={setZoom} onCropComplete={(_, area) => setPixels(area)} /></div>
      <label className="crop-zoom"><span>Zoom</span><input type="range" min="1" max="3" step="0.01" value={zoom} onChange={(event) => setZoom(Number(event.target.value))} /></label>
      <div className="modal-actions"><button className="secondary-button" type="button" onClick={onCancel}>Cancelar</button><button className="primary-button" type="button" disabled={busy || !pixels} onClick={save}><Check /> {busy ? 'Preparando...' : 'Usar imagen'}</button></div>
    </motion.section>
  </motion.div>, document.body)
}

async function cropImage(source, area, type) {
  const image = await loadImage(source)
  const canvas = document.createElement('canvas')
  const [width, height] = type === 'avatar' ? [512, 512] : [1600, 500]
  canvas.width = width; canvas.height = height
  canvas.getContext('2d').drawImage(image, area.x, area.y, area.width, area.height, 0, 0, width, height)
  const blob = await new Promise((resolve, reject) => canvas.toBlob((value) => value ? resolve(value) : reject(new Error('No se pudo recortar la imagen')), 'image/webp', .9))
  return new File([blob], `${type}.webp`, { type: 'image/webp' })
}

function loadImage(source) {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('No se pudo abrir la imagen'))
    image.src = source
  })
}
