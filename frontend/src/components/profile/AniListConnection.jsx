import { CheckCircle2, Link2, LogOut } from 'lucide-react'
import { useEffect, useState } from 'react'
import apiClient from '../../api/apiClient.js'

export default function AniListConnection() {
  const [connection, setConnection] = useState(null); const [error, setError] = useState(null)
  useEffect(() => { apiClient.get('/anilist/oauth/status').then(({ data }) => setConnection(data)).catch((reason) => setError(reason.message)) }, [])
  const connect = () => { window.location.href = `${import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080'}/api/anilist/oauth/connect` }
  const disconnect = async () => { await apiClient.delete('/anilist/oauth'); setConnection({ connected: false, username: null }) }
  return <section className={`anilist-connection ${connection?.connected ? 'connected' : ''}`}><div className="anilist-logo">A</div><div><span className="eyebrow">Sincronización</span><h3>{connection?.connected ? `Conectado como ${connection.username}` : 'Conectar con AniList'}</h3><p>{connection?.connected ? 'Recibirás anuncios, cambios importantes y solamente el estreno del primer episodio.' : 'Necesario para consultar tus notificaciones personales de AniList.'}</p>{error && <small>{error}</small>}</div>{connection?.connected ? <button className="connection-secondary" onClick={disconnect}><LogOut /> Desconectar</button> : <button className="primary-button" disabled={!connection} onClick={connect}><Link2 /> Conectar</button>}{connection?.connected && <CheckCircle2 className="connection-check" />}</section>
}
