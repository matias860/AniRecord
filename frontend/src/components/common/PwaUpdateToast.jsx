import { useEffect, useRef } from 'react'
import { motion } from 'framer-motion'
import { RefreshCw, WifiOff } from 'lucide-react'
import { useRegisterSW } from 'virtual:pwa-register/react'
import { isStandalone, shouldDismissPwaUpdate } from '../../lib/pwaInstall.js'

const dragConstraints = { top: 0, bottom: 0 }
const dragElastic = { top: 0.03, bottom: 0.82 }

export default function PwaUpdateToast() {
  const { offlineReady: [offlineReady, setOfflineReady], needRefresh: [needRefresh, setNeedRefresh], updateServiceWorker } = useRegisterSW()
  const standalone = isStandalone()
  const automaticUpdate = useRef(false)
  useEffect(() => {
    if (standalone || !needRefresh || automaticUpdate.current) return
    automaticUpdate.current = true
    // Registration checks for updates when this browser page opens or reloads.
    // Installed windows keep their explicit update action.
    void updateServiceWorker(true).catch(() => { automaticUpdate.current = false })
  }, [needRefresh, standalone, updateServiceWorker])
  if (!standalone || (!offlineReady && !needRefresh)) return null
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
