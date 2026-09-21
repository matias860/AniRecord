import { useEffect, useRef, useState } from 'react'
import { useSelector } from 'react-redux'
import { ArrowUpRight, RefreshCw, Upload, Check, Minus, Pause, Image as ImageIcon } from 'lucide-react'
import apiClient from '../../api/apiClient.js'
import './AniListExportPanel.css'

const statuses = { CURRENT: 'Viendo', COMPLETED: 'Completado', PLANNING: 'Pendiente', PAUSED: 'Pausado', DROPPED: 'Abandonado', REPEATING: 'Volviendo a ver' }
const describe = (value) => value
  ? `${statuses[value.status] ?? value.status} · ${value.progress} episodios · ${value.score ? `${value.score / 10}/10` : 'Sin puntuar'}`
  : 'No está en tu lista'

function Cover({ entry, uploading = false }) {
  const [failed, setFailed] = useState(false)
  return <div className={`anilist-export-cover${uploading ? ' is-uploading' : ''}`} aria-hidden="true">
    {entry?.coverImage && !failed ? <img src={entry.coverImage} alt="" onError={() => setFailed(true)} /> : <ImageIcon />}
    {uploading && <span className="anilist-export-cover-scan" />}
  </div>
}

function SelectionCheckbox({ checked, partial = false, onChange }) {
  return <>
    <input className="anilist-export-checkbox-input" type="checkbox" checked={checked} onChange={onChange} />
    <span className={`anilist-export-checkbox${partial ? ' is-partial' : ''}`} aria-hidden="true">
      {partial ? <Minus /> : <Check />}
    </span>
  </>
}

