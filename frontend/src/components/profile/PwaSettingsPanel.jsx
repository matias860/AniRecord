import { Bell, BellRing, Check, Download, ExternalLink, Send, Share2, Smartphone, X } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import apiClient from '../../api/apiClient.js'
import { canPromptInstall, isIos, isStandalone, promptInstall, urlBase64ToUint8Array } from '../../lib/pwaInstall.js'

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
    const update = () => { setInstallable(canPromptInstall()); setInstalled(isStandalone()) }
    window.addEventListener('anirecord:installable', update)
    window.addEventListener('anirecord:installed', update)
    return () => { window.clearTimeout(timer); window.removeEventListener('anirecord:installable', update); window.removeEventListener('anirecord:installed', update) }
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
      const current = await registration.pushManager.getSubscription() || await registration.pushManager.subscribe({
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
    catch (error) { setMessage(error.message) }
    finally { setBusy(false) }
  }

  const iosHelp = !installed && isIos() && !installable
  return <section className="pwa-settings-panel">
    <div className="section-heading"><div><span className="eyebrow">AniRecord en tu celular</span><h2><Smartphone /> App y notificaciones</h2><p>Instalá AniRecord y enterate cuando se estrene una serie o salga un episodio nuevo.</p></div></div>
    <div className="pwa-settings-grid">
      <article className="pwa-setting-card">
        <span className="pwa-setting-icon"><Download /></span>
        <div><small>Aplicación</small><h3>{installed ? 'AniRecord está instalada' : 'Instalar AniRecord'}</h3><p>{installed ? 'Se abre como una app y conserva la interfaz básica sin conexión.' : 'Acceso directo, pantalla completa y actualizaciones automáticas.'}</p></div>
        {installed ? <span className="pwa-state"><Check /> Instalada</span> : installable ? <button className="primary-button" onClick={install}><Download /> Instalar</button> : iosHelp ? <button className="secondary-button" onClick={install}><Share2 /> Cómo instalar</button> : <span className="pwa-state muted"><ExternalLink /> Usá el menú del navegador</span>}
      </article>
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
