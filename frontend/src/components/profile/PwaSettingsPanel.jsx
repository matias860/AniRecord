import { Bell, BellRing, Check, Download, ExternalLink, Send, Share2, Smartphone, X } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import apiClient from '../../api/apiClient.js'
import { canPromptInstall, isAppInstalled, isIos, isStandalone, promptInstall, subscriptionUsesKey, urlBase64ToUint8Array } from '../../lib/pwaInstall.js'

const supported = () => 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window

export default function PwaSettingsPanel() {
  const [installed, setInstalled] = useState(isStandalone())
  const [installable, setInstallable] = useState(canPromptInstall())
  const [config, setConfig] = useState(null)
  const [subscription, setSubscription] = useState(null)
  const [preferences, setPreferences] = useState({ premieresEnabled: true, episodesEnabled: true })
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')

  const load = useCallback(async () => {
    try {
      const { data } = await apiClient.get('/push/config')
      setConfig(data)
      if (!supported()) return
      const registration = await navigator.serviceWorker.ready
      const current = await registration.pushManager.getSubscription()
      if (current) {
        if (!subscriptionUsesKey(current, data.publicKey)) {
          setSubscription(null)
          setMessage('La configuración de avisos cambió. Volvé a activarlos para renovar este dispositivo.')
          return
        }
        const status = await apiClient.post('/push/subscriptions/status', { endpoint: current.endpoint })
        setPreferences({ premieresEnabled: status.data.premieresEnabled, episodesEnabled: status.data.episodesEnabled })
        setSubscription(status.data.enabled ? current : null)
      } else {
        setSubscription(null)
      }
    } catch (error) { setMessage(error.message) }
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(load, 0)
    const refreshInstallState = async () => {
      setInstallable(canPromptInstall())
      setInstalled(await isAppInstalled())
    }
    const installedNow = () => { setInstalled(true); setInstallable(false) }
    const refreshWhenVisible = () => { if (document.visibilityState === 'visible') refreshInstallState() }
    refreshInstallState()
    window.addEventListener('anirecord:installable', refreshInstallState)
    window.addEventListener('anirecord:installed', installedNow)
    window.addEventListener('focus', refreshInstallState)
    document.addEventListener('visibilitychange', refreshWhenVisible)
    return () => {
      window.clearTimeout(timer)
      window.removeEventListener('anirecord:installable', refreshInstallState)
      window.removeEventListener('anirecord:installed', installedNow)
      window.removeEventListener('focus', refreshInstallState)
      document.removeEventListener('visibilitychange', refreshWhenVisible)
    }
  }, [load])

  const install = async () => {
    if (await promptInstall()) setInstalled(true)
    else if (isIos()) setMessage('En Safari, tocá Compartir y luego “Agregar a inicio”.')
  }

  const payload = (current, next = preferences) => {
    const serialized = current.toJSON()
    return { endpoint: current.endpoint, keys: serialized.keys, ...next }
  }

  const enable = async () => {
    setBusy(true); setMessage('')
    try {
      if (!supported()) throw new Error('Este navegador no admite notificaciones push.')
      if (!config?.available) throw new Error('Las notificaciones todavía no están disponibles en el servidor.')
      const permission = await Notification.requestPermission()
      if (permission !== 'granted') throw new Error('El permiso de notificaciones no fue concedido.')
      const registration = await navigator.serviceWorker.ready
      let current = await registration.pushManager.getSubscription()
      if (current && !subscriptionUsesKey(current, config.publicKey)) {
        try { await apiClient.delete('/push/subscriptions', { data: { endpoint: current.endpoint } }) } catch { /* An old endpoint may no longer exist server-side. */ }
        await current.unsubscribe()
        current = null
      }
      current ||= await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(config.publicKey),
      })
      await apiClient.post('/push/subscriptions', payload(current))
      setSubscription(current)
      setMessage('Notificaciones activadas en este dispositivo.')
    } catch (error) { setMessage(error.message) }
    finally { setBusy(false) }
  }

  const disable = async () => {
    if (!subscription) return
    setBusy(true); setMessage('')
    try {
      await apiClient.delete('/push/subscriptions', { data: { endpoint: subscription.endpoint } })
      await subscription.unsubscribe()
      setSubscription(null)
      setMessage('Notificaciones desactivadas en este dispositivo.')
    } catch (error) { setMessage(error.message) }
    finally { setBusy(false) }
  }

  const toggle = async (key) => {
    if (!subscription) return
    const next = { ...preferences, [key]: !preferences[key] }
    setPreferences(next)
    try { await apiClient.patch('/push/subscriptions', payload(subscription, next)) }
    catch (error) { setPreferences(preferences); setMessage(error.message) }
  }

  const test = async () => {
    setBusy(true); setMessage('')
    try { await apiClient.post('/push/test', { endpoint: subscription.endpoint }); setMessage('Prueba enviada. Puede tardar unos segundos.') }
    catch (error) {
      const rejectedSubscription = error.status === 403 || error.message?.includes('HTTP 403')
      if (!rejectedSubscription || !config?.publicKey) {
        setMessage(error.message)
      } else {
        try {
          try { await apiClient.delete('/push/subscriptions', { data: { endpoint: subscription.endpoint } }) } catch { /* The rejected endpoint may already be absent. */ }
          await subscription.unsubscribe()
          const registration = await navigator.serviceWorker.ready
          const renewed = await registration.pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: urlBase64ToUint8Array(config.publicKey),
          })
          await apiClient.post('/push/subscriptions', payload(renewed))
          setSubscription(renewed)
          await apiClient.post('/push/test', { endpoint: renewed.endpoint })
          setMessage('Renovamos la configuración del dispositivo y enviamos la prueba.')
        } catch (renewalError) {
          setSubscription(null)
          setMessage(`No pudimos renovar los avisos automáticamente: ${renewalError.message}`)
        }
      }
    }
    finally { setBusy(false) }
  }

  const iosHelp = !installed && isIos() && !installable
  return <section className="pwa-settings-panel">
    <div className="section-heading"><div><span className="eyebrow">AniRecord en tu celular</span><h2><Smartphone /> {installed ? 'Notificaciones' : 'App y notificaciones'}</h2><p>{installed ? 'Elegí qué avisos querés recibir en este dispositivo.' : 'Instalá AniRecord y enterate cuando se estrene una serie o salga un episodio nuevo.'}</p></div></div>
    <div className={`pwa-settings-grid${installed ? ' is-single' : ''}`}>
      {!installed && <article className="pwa-setting-card">
        <span className="pwa-setting-icon"><Download /></span>
        <div><small>Aplicación</small><h3>Instalar AniRecord</h3><p>Acceso directo, pantalla completa y actualizaciones automáticas.</p></div>
        {installable ? <button className="primary-button" onClick={install}><Download /> Instalar</button> : iosHelp ? <button className="secondary-button" onClick={install}><Share2 /> Cómo instalar</button> : <span className="pwa-state muted"><ExternalLink /> Usá el menú del navegador</span>}
      </article>}
      <article className="pwa-setting-card">
        <span className="pwa-setting-icon"><BellRing /></span>
        <div><small>Notificaciones push</small><h3>{subscription ? 'Avisos activados' : 'Estrenos y episodios'}</h3><p>Solo se enviarán avisos sobre anime pendiente o en curso de tu biblioteca.</p></div>
        {subscription ? <button className="secondary-button danger" disabled={busy} onClick={disable}><X /> Desactivar</button> : <button className="primary-button" disabled={busy || config?.available === false} onClick={enable}><Bell /> {busy ? 'Activando…' : 'Activar'}</button>}
      </article>
    </div>
    {subscription && <div className="pwa-push-options">
      <button className={preferences.premieresEnabled ? 'active' : ''} onClick={() => toggle('premieresEnabled')}><span>{preferences.premieresEnabled && <Check />}</span><b>Estrenos</b><small>Primer episodio disponible</small></button>
      <button className={preferences.episodesEnabled ? 'active' : ''} onClick={() => toggle('episodesEnabled')}><span>{preferences.episodesEnabled && <Check />}</span><b>Episodios nuevos</b><small>Siguientes capítulos al emitirse</small></button>
      <button className="pwa-test-button" disabled={busy} onClick={test}><Send /> Enviar prueba</button>
    </div>}
    {message && <p className="pwa-settings-message">{message}</p>}
  </section>
}
