let deferredPrompt = null

export function initInstallPrompt() {
  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault()
    deferredPrompt = event
    window.dispatchEvent(new Event('anirecord:installable'))
  })
  window.addEventListener('appinstalled', () => {
    deferredPrompt = null
    window.dispatchEvent(new Event('anirecord:installed'))
  })
}

export const isStandalone = () => window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true
export const isIos = () => /iphone|ipad|ipod/i.test(navigator.userAgent)
export const canPromptInstall = () => Boolean(deferredPrompt)
export async function promptInstall() {
  if (!deferredPrompt) return false
  const prompt = deferredPrompt
  deferredPrompt = null
  await prompt.prompt()
  const choice = await prompt.userChoice
  window.dispatchEvent(new Event('anirecord:installable'))
  return choice.outcome === 'accepted'
}

export function urlBase64ToUint8Array(value) {
  const padding = '='.repeat((4 - value.length % 4) % 4)
  const raw = atob((value + padding).replace(/-/g, '+').replace(/_/g, '/'))
  return Uint8Array.from([...raw].map((character) => character.charCodeAt(0)))
}