export default function AniListExportPanel() {
  const accountType = useSelector((state) => state.auth.accountType)
  const [preview, setPreview] = useState(null)
  const [phase, setPhase] = useState('idle')
  const [error, setError] = useState('')
  const [completed, setCompleted] = useState(0)
  const [selected, setSelected] = useState(new Set())
  const [total, setTotal] = useState(0)
  const [activeEntry, setActiveEntry] = useState(null)
  const stop = useRef(false)
  const mounted = useRef(true)
  const busy = useRef(false)

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; stop.current = true }
  }, [])

  const review = async () => {
    if (busy.current) return
    busy.current = true
    setError(''); setPhase('loading'); setPreview(null); setCompleted(0); setActiveEntry(null)
    try {
      const { data } = await apiClient.post('/library/export/anilist/preview')
      if (!mounted.current) return
      setPreview(data); setSelected(new Set(data.changes.map((entry) => entry.entryId))); setPhase('review')
    } catch (reason) {
      if (mounted.current) { setError(reason.message); setPhase('idle') }
    } finally { busy.current = false }
  }

  const apply = async () => {
    if (busy.current || !preview || !selected.size) return
    busy.current = true; stop.current = false
    const changes = preview.changes.filter((entry) => selected.has(entry.entryId))
    setError(''); setPhase('sending'); setCompleted(0); setTotal(changes.length)
    let count = 0
    let currentEntry = null
    try {
      for (const entry of changes) {
        if (stop.current) break
        currentEntry = entry
        if (mounted.current) setActiveEntry(entry)
        await apiClient.post(`/library/export/anilist/${preview.id}/entries/${entry.entryId}`)
        count += 1
        if (mounted.current) setCompleted(count)
      }
      if (mounted.current) setPhase(count === changes.length ? 'done' : 'stopped')
    } catch (reason) {
      if (mounted.current) { setError(`${currentEntry ? `${currentEntry.title}: ` : ''}${reason.message}`); setPhase('stopped') }
    } finally { busy.current = false }
  }

  if (accountType !== 'ANILIST') return null
  const working = phase === 'loading' || phase === 'sending' || phase === 'stopping'
  const reviewing = phase === 'review'
  const additions = preview?.changes.filter((entry) => !entry.before).length ?? 0
  const sending = phase === 'sending' || phase === 'stopping'
  const showTransfer = activeEntry && (sending || phase === 'done' || phase === 'stopped')
  const percent = total ? Math.round(completed / total * 100) : 0

  return <section className="anilist-export-panel" aria-labelledby="anilist-export-heading">
    <div className="section-heading"><div><span className="eyebrow">Tu biblioteca conectada</span><h2 id="anilist-export-heading"><ArrowUpRight /> Sincronizar con AniList</h2></div></div>
    <p className="anilist-export-intro">Tu estado, episodios y puntuación, de AniRecord a AniList.</p>
    <details className="anilist-export-details"><summary>Qué se sincroniza</summary><p>Envío manual de animes, con revisión antes de confirmar. No se borran entradas. Manga, fechas, notas y otros datos se conservan.</p></details>
    {error && <p role="alert" className="anilist-export-error">{error}</p>}
    <div aria-live="polite" role="status">
      {phase === 'loading' && <p className="anilist-export-loading"><RefreshCw aria-hidden="true" /> Comparando tu biblioteca…</p>}
      {showTransfer && <div className={`anilist-export-transfer is-${phase}`}>
        <Cover key={`${activeEntry.entryId}-${activeEntry.coverImage}`} entry={activeEntry} uploading={sending} />
        <div className="anilist-export-transfer-copy">
          <span className="anilist-export-transfer-label">{phase === 'done' ? 'Biblioteca sincronizada' : phase === 'stopped' ? 'Envío detenido' : phase === 'stopping' ? 'Terminando la entrada actual' : 'Enviando a AniList'}</span>
          <strong>{phase === 'done' ? 'Todo al día' : activeEntry.title}</strong>
          <span className="anilist-export-transfer-meta">{phase === 'done' ? `${completed} ${completed === 1 ? 'anime sincronizado' : 'animes sincronizados'}` : `${statuses[activeEntry.after.status]} · ${activeEntry.before ? 'Actualizando entrada' : 'Agregando a tu lista'}`}</span>
          <small>{sending ? 'Mantené esta página abierta.' : phase === 'stopped' ? 'Lo enviado se conserva. Revisá de nuevo para continuar.' : 'Tus cambios ya están en AniList.'}</small>
        </div>
        <div className="anilist-export-meter">
          <div className="anilist-export-ring" role="progressbar" aria-label="Animes confirmados en AniList" aria-valuemin={0} aria-valuemax={total} aria-valuenow={completed} aria-valuetext={`${completed} de ${total} confirmados`}>
            <svg viewBox="0 0 48 48" aria-hidden="true"><circle className="ring-track" cx="24" cy="24" r="20" /><circle className="ring-value" cx="24" cy="24" r="20" pathLength="100" strokeDasharray={`${percent} 100`} /></svg>
            {phase === 'done' ? <Check aria-hidden="true" /> : phase === 'stopped' ? <Pause aria-hidden="true" /> : <Upload className="anilist-export-upload-icon" aria-hidden="true" />}
          </div>
          <span>{completed}<span> / {total}</span></span>
        </div>
      </div>}
    </div>
    {reviewing && preview && <>
      <p><strong>Destino: {preview.username}</strong> · {additions} para agregar · {preview.changes.length - additions} para actualizar · {preview.unchanged} sin cambios.</p>
      {preview.changes.length ? <>
        <p>Los valores de AniRecord reemplazarán los de AniList en las entradas seleccionadas, incluso si el progreso o la puntuación son menores.</p>
        <label className="anilist-export-selection"><SelectionCheckbox checked={selected.size === preview.changes.length} partial={selected.size > 0 && selected.size < preview.changes.length} onChange={(event) => setSelected(new Set(event.target.checked ? preview.changes.map((entry) => entry.entryId) : []))} /> Seleccionar todos los cambios</label>
        <div className="anilist-export-changes" role="region" aria-label="Vista previa de cambios" tabIndex={0}>
          {preview.changes.map((entry) => <label key={entry.entryId} className="anilist-export-entry">
            <SelectionCheckbox checked={selected.has(entry.entryId)} onChange={(event) => setSelected((previous) => {
              const next = new Set(previous)
              if (event.target.checked) next.add(entry.entryId); else next.delete(entry.entryId)
              return next
            })} />
            <Cover key={`${entry.entryId}-${entry.coverImage}`} entry={entry} />
            <span><strong>{entry.title}</strong><small>AniList: {describe(entry.before)}</small><small>AniRecord: {describe(entry.after)}</small></span>
          </label>)}
        </div>
        <p className="anilist-export-note">Se envían gradualmente para respetar los límites de AniList. Puedes detener el proceso entre entradas.</p>
      </> : <p>Tus animes ya están al día. No hay nada que enviar.</p>}
    </>}
    <div className="anilist-export-actions">
      {!working && <button type="button" className="reset-theme" onClick={review}><RefreshCw /> {preview ? 'Revisar de nuevo' : 'Revisar cambios'}</button>}
      {reviewing && selected.size > 0 && <button type="button" className="primary-button" onClick={apply}>Confirmar y enviar {selected.size} {selected.size === 1 ? 'cambio' : 'cambios'}</button>}
      {(phase === 'sending' || phase === 'stopping') && <button type="button" className="reset-theme" disabled={phase === 'stopping'} onClick={() => { stop.current = true; setPhase('stopping') }}>Detener envío</button>}
    </div>
  </section>
}
