export async function prepareImageForCrop(file, type) {
  if (!window.createImageBitmap) return file
  let source
  try {
    source = await createImageBitmap(file, { imageOrientation: 'from-image' })
    const maxDimension = type === 'avatar' ? 1400 : 2400
    const scale = Math.min(1, maxDimension / Math.max(source.width, source.height))
    if (scale === 1) return file
    const width = Math.max(1, Math.round(source.width * scale))
    const height = Math.max(1, Math.round(source.height * scale))
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    canvas.getContext('2d').drawImage(source, 0, 0, width, height)
    const blob = await new Promise((resolve, reject) => canvas.toBlob((value) => value ? resolve(value) : reject(new Error('No se pudo preparar la imagen')), 'image/webp', .86))
    return new File([blob], `${type}-preview.webp`, { type: 'image/webp' })
  } catch {
    return file
  } finally {
    source?.close()
  }
}
