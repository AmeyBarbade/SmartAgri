import { ChevronRight, MapPin } from 'lucide-react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { farmApi } from '../api/endpoints'
import { EmptyState, ErrorNotice, Loading, PageHeader, Panel, Skeleton } from '../components/ui'
import { fmt, fmtDate, titleCase } from '../format'
import { useAsync } from '../useAsync'

export default function FarmsPage() {
  const { farmId } = useParams()
  const farms = useAsync(() => farmApi.list(), [])

  if (!farmId && farms.data?.length) return <Navigate to={`/farms/${farms.data[0].id}`} replace />

  return (
    <>
      <PageHeader title="Farms & fields" meta="Select a field to review its soil and crop stage and generate a recommendation." />
      {farms.error && <ErrorNotice error={farms.error} onRetry={farms.reload} />}
      {farms.loading && <Loading />}
      {farms.data?.length === 0 && (
        <EmptyState title="No farms yet">
          Run <code className="text-ink-2">python scripts/demo_recommendation.py --seed-demo-user</code> to load demo data.
        </EmptyState>
      )}
      {farms.data?.length > 0 && (
        <div className="grid gap-6 md:grid-cols-[15rem_1fr]">
          <nav aria-label="Farms">
            <p className="mb-2 text-xs font-medium text-muted">Farms</p>
            <ul className="space-y-1">
              {farms.data.map((farm) => {
                const active = String(farm.id) === farmId
                return (
                  <li key={farm.id}>
                    <Link
                      to={`/farms/${farm.id}`}
                      aria-current={active ? 'page' : undefined}
                      className={`block rounded-md border px-3 py-2 ${
                        active ? 'border-accent-line bg-accent-soft' : 'border-transparent hover:bg-surface'
                      }`}
                    >
                      <span className={`block text-sm font-medium ${active ? 'text-accent' : 'text-ink'}`}>{farm.name}</span>
                      <span className="block text-xs text-muted">
                        {farm.locationName ?? 'No location'} · {farm.fieldCount} {farm.fieldCount === 1 ? 'field' : 'fields'}
                      </span>
                    </Link>
                  </li>
                )
              })}
            </ul>
          </nav>
          {farmId && <FarmFields farmId={farmId} farm={farms.data.find((f) => String(f.id) === farmId)} />}
        </div>
      )}
    </>
  )
}

function FarmFields({ farmId, farm }) {
  const fields = useAsync(() => farmApi.fields(farmId), [farmId])
  if (!farm) return <EmptyState title="Farm not found" />
  return (
    <div className="min-w-0">
      <div className="mb-3 flex items-baseline justify-between">
        <h2 className="text-[15px] font-semibold">{farm.name}</h2>
        {farm.locationName && (
          <span className="inline-flex items-center gap-1 text-[13px] text-muted">
            <MapPin className="size-3.5" aria-hidden />
            {farm.locationName}
          </span>
        )}
      </div>
      {fields.error && <ErrorNotice error={fields.error} onRetry={fields.reload} />}
      {fields.loading && (
        <div className="grid gap-3 sm:grid-cols-2">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
      )}
      {fields.data?.length === 0 && <EmptyState title="No fields on this farm" />}
      <div className="grid gap-3 lg:grid-cols-2">
        {fields.data?.map((field) => (
          <Link key={field.id} to={`/fields/${field.id}`} className="group">
            <Panel className="flex h-full items-center justify-between gap-4 px-4 py-3.5 group-hover:border-accent-line">
              <div className="min-w-0">
                <p className="truncate font-medium group-hover:text-accent">{field.name}</p>
                <p className="mt-0.5 text-[13px] text-ink-2">
                  {field.crop ? `${field.crop.name} · ${field.growthStage?.name ?? 'stage not set'}` : 'Crop not set'}
                </p>
                <p className="mt-1 text-xs text-muted">
                  {fmt(field.areaHa)} ha
                  {field.irrigationType && ` · ${titleCase(field.irrigationType)}`}
                  {field.sowingDate && ` · sown ${fmtDate(field.sowingDate)}`}
                </p>
              </div>
              <ChevronRight className="size-4 shrink-0 text-faint group-hover:text-accent" aria-hidden />
            </Panel>
          </Link>
        ))}
      </div>
    </div>
  )
}
