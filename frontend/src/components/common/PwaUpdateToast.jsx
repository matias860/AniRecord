import { RefreshCw, WifiOff, X } from 'lucide-react'
import { useRegisterSW } from 'virtual:pwa-register/react'

export default function PwaUpdateToast() {
  const { offlineReady: [offlineReady, setOfflineReady], needRefresh: [needRefresh, setNeedRefresh], updateServiceWorker } = useRegisterSW()
  if (!offlineReady && !needRefresh) return null
  const close = () => { setOfflineReady(false); setNeedRefresh(false) }
  return <aside className="pwa-update-toast" role="status">
    <span>{needRefresh ? <RefreshCw /> : <WifiOff />}</span>
    <div><strong>{needRefresh ? 'Hay una actualización' : 'AniRecord está lista sin conexión'}</strong><p>{needRefresh ? 'Actualizá para usar la versión más reciente.' : 'La interfaz podrá abrirse aunque pierdas la conexión.'}</p></div>
    {needRefresh && <button className="primary-button" onClick={() => updateServiceWorker(true)}>Actualizar</button>}
    <button className="pwa-toast-close" aria-label="Cerrar" onClick={close}><X /></button>
  </aside>
}
