import { ArrowLeft, Loader2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toApiError } from '../api/client'
import { farmApi, fieldApi, fieldToRequest, recommendationApi, referenceApi } from '../api/endpoints'
import {
  Button,
  ButtonLink,
  ErrorNotice,
  Label,
  Loading,
  Notice,
  PageHeader,
  Panel,
  Section,
  Stat,
} from '../components/ui'
import { fmt, fmtDate, fmtDateTime, strategyLabel, titleCase } from '../format'
import { useAsync } from '../useAsync'
import SoilSection from './SoilSection'

async function loadField(fieldId) {
  const field = await fieldApi.get(fieldId)
  const [farm, soilRecords, history] = await Promise.all([
    farmApi.get(field.farmId),
    fieldApi.soilRecords(fieldId),
    recommendationApi.history(fieldId),
  ])
  return { field, farm, soilRecords, history }
}

export default function FieldPage() {
  const { fieldId } = useParams()
  const { data, error, loading, reload } = useAsync(() => loadField(fieldId), [fieldId])

  if (loading && !data) return <Loading label="Loading field…" />
  if (error) return <ErrorNotice error={error} onRetry={reload} />
  const { field, farm, soilRecords, history } = data

  return (
    <>
      <PageHeader
        back={
          <Link to={`/farms/${farm.id}`} className="mb-2 inline-flex items-center gap-1 text-[13px] text-muted hover:text-ink">
            <ArrowLeft className="size-3.5" aria-hidden />
            {farm.name}
          </Link>
        }
        title={field.name}
        meta={[farm.locationName, `${fmt(field.areaHa)} ha`, field.irrigationType && titleCase(field.irrigationType)]
          .filter(Boolean)
          .join(' · ')}
        actions={
          <ButtonLink variant="tertiary" to={`/history?field=${field.id}`}>
            View history
          </ButtonLink>
        }
      />
      <div className="grid gap-8 lg:grid-cols-[1fr_20rem]">
        <div className="min-w-0">
          <CropStageSection field={field} onSaved={reload} />
          <SoilSection field={field} soilRecords={soilRecords} onSaved={reload} />
        </div>
        <aside className="lg:sticky lg:top-20 lg:self-start">
          <GeneratePanel field={field} history={history} hasSoil={soilRecords.length > 0} />
        </aside>
      </div>
    </>
  )
}

function CropStageSection({ field, onSaved }) {
  const [editing, setEditing] = useState(false)
  return (
    <Section
      title="Crop & growth stage"
      description="The stage decides which split of the season's dose is due now."
      actions={
        !editing && (
          <Button variant="secondary" onClick={() => setEditing(true)}>
            Change
          </Button>
        )
      }
    >
      {editing ? (
        <CropStageForm
          field={field}
          onCancel={() => setEditing(false)}
          onSaved={() => {
            setEditing(false)
            onSaved()
          }}
        />
      ) : (
        <Panel className="px-5 py-4">
          <dl className="grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-4">
            <Stat label="Crop">{field.crop?.name ?? 'Not set'}</Stat>
            <Stat label="Growth stage">{field.growthStage?.name ?? 'Not set'}</Stat>
            <Stat label="Season">{field.season ? titleCase(field.season) : '—'}</Stat>
            <Stat label="Sowing date">{fmtDate(field.sowingDate)}</Stat>
            <Stat label="Area">{fmt(field.areaHa)} ha</Stat>
            <Stat label="Irrigation">{field.irrigationType ? titleCase(field.irrigationType) : 'Not recorded'}</Stat>
            <Stat label="Previous crop">{field.previousCrop ?? '—'}</Stat>
          </dl>
        </Panel>
      )}
    </Section>
  )
}

