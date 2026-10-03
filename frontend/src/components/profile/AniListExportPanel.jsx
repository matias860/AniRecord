import { useEffect, useRef, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { ArrowDownToLine, ArrowUpRight, RefreshCw, Upload, Check, Minus, Pause, Image as ImageIcon } from 'lucide-react'
import apiClient from '../../api/apiClient.js'
import { synchronizeAniListLibrary } from '../../features/library/librarySlice.js'
import MediaTypeSwitch from '../common/MediaTypeSwitch.jsx'
import './AniListExportPanel.css'

const statusLabel = (status, mediaType) => ({
  CURRENT: mediaType === 'MANGA' ? 'Leyendo' : 'Viendo',
  COMPLETED: 'Completado',
  PLANNING: 'Pendiente',
  PAUSED: 'Pausado',
  DROPPED: 'Abandonado',
  REPEATING: mediaType === 'MANGA' ? 'Releyendo' : 'Volviendo a ver',
}[status] ?? status)
const unitLabel = (unit, progress) => {
  const labels = unit === 'VOLUMES' ? ['volumen', 'volúmenes'] : unit === 'CHAPTERS' ? ['capítulo', 'capítulos'] : ['episodio', 'episodios']
  return labels[progress === 1 ? 0 : 1]
}
const describe = (value, entry, mediaType) => value
  ? `${statusLabel(value.status, mediaType)} · ${value.progress} ${unitLabel(entry.progressUnit, value.progress)} · ${value.score ? `${value.score / 10}/10` : 'Sin puntuar'}`
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
  const dispatch = useDispatch()
  const accountType = useSelector((state) => state.auth.accountType)
  const importStatus = useSelector((state) => state.library.syncStatus)
  const [mediaType, setMediaType] = useState('ANIME')
  const [preview, setPreview] = useState(null)
  const [phase, setPhase] = useState('idle')
  const [error, setError] = useState('')
  const [completed, setCompleted] = useState(0)
  const [selected, setSelected] = useState(new Set())
  const [total, setTotal] = useState(0)
  const [activeEntry, setActiveEntry] = useState(null)
  const [importResult, setImportResult] = useState(null)
  const [importError, setImportError] = useState('')
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
      const { data } = await apiClient.post(`/library/export/anilist/preview?type=${mediaType}`)
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

  const importFromAniList = async () => {
    if (working || importStatus === 'loading') return
    setImportError(''); setImportResult(null)
    try {
      const result = await dispatch(synchronizeAniListLibrary()).unwrap()
      if (mounted.current) setImportResult(result)
    } catch (reason) {
      if (mounted.current) setImportError(reason.message)
    }
  }

  if (accountType !== 'ANILIST') return null
  const working = phase === 'loading' || phase === 'sending' || phase === 'stopping'
  const reviewing = phase === 'review'
  const additions = preview?.changes.filter((entry) => !entry.before).length ?? 0
  const sending = phase === 'sending' || phase === 'stopping'
  const showTransfer = activeEntry && (sending || phase === 'done' || phase === 'stopped')
  const percent = total ? Math.round(completed / total * 100) : 0
  const itemName = mediaType === 'MANGA' ? 'manga' : 'anime'
  const itemPlural = mediaType === 'MANGA' ? 'mangas' : 'animes'

  const changeMediaType = (nextType) => {
    if (working || nextType === mediaType) return
    setMediaType(nextType); setPreview(null); setPhase('idle'); setError(''); setCompleted(0); setTotal(0); setSelected(new Set()); setActiveEntry(null)
  }

  return <section className="anilist-export-panel" aria-labelledby="anilist-export-heading">
    <div className="section-heading anilist-export-heading"><div><span className="eyebrow">Tu biblioteca conectada</span><h2 id="anilist-export-heading"><ArrowUpRight /> Sincronizar con AniList</h2></div><MediaTypeSwitch value={mediaType} onChange={changeMediaType} ariaLabel="Elegir biblioteca para sincronizar" /></div>
    <p className="anilist-export-intro">Combina tu biblioteca sin retroceder el progreso ni reemplazar puntuaciones existentes en AniList.</p>
    <div className="anilist-import-card">
      <div className="anilist-import-copy">
        <span className="anilist-import-icon"><ArrowDownToLine aria-hidden="true" /></span>
        <span><strong>Traer cambios desde AniList</strong><small>Agrega obras y favoritos que todavía no estén en AniRecord y actualiza sus fichas. Tus entradas existentes conservan progreso, estado y puntaje.</small></span>
      </div>
      <button type="button" className="secondary-button" disabled={working || importStatus === 'loading'} onClick={importFromAniList}>
        <RefreshCw className={importStatus === 'loading' ? 'spin' : ''} />
        {importStatus === 'loading' ? 'Trayendo cambios…' : 'Actualizar desde AniList'}
      </button>
      {importError && <p role="alert" className="anilist-export-error">{importError}</p>}
      {importResult && <p className="anilist-import-result" role="status"><Check aria-hidden="true" /> {importResult.created} nuevas · {importResult.updated} fichas actualizadas · {importResult.personFavoritesCreated || 0} favoritos nuevos</p>}
    </div>
    <details className="anilist-export-details"><summary>Qué se sincroniza</summary><p>Los {itemPlural} nuevos se agregan. En entradas existentes, el progreso solo avanza y la puntuación de AniList se conserva; AniRecord completa una puntuación únicamente cuando allí no hay ninguna. Los estados de pausa o abandono de AniList también se respetan. Fechas, notas y otros datos no se modifican.</p></details>
    {error && <p role="alert" className="anilist-export-error">{error}</p>}
    <div aria-live="polite" role="status">
      {phase === 'loading' && <p className="anilist-export-loading"><RefreshCw aria-hidden="true" /> Comparando tu biblioteca…</p>}
      {showTransfer && <div className={`anilist-export-transfer is-${phase}`}>
        <Cover key={`${activeEntry.entryId}-${activeEntry.coverImage}`} entry={activeEntry} uploading={sending} />
        <div className="anilist-export-transfer-copy">
          <span className="anilist-export-transfer-label">{phase === 'done' ? 'Biblioteca sincronizada' : phase === 'stopped' ? 'Envío detenido' : phase === 'stopping' ? 'Terminando la entrada actual' : 'Enviando a AniList'}</span>
          <strong>{phase === 'done' ? 'Todo al día' : activeEntry.title}</strong>
          <span className="anilist-export-transfer-meta">{phase === 'done' ? `${completed} ${completed === 1 ? `${itemName} sincronizado` : `${itemPlural} sincronizados`}` : `${statusLabel(activeEntry.after.status, mediaType)} · ${activeEntry.before ? 'Actualizando entrada' : 'Agregando a tu lista'}`}</span>
          <small>{sending ? 'Mantené esta página abierta.' : phase === 'stopped' ? 'Lo enviado se conserva. Revisá de nuevo para continuar.' : 'Tus cambios ya están en AniList.'}</small>
        </div>
        <div className="anilist-export-meter">
          <div className="anilist-export-ring" role="progressbar" aria-label={`${itemPlural} confirmados en AniList`} aria-valuemin={0} aria-valuemax={total} aria-valuenow={completed} aria-valuetext={`${completed} de ${total} confirmados`}>
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
        <p>Este es el resultado combinado: AniList conserva sus puntuaciones y nunca recibe un progreso menor.</p>
        <label className="anilist-export-selection"><SelectionCheckbox checked={selected.size === preview.changes.length} partial={selected.size > 0 && selected.size < preview.changes.length} onChange={(event) => setSelected(new Set(event.target.checked ? preview.changes.map((entry) => entry.entryId) : []))} /> Seleccionar todos los cambios</label>
        <div className="anilist-export-changes" role="region" aria-label="Vista previa de cambios" tabIndex={0}>
          {preview.changes.map((entry) => <label key={entry.entryId} className="anilist-export-entry">
            <SelectionCheckbox checked={selected.has(entry.entryId)} onChange={(event) => setSelected((previous) => {
              const next = new Set(previous)
              if (event.target.checked) next.add(entry.entryId); else next.delete(entry.entryId)
              return next
            })} />
            <Cover key={`${entry.entryId}-${entry.coverImage}`} entry={entry} />
            <span><strong>{entry.title}</strong><small>AniList: {describe(entry.before, entry, mediaType)}</small><small>Resultado seguro: {describe(entry.after, entry, mediaType)}</small></span>
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
