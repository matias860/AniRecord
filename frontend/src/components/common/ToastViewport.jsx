import { AnimatePresence, motion } from 'framer-motion'
import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react'
import { useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { removeToast } from '../../features/toast/toastSlice.js'
import { toastMotion } from '../../lib/motionVariants.js'

const icons = { error: AlertCircle, success: CheckCircle2, info: Info }

export default function ToastViewport() {
  const items = useSelector((state) => state.toast.items)
  return <div className="toast-viewport" aria-live="polite" aria-label="Mensajes de la aplicación"><AnimatePresence>{items.map((toast) => <ToastItem toast={toast} key={toast.id} />)}</AnimatePresence></div>
}

function ToastItem({ toast }) {
  const dispatch = useDispatch(); const Icon = icons[toast.type] || Info
  useEffect(() => { const timer = window.setTimeout(() => dispatch(removeToast(toast.id)), toast.type === 'error' ? 6500 : 4000); return () => window.clearTimeout(timer) }, [dispatch, toast.id, toast.type])
  const onClose = () => dispatch(removeToast(toast.id))
  return <motion.article {...toastMotion} className={`app-toast ${toast.type}`} role={toast.type === 'error' ? 'alert' : 'status'} layout="position"><div className="toast-icon"><Icon /></div><div><strong>{toast.title}</strong><p>{toast.message}</p></div><button onClick={onClose} aria-label="Cerrar mensaje"><X /></button><motion.i initial={{ scaleX: 1 }} animate={{ scaleX: 0 }} transition={{ duration: toast.type === 'error' ? 6.5 : 4, ease: 'linear' }} /></motion.article>
}