function CropStageForm({ field, onCancel, onSaved }) {
  const crops = useAsync(() => referenceApi.crops(), [])
  const [cropId, setCropId] = useState(field.crop?.id ?? '')
  const stages = useAsync(() => (cropId ? referenceApi.stages(cropId) : Promise.resolve([])), [cropId])
  const [stageId, setStageId] = useState(field.growthStage?.id ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  async function save(e) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await fieldApi.update(
        field.id,
        fieldToRequest(field, { cropId: Number(cropId) || null, growthStageId: Number(stageId) || null }),
      )
      onSaved()
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Panel className="px-5 py-5">
      <form onSubmit={save}>
        {(error || crops.error) && (
          <div className="mb-4">
            <ErrorNotice error={error ?? crops.error} />
          </div>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <Label htmlFor="crop">Crop</Label>
            <select
              id="crop"
              className="input"
              value={cropId}
              disabled={crops.loading}
              onChange={(e) => {
                setCropId(e.target.value)
                setStageId('')
              }}
            >
              <option value="">Select crop</option>
              {crops.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <Label htmlFor="stage">Growth stage</Label>
            <select
              id="stage"
              className="input"
              value={stageId}
              disabled={!cropId || stages.loading}
              onChange={(e) => setStageId(e.target.value)}
            >
              <option value="">Select stage</option>
              {stages.data?.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.seq}. {s.name}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="mt-6 flex gap-3">
          <Button type="submit" variant="primary" loading={saving} disabled={!cropId || !stageId}>
            Save
          </Button>
          <Button type="button" variant="secondary" onClick={onCancel} disabled={saving}>
            Cancel
          </Button>
        </div>
      </form>
    </Panel>
  )
}

// The real steps of POST /recommendations, in order. One synchronous request, so no step-by-step progress is shown.
const PIPELINE = ['Calculating nutrient requirement', 'Optimising fertilizer plans', 'Estimating yield and scoring plans']

export function GeneratePanel({ field, history, hasSoil }) {
  const navigate = useNavigate()
  const ready = !!(field.crop && field.growthStage)
  const [profile, setProfile] = useState('')
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState(null)

  const requirement = useAsync(
    () => (ready ? fieldApi.requirement(field.id, profile || undefined) : Promise.resolve(null)),
    [field.id, field.crop?.id, field.growthStage?.id, field.updatedAt, profile, ready],
  )
  const kb = useAsync(() => referenceApi.knowledgeBase(), [])

  const profileNames = useMemo(() => {
    const crop = kb.data?.crops?.find((c) => c.code === field.crop?.code)
    return Object.fromEntries((crop?.profiles ?? []).map((p) => [p.code, p.description]))
  }, [kb.data, field.crop?.code])

  useEffect(() => setProfile(''), [field.crop?.id])

  async function generate() {
    setGenerating(true)
    setError(null)
    try {
      const rec = await recommendationApi.generate(field.id, profile || undefined)
      navigate(`/recommendations/${rec.id}`, { state: { recommendation: rec } })
    } catch (err) {
      setError(toApiError(err))
      setGenerating(false)
    }
  }

  const due = requirement.data?.requirementForOptimizer?.kgPerHa
  const auto = requirement.data?.profile

  return (
    <Panel className="px-5 py-5">
      <h2 className="text-[15px] font-semibold">Recommendation</h2>
      {!ready ? (
        <p className="mt-2 text-[13px] text-muted">Set the crop and growth stage to generate a recommendation.</p>
      ) : (
        <>
          <div className="mt-4">
            <p className="text-xs text-muted">Due at this stage</p>
            {requirement.loading ? (
              <p className="mt-1 h-6 text-sm text-faint">…</p>
            ) : requirement.error ? (
              <p className="mt-1 text-[13px] text-danger">{requirement.error.message}</p>
            ) : (
              <dl className="mt-1 grid grid-cols-3 gap-2">
                {[
                  ['N', due?.n],
                  ['P₂O₅', due?.p2o5],
                  ['K₂O', due?.k2o],
                ].map(([k, v]) => (
                  <div key={k}>
                    <dt className="text-xs text-muted">{k}</dt>
                    <dd className="tabular font-medium">
                      {fmt(v)} <span className="text-xs font-normal text-muted">kg/ha</span>
                    </dd>
                  </div>
                ))}
              </dl>
            )}
          </div>
          <div className="mt-4">
            <Label htmlFor="profile">Recommendation profile</Label>
            <select
              id="profile"
              className="input"
              value={profile}
              onChange={(e) => setProfile(e.target.value)}
              disabled={generating || !requirement.data}
            >
              <option value="">Automatic{auto && !profile ? ` (${profileNames[auto.code] ?? auto.code})` : ''}</option>
              {requirement.data?.availableProfiles?.map((code) => (
                <option key={code} value={code}>
                  {profileNames[code] ?? titleCase(code)}
                </option>
              ))}
            </select>
            {auto?.selectedBecause && !profile && <p className="mt-1 text-xs text-muted">{auto.selectedBecause}</p>}
          </div>
          {!hasSoil && (
            <p className="mt-4 text-xs text-warn">No soil test: medium fertility will be assumed.</p>
          )}
          <Button variant="primary" size="lg" className="mt-5 w-full" onClick={generate} loading={generating}>
            {generating ? 'Generating…' : 'Generate Recommendation'}
          </Button>
          {generating && (
            <ol className="mt-4 space-y-1.5 text-[13px] text-muted" aria-live="polite">
              {PIPELINE.map((step) => (
                <li key={step} className="flex items-center gap-2">
                  <Loader2 className="size-3.5 animate-spin text-faint" aria-hidden />
                  {step}
                </li>
              ))}
            </ol>
          )}
          {error && (
            <div className="mt-4">
              <GenerateError error={error} />
            </div>
          )}
        </>
      )}
      {history.length > 0 && (
        <div className="mt-5 border-t border-line pt-4 text-[13px]">
          <p className="text-xs text-muted">Last recommendation</p>
          <Link to={`/recommendations/${history[0].id}`} className="mt-0.5 block text-accent hover:underline">
            {history[0].feasible ? strategyLabel(history[0].selectedStrategy) : 'Not feasible'} ·{' '}
            {fmtDateTime(history[0].createdAt)}
          </Link>
        </div>
      )}
    </Panel>
  )
}

function GenerateError({ error }) {
  if (error.status === 400) {
    return (
      <Notice tone="danger" title="Cannot generate for this field">
        {error.message}
      </Notice>
    )
  }
  return <ErrorNotice error={error} />
}
