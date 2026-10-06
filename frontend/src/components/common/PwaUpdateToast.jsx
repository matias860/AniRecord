import { motion } from 'framer-motion'
import { RefreshCw, WifiOff } from 'lucide-react'
import { useRegisterSW } from 'virtual:pwa-register/react'
import { shouldDismissPwaUpdate } from '../../lib/pwaInstall.js'

const dragConstraints = { top: 0, bottom: 0 }
const dragElastic = { top: 0.03, bottom: 0.82 }

export default function PwaUpdateToast() {
  const { offlineReady: [offlineReady, setOfflineReady], needRefresh: [needRefresh, setNeedRefresh], updateServiceWorker } = useRegisterSW()
  if (!offlineReady && !needRefresh) return null
  const close = () => { setOfflineReady(false); setNeedRefresh(false) }
  const finishDrag = (_, info) => { if (shouldDismissPwaUpdate(info.offset.y, info.velocity.y)) close() }
  return <motion.aside
    className="pwa-update-toast"
    role="status"
    drag="y"
    dragConstraints={dragConstraints}
    dragElastic={dragElastic}
    onDragEnd={finishDrag}
    whileDrag={{ scale: 0.985 }}
  >
    <i className="pwa-update-drag-handle" aria-hidden="true" />
    <span>{needRefresh ? <RefreshCw /> : <WifiOff />}</span>
    <div><strong>{needRefresh ? 'Hay una actualización' : 'AniRecord está lista sin conexión'}</strong><p>{needRefresh ? 'Actualizá para usar la versión más reciente.' : 'La interfaz podrá abrirse aunque pierdas la conexión.'}</p></div>
    {needRefresh && <button className="primary-button" onClick={() => updateServiceWorker(true)}>Actualizar</button>}
  </motion.aside>
}
