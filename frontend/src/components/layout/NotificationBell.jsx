import { AnimatePresence, motion } from 'framer-motion'
import { Bell, CheckCheck, GitBranch, Grid3X3, Info, Play, RefreshCw, Sparkles, Trash2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { Link } from 'react-router-dom'
import { fetchNotifications, fetchUnreadCount, markAllNotificationsRead, markNotificationRead, syncNotifications } from '../../features/notifications/notificationSlice.js'
import { notificationSyncInterval } from '../../lib/notificationSync.js'

const icons = { FIRST_EPISODE: Play, AIRING_EPISODE: Play, RELATED_MEDIA_ADDITION: GitBranch, MEDIA_DATA_CHANGE: RefreshCw, MEDIA_MERGE: Info, MEDIA_DELETION: Trash2, TOP_NINE_AVAILABLE: Grid3X3, WEEKLY_RECOMMENDATION_AVAILABLE: Sparkles }
export default function NotificationBell({ hovered, onMouseEnter }) {
  const [open, setOpen] = useState(false); const root = useRef(null); const dispatch = useDispatch()
  const { items, unreadCount: unread, status, error } = useSelector((state) => state.notifications)
  useEffect(() => {
    let timer = null
    const stop = () => { if (timer) window.clearInterval(timer); timer = null }
    const update = () => {
      stop()
      if (document.visibilityState !== 'visible') return
      dispatch(fetchUnreadCount())
      timer = window.setInterval(() => dispatch(fetchUnreadCount()), notificationSyncInterval)
    }
    document.addEventListener('visibilitychange', update)
    update()
    return () => { stop(); document.removeEventListener('visibilitychange', update) }
  }, [dispatch])
  useEffect(() => { const close = (event) => { if (!root.current?.contains(event.target)) setOpen(false) }; document.addEventListener('mousedown', close); return () => document.removeEventListener('mousedown', close) }, [])
  const select = (item) => { if (!item.read) dispatch(markNotificationRead(item.id)); setOpen(false) }
  const synchronize = () => dispatch(syncNotifications()).then(() => dispatch(fetchUnreadCount()))
  const toggle = async () => {
    const next = !open
    setOpen(next)
    if (!next) return
    const result = status !== 'loading' ? await dispatch(fetchNotifications()) : null
    const loaded = fetchNotifications.fulfilled.match(result)
    if (loaded) synchronize()
  }
  const retry = async () => {
    const result = await dispatch(fetchNotifications())
    if (fetchNotifications.fulfilled.match(result)) synchronize()
  }
  return <div className="notification-root" ref={root} onMouseEnter={onMouseEnter}>{hovered && <motion.span className="nav-hover" layoutId="navbar-hover" transition={{ type: 'spring', stiffness: 420, damping: 35 }} />}<button className="notification-button" onClick={toggle} aria-label={unread > 0 ? `Notificaciones sin leer: ${unread}` : 'Notificaciones'} aria-expanded={open} aria-controls="notification-popover"><Bell />{unread > 0 && <span className="notification-unread-dot" aria-hidden="true" />}</button>
    <AnimatePresence>{open && <motion.div id="notification-popover" role="dialog" aria-label="Notificaciones" className="notification-popover" initial={{ opacity: 0, y: -8, scale: .98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: -6, scale: .98 }}>
      <header><div><span className="eyebrow">Novedades</span><h3>Notificaciones</h3></div>{unread > 0 && <button onClick={() => dispatch(markAllNotificationsRead())}><CheckCheck /> Leer todas</button>}</header>
      <div className="notification-list">{status === 'loading' && !items.length ? <p>Cargando novedades…</p> : error && !items.length ? <div className="notification-error"><p>{error}</p><button onClick={retry}>Reintentar</button></div> : items.length ? items.slice(0, 10).map((item) => <NotificationItem item={item} onSelect={() => select(item)} key={item.id} />) : <p>No hay novedades todavía.</p>}</div>
    </motion.div>}</AnimatePresence></div>
}

function NotificationItem({ item, onSelect }) {
  const Icon = icons[item.type] || Bell; const content = <><div className="notification-cover">{item.coverImage ? <img src={item.coverImage} alt="" /> : <Icon />}</div><div><strong>{item.title}</strong><p>{item.message}</p><time>{new Date(item.occurredAt).toLocaleDateString('es-AR', { day: '2-digit', month: 'short', year: 'numeric' })}</time></div>{!item.read && <i />}</>
  const homeNotification = item.type === 'TOP_NINE_AVAILABLE' || item.type === 'WEEKLY_RECOMMENDATION_AVAILABLE'
  return item.anilistMediaId ? <Link to={`/media/${item.anilistMediaId}`} onClick={onSelect} className={item.read ? '' : 'unread'}>{content}</Link> : homeNotification ? <Link to="/" onClick={onSelect} className={item.read ? '' : 'unread'}>{content}</Link> : <button onClick={onSelect} className={item.read ? '' : 'unread'}>{content}</button>
}
