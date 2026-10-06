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
export const shouldDismissPwaUpdate = (offsetY, velocityY) => offsetY > 72 || velocityY > 650
export async function isAppInstalled() {
  if (isStandalone()) return true
  if (typeof navigator.getInstalledRelatedApps !== 'function') return false
  try {
    const relatedApps = await navigator.getInstalledRelatedApps()
    return relatedApps.some((app) => app.platform === 'webapp')
  } catch {
    return false
  }
}
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

export function subscriptionUsesKey(subscription, publicKey) {
  const current = subscription?.options?.applicationServerKey
  if (!current || !publicKey) return false
  const expected = urlBase64ToUint8Array(publicKey)
  const actual = new Uint8Array(current)
  return actual.length === expected.length && actual.every((value, index) => value === expected[index])
}
