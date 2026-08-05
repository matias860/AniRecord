import { AnimatePresence, motion } from 'framer-motion'
import { Bell, CheckCheck, GitBranch, Info, Play, RefreshCw, Trash2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { Link } from 'react-router-dom'
import { fetchNotifications, markAllNotificationsRead, markNotificationRead } from '../../features/notifications/notificationSlice.js'

const icons = { FIRST_EPISODE: Play, RELATED_MEDIA_ADDITION: GitBranch, MEDIA_DATA_CHANGE: RefreshCw, MEDIA_MERGE: Info, MEDIA_DELETION: Trash2 }

export default function NotificationBell() {
  const [open, setOpen] = useState(false); const root = useRef(null); const dispatch = useDispatch()
  const { items, status } = useSelector((state) => state.notifications); const unread = items.filter((item) => !item.read).length
  useEffect(() => { if (status === 'idle') dispatch(fetchNotifications()) }, [dispatch, status])
  useEffect(() => { const close = (event) => { if (!root.current?.contains(event.target)) setOpen(false) }; document.addEventListener('mousedown', close); return () => document.removeEventListener('mousedown', close) }, [])
  const select = (item) => { if (!item.read) dispatch(markNotificationRead(item.id)); setOpen(false) }
  return <div className="notification-root" ref={root}><button className="notification-button" onClick={() => setOpen((value) => !value)} aria-label="Notificaciones"><Bell />{unread > 0 && <span>{unread > 9 ? '9+' : unread}</span>}</button>
    <AnimatePresence>{open && <motion.div className="notification-popover" initial={{ opacity: 0, y: -8, scale: .98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: -6, scale: .98 }}>
      <header><div><span className="eyebrow">AniList</span><h3>Notificaciones</h3></div>{unread > 0 && <button onClick={() => dispatch(markAllNotificationsRead())}><CheckCheck /> Leer todas</button>}</header>
      <div className="notification-list">{status === 'loading' && !items.length ? <p>Cargando novedades…</p> : items.length ? items.slice(0, 12).map((item) => <NotificationItem item={item} onSelect={() => select(item)} key={item.id} />) : <p>No hay novedades todavía.</p>}</div>
    </motion.div>}</AnimatePresence></div>
}

function NotificationItem({ item, onSelect }) {
  const Icon = icons[item.type] || Bell; const content = <><div className="notification-cover">{item.coverImage ? <img src={item.coverImage} alt="" /> : <Icon />}</div><div><strong>{item.title}</strong><p>{item.message}</p><time>{new Date(item.occurredAt).toLocaleDateString('es-AR', { day: '2-digit', month: 'short', year: 'numeric' })}</time></div>{!item.read && <i />}</>
  return item.anilistMediaId ? <Link to={`/media/${item.anilistMediaId}`} onClick={onSelect} className={item.read ? '' : 'unread'}>{content}</Link> : <button onClick={onSelect} className={item.read ? '' : 'unread'}>{content}</button>
}
