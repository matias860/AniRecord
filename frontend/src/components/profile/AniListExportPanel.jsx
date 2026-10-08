import { useEffect, useRef, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { ArrowDownToLine, ArrowRight, ArrowUpRight, RefreshCw, Upload, Check, Minus, Pause, ShieldCheck, Image as ImageIcon } from 'lucide-react'
import apiClient from '../../api/apiClient.js'
import { fetchLibrary } from '../../features/library/librarySlice.js'
import { fetchPersonFavorites } from '../../features/favorites/favoritesSlice.js'
import MediaTypeSwitch from '../common/MediaTypeSwitch.jsx'
import './AniListExportPanel.css'

const statusLabel = (status, mediaType) => ({
  CURRENT: mediaType === 'MANGA' ? 'Leyendo' : 'Viendo',
  IN_PROGRESS: mediaType === 'MANGA' ? 'Leyendo' : 'Viendo',
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
const describeImport = (value, entry) => value
  ? `${statusLabel(value.status, entry.mediaType)} · ${value.progress} ${unitLabel(entry.mediaFormat === 'NOVEL' ? 'VOLUMES' : entry.mediaType === 'MANGA' ? 'CHAPTERS' : 'EPISODES', value.progress)} · ${value.score ? `${value.score}/10` : 'Sin puntuar'}`
  : 'No está en AniRecord'

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
  const [mediaType, setMediaType] = useState('ANIME')
  const [preview, setPreview] = useState(null)
  const [phase, setPhase] = useState('idle')
  const [error, setError] = useState('')
  const [completed, setCompleted] = useState(0)
  const [selected, setSelected] = useState(new Set())
  const [total, setTotal] = useState(0)
  const [activeEntry, setActiveEntry] = useState(null)
  const [importResult, setImportResult] = useState(null)
  const [importPreview, setImportPreview] = useState(null)
  const [importSelected, setImportSelected] = useState(new Set())
  const [importError, setImportError] = useState('')
  const [importPhase, setImportPhase] = useState('idle')
  const [importCompleted, setImportCompleted] = useState(0)
  const [importTotal, setImportTotal] = useState(0)
  const [importActiveEntry, setImportActiveEntry] = useState(null)
  const stop = useRef(false)
  const importStop = useRef(false)
  const mounted = useRef(true)
  const busy = useRef(false)

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; stop.current = true; importStop.current = true }
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

  const reviewImport = async () => {
    if (working || importWorking) return
    importStop.current = false
    setImportError(''); setImportResult(null); setImportPreview(null); setImportSelected(new Set()); setImportPhase('loading'); setImportCompleted(0); setImportTotal(0); setImportActiveEntry(null)
    try {
      const { data: plan } = await apiClient.post('/library/import/anilist/preview')
      if (!mounted.current) return
      const changes = plan.changes ?? []
      setImportPreview(plan)
      setImportSelected(new Set(changes.map((entry) => entry.anilistId)))
      setImportPhase('review')
    } catch (reason) {
      if (mounted.current) { setImportError(reason.message); setImportPhase('idle') }
    }
  }

  const applyImport = async () => {
    if (working || importWorking || !importPreview) return
    importStop.current = false
    const changes = importPreview.changes.filter((entry) => importSelected.has(entry.anilistId))
    const batchSize = importPreview.batchSize || 20
    setImportError(''); setImportResult(null); setImportCompleted(0); setImportTotal(changes.length); setImportPhase('sending')
    try {
      let processed = 0
      let created = 0
      let updated = 0
      for (let from = 0; from < changes.length; from += batchSize) {
        if (importStop.current) break
        const batchEntries = changes.slice(from, from + batchSize)
        setImportActiveEntry(batchEntries[0])
        const { data: batch } = await apiClient.post(`/library/import/anilist/${importPreview.id}/selection`, {
          anilistIds: batchEntries.map((entry) => entry.anilistId),
        })
        processed += batch.processed
        created += batch.created
        updated += batch.updated
        if (mounted.current) setImportCompleted(processed)
      }
      if (importStop.current) {
        if (mounted.current) setImportPhase('stopped')
        return
      }
      const { data: favorites } = await apiClient.post(`/library/import/anilist/${importPreview.id}/complete`)
      await Promise.all([
        dispatch(fetchLibrary()).unwrap(),
        dispatch(fetchPersonFavorites(true)).unwrap(),
      ])
      if (mounted.current) {
        setImportResult({ created, updated, preserved: importPreview.changes.length - changes.length, personFavoritesCreated: favorites.personFavoritesCreated || 0 })
        setImportPhase('done')
      }
    } catch (reason) {
      if (mounted.current) { setImportError(reason.message); setImportPhase('stopped') }
    }
  }

  if (accountType !== 'ANILIST') return null
  const importWorking = importPhase === 'loading' || importPhase === 'sending' || importPhase === 'stopping'
  const working = phase === 'loading' || phase === 'sending' || phase === 'stopping'
  const anyWorking = working || importWorking
  const reviewing = phase === 'review'
  const additions = preview?.changes.filter((entry) => !entry.before).length ?? 0
  const sending = phase === 'sending' || phase === 'stopping'
  const showTransfer = activeEntry && (sending || phase === 'done' || phase === 'stopped')
  const percent = total ? Math.round(completed / total * 100) : 0
  const importPercent = importTotal ? Math.round(importCompleted / importTotal * 100) : 0
  const itemName = mediaType === 'MANGA' ? 'manga' : 'anime'
  const itemPlural = mediaType === 'MANGA' ? 'mangas' : 'animes'

  const changeMediaType = (nextType) => {
    if (anyWorking || nextType === mediaType) return
    setMediaType(nextType); setPreview(null); setPhase('idle'); setError(''); setCompleted(0); setTotal(0); setSelected(new Set()); setActiveEntry(null)
  }

  return <section className="anilist-sync-section" aria-labelledby="anilist-sync-heading">
    <div className="section-heading anilist-sync-heading"><div><span className="eyebrow">Tu biblioteca conectada</span><h2 id="anilist-sync-heading"><RefreshCw /> Sincronización con AniList</h2><p>Elegí la dirección según dónde hiciste los cambios.</p></div></div>
    <div className="anilist-sync-grid">
      <article className="anilist-sync-card is-export" aria-labelledby="anilist-export-heading">
        <div className="anilist-direction-route" aria-label="De AniRecord hacia AniList"><span>AniRecord</span><ArrowRight /><span>AniList</span></div>
        <div className="anilist-card-title"><span className="anilist-card-icon"><ArrowUpRight /></span><div><small>Enviar cambios</small><h3 id="anilist-export-heading">Actualizar AniList</h3></div></div>
        <p className="anilist-card-description">Combina tu biblioteca sin retroceder el progreso ni reemplazar puntuaciones que ya existan en AniList.</p>
        <div className="anilist-safe-note"><ShieldCheck /><span><strong>Envío protegido</strong><small>Primero revisás cada cambio y elegís cuáles confirmar.</small></span></div>
        <MediaTypeSwitch value={mediaType} onChange={changeMediaType} ariaLabel="Elegir biblioteca para enviar a AniList" />
        <details className="anilist-export-details"><summary>Qué se envía</summary><p>Los {itemPlural} nuevos se agregan. En entradas existentes, el progreso solo avanza y la puntuación de AniList se conserva; AniRecord completa una puntuación únicamente cuando allí no hay ninguna. Los estados de pausa o abandono de AniList también se respetan. Fechas, notas y otros datos no se modifican.</p></details>
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
          </> : <p>Tus {itemPlural} ya están al día. No hay nada que enviar.</p>}
        </>}
        <div className="anilist-export-actions">
          {!anyWorking && <button type="button" className="anilist-sync-button is-export" onClick={review}><ArrowUpRight /> <span>{preview ? 'Revisar de nuevo' : 'Revisar cambios para enviar'}</span></button>}
          {reviewing && selected.size > 0 && !importWorking && <button type="button" className="anilist-sync-button is-confirm" onClick={apply}><Upload /> <span>Confirmar y enviar {selected.size} {selected.size === 1 ? 'cambio' : 'cambios'}</span></button>}
          {(phase === 'sending' || phase === 'stopping') && <button type="button" className="anilist-sync-button is-stop" disabled={phase === 'stopping'} onClick={() => { stop.current = true; setPhase('stopping') }}><Pause /> <span>Detener envío</span></button>}
        </div>
      </article>

      <article className="anilist-sync-card is-import" aria-labelledby="anilist-import-heading">
        <div className="anilist-direction-route" aria-label="De AniList hacia AniRecord"><span>AniList</span><ArrowRight /><span>AniRecord</span></div>
        <div className="anilist-card-title"><span className="anilist-card-icon"><ArrowDownToLine /></span><div><small>Traer cambios</small><h3 id="anilist-import-heading">Actualizar AniRecord</h3></div></div>
        <p className="anilist-card-description">Combina tu biblioteca sin retroceder el progreso ni reemplazar puntuaciones que ya existan en AniRecord.</p>
        <div className="anilist-safe-note"><ShieldCheck /><span><strong>Importación protegida</strong><small>Primero revisás el resultado seguro y elegís cuáles cambios confirmar.</small></span></div>
        <details className="anilist-export-details"><summary>Qué se trae</summary><p>Las obras nuevas se agregan. En entradas existentes, el progreso solo avanza y la puntuación de AniRecord se conserva; AniList completa una puntuación únicamente cuando aquí no hay ninguna. Los estados de pausa o abandono de AniRecord también se respetan. Fechas, repeticiones y otros datos existentes no se modifican.</p></details>
        <div className="anilist-import-spacer" />
        {importError && <p role="alert" className="anilist-export-error">{importError}</p>}
        {importPhase === 'loading' && <p className="anilist-export-loading" role="status"><RefreshCw aria-hidden="true" /> Comparando tu biblioteca de AniList…</p>}
        {importPhase === 'review' && importPreview && <div className="anilist-import-review">
          <p><strong>{importPreview.changes.length} cambios seguros</strong> · {importPreview.unchanged} entradas no necesitan cambios.</p>
          {importPreview.changes.length ? <>
            <p className="anilist-import-warning"><ShieldCheck /> Este es el resultado combinado: AniRecord conserva sus puntuaciones y nunca recibe un progreso menor.</p>
            <label className="anilist-export-selection"><SelectionCheckbox checked={importSelected.size === importPreview.changes.length} partial={importSelected.size > 0 && importSelected.size < importPreview.changes.length} onChange={(event) => setImportSelected(new Set(event.target.checked ? importPreview.changes.map((entry) => entry.anilistId) : []))} /> Seleccionar todas las diferencias</label>
            <div className="anilist-export-changes" role="region" aria-label="Diferencias entre AniList y AniRecord" tabIndex={0}>
              {importPreview.changes.map((entry) => <label key={entry.anilistId} className="anilist-export-entry">
                <SelectionCheckbox checked={importSelected.has(entry.anilistId)} onChange={(event) => setImportSelected((previous) => {
                  const next = new Set(previous)
                  if (event.target.checked) next.add(entry.anilistId); else next.delete(entry.anilistId)
                  return next
                })} />
                <Cover key={`${entry.anilistId}-${entry.coverImage}`} entry={entry} />
                <span><strong>{entry.title}</strong><small>AniRecord: {describeImport(entry.before, entry)}</small><small>Resultado seguro: {describeImport(entry.after, entry)}</small>{entry.addition && <em>Nueva: seleccionada automáticamente</em>}</span>
              </label>)}
            </div>
          </> : <p>No hay progreso, estados ni puntuaciones diferentes para importar.</p>}
        </div>}
        {importActiveEntry && ['sending', 'stopping', 'done', 'stopped'].includes(importPhase) && <div className={`anilist-export-transfer is-${importPhase}`} role="status" aria-live="polite">
          <Cover key={`${importActiveEntry.anilistId}-${importActiveEntry.coverImage}`} entry={importActiveEntry} uploading={importPhase === 'sending' || importPhase === 'stopping'} />
          <div className="anilist-export-transfer-copy">
            <span className="anilist-export-transfer-label">{importPhase === 'done' ? 'Biblioteca sincronizada' : importPhase === 'stopped' ? 'Importación detenida' : 'Trayendo desde AniList'}</span>
            <strong>{importPhase === 'done' ? 'Todo al día' : importActiveEntry.title}</strong>
            <span className="anilist-export-transfer-meta">{importActiveEntry.addition ? 'Agregando a tu biblioteca' : 'Actualizando entrada'}</span>
            <small>{importPhase === 'sending' ? 'Los cambios confirmados se guardan por partes.' : importPhase === 'stopped' ? 'Lo que ya se importó se conserva.' : 'Los cambios de AniList ya están en AniRecord.'}</small>
          </div>
          <div className="anilist-export-meter">
            <div className="anilist-export-ring" role="progressbar" aria-label="Entradas importadas desde AniList" aria-valuemin={0} aria-valuemax={importTotal} aria-valuenow={importCompleted} aria-valuetext={`${importCompleted} de ${importTotal} importadas`}>
              <svg viewBox="0 0 48 48" aria-hidden="true"><circle className="ring-track" cx="24" cy="24" r="20" /><circle className="ring-value" cx="24" cy="24" r="20" pathLength="100" strokeDasharray={`${importPercent} 100`} /></svg>
              {importPhase === 'done' ? <Check aria-hidden="true" /> : importPhase === 'stopped' ? <Pause aria-hidden="true" /> : <ArrowDownToLine className="anilist-export-upload-icon" aria-hidden="true" />}
            </div>
            <span>{importCompleted}<span> / {importTotal}</span></span>
          </div>
        </div>}
        {importResult && <p className="anilist-import-result" role="status"><Check aria-hidden="true" /> {importResult.created} nuevas · {importResult.updated} elegidas y actualizadas · {importResult.preserved} conservadas en AniRecord · {importResult.personFavoritesCreated} favoritos nuevos</p>}
        {!importWorking && importPhase !== 'review' && <button type="button" className="anilist-sync-button is-import" disabled={working} onClick={reviewImport}>
          <ArrowDownToLine />
          <span>{importPhase === 'done' || importPhase === 'stopped' ? 'Comparar de nuevo' : 'Comparar con AniRecord'}</span>
        </button>}
        {importPhase === 'review' && <div className="anilist-export-actions">
          <button type="button" className="anilist-sync-button is-import" disabled={working} onClick={applyImport}><ArrowDownToLine /><span>Aplicar {importSelected.size} {importSelected.size === 1 ? 'cambio seguro' : 'cambios seguros'}</span></button>
          <button type="button" className="anilist-sync-button is-preserve" onClick={() => { setImportPreview(null); setImportSelected(new Set()); setImportPhase('idle') }}><ShieldCheck /><span>Conservar AniRecord</span></button>
        </div>}
        {(importPhase === 'sending' || importPhase === 'stopping') && <button type="button" className="anilist-sync-button is-stop" disabled={importPhase === 'stopping'} onClick={() => { importStop.current = true; setImportPhase('stopping') }}><Pause /> <span>Detener importación</span></button>}
      </article>
    </div>
  </section>
}
