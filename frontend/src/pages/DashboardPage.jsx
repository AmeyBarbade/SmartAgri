import { ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { loadOverview } from '../api/overview'
import { EmptyState, ErrorNotice, PageHeader, Panel, Section, Skeleton, StatusDot } from '../components/ui'
import { fmt, fmtDate, fmtDateTime, fmtFixed, strategyLabel } from '../format'
import { useAsync } from '../useAsync'

export default function DashboardPage() {
  const { data, error, loading, reload } = useAsync(loadOverview, [])

  const fieldCount = data?.reduce((n, f) => n + f.fields.length, 0) ?? 0
  const recent = (data ?? [])
    .flatMap(({ fields }) => fields.flatMap(({ field, history }) => history.map((h) => ({ ...h, field }))))
    .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
    .slice(0, 5)

  return (
    <>
      <PageHeader
        title="Dashboard"
        meta={data ? `${data.length} ${data.length === 1 ? 'farm' : 'farms'} · ${fieldCount} ${fieldCount === 1 ? 'field' : 'fields'}` : 'Your farms and fields'}
      />
      {loading && <DashboardSkeleton />}
      {error && <ErrorNotice error={error} onRetry={reload} />}
      {data && data.length === 0 && (
        <EmptyState title="No farms yet">
          Farms and fields are created through the API for this prototype. Run{' '}
          <code className="text-ink-2">python scripts/demo_recommendation.py --seed-demo-user</code> to load demo data.
        </EmptyState>
      )}
      {data?.map(({ farm, fields }) => (
        <Section
          key={farm.id}
          title={farm.name}
          description={farm.locationName ?? 'No location recorded'}
          actions={
            <Link to={`/farms/${farm.id}`} className="text-[13px] text-accent hover:underline">
              View farm
            </Link>
          }
        >
          {fields.length === 0 ? (
            <EmptyState title="No fields on this farm" />
          ) : (
            <FieldTable rows={fields} />
          )}
        </Section>
      ))}
      {recent.length > 0 && (
        <Section title="Recent recommendations">
          <Panel>
            <ul className="divide-y divide-line">
              {recent.map((r) => (
                <li key={r.id}>
                  <Link
                    to={`/recommendations/${r.id}`}
                    className="flex items-center justify-between gap-4 px-4 py-2.5 hover:bg-canvas"
                  >
                    <span className="min-w-0 truncate">
                      <span className="font-medium">{r.field.name}</span>
                      <span className="text-muted"> · {r.feasible ? strategyLabel(r.selectedStrategy) : 'Not feasible'}</span>
                    </span>
                    <span className="flex shrink-0 items-center gap-2 text-[13px] text-muted">
                      {fmtDateTime(r.createdAt)}
                      <ChevronRight className="size-4" aria-hidden />
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </Panel>
        </Section>
      )}
    </>
  )
}

export function FieldTable({ rows }) {
  return (
    <Panel className="overflow-x-auto">
      <table className="w-full min-w-[640px] text-left text-sm">
        <thead className="border-b border-line text-xs text-muted">
          <tr>
            <th className="px-4 py-2 font-medium">Field</th>
            <th className="px-4 py-2 font-medium">Crop · stage</th>
            <th className="px-4 py-2 text-right font-medium">Area</th>
            <th className="px-4 py-2 font-medium">Latest soil test</th>
            <th className="px-4 py-2 font-medium">Last recommendation</th>
            <th className="w-8 px-2" />
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {rows.map(({ field, latestSoil, latestRecommendation }) => (
            <tr key={field.id} className="group hover:bg-canvas">
              <td className="px-4 py-2.5">
                <Link to={`/fields/${field.id}`} className="font-medium text-ink group-hover:text-accent">
                  {field.name}
                </Link>
              </td>
              <td className="px-4 py-2.5 text-ink-2">
                {field.crop ? (
                  <>
                    {field.crop.name}
                    <span className="text-muted"> · {field.growthStage?.name ?? 'stage not set'}</span>
                  </>
                ) : (
                  <span className="text-muted">Not set</span>
                )}
              </td>
              <td className="tabular px-4 py-2.5 text-right">{fmt(field.areaHa)} ha</td>
              <td className="px-4 py-2.5">
                {latestSoil ? (
                  <StatusDot tone="ok">
                    {fmtDate(latestSoil.sampleDate)}
                    <span className="text-muted">· pH {fmtFixed(latestSoil.ph, 1)}</span>
                  </StatusDot>
                ) : (
                  <StatusDot tone="off">
                    <span className="text-muted">No soil test</span>
                  </StatusDot>
                )}
              </td>
              <td className="px-4 py-2.5">
                {latestRecommendation ? (
                  <Link to={`/recommendations/${latestRecommendation.id}`} className="hover:text-accent">
                    {latestRecommendation.feasible ? strategyLabel(latestRecommendation.selectedStrategy) : 'Not feasible'}
                    <span className="text-muted"> · {fmtDate(latestRecommendation.createdAt)}</span>
                  </Link>
                ) : (
                  <span className="text-muted">None yet</span>
                )}
              </td>
              <td className="px-2 text-right">
                <Link to={`/fields/${field.id}`} aria-label={`Open ${field.name}`} className="text-faint hover:text-ink">
                  <ChevronRight className="size-4" />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Panel>
  )
}

function DashboardSkeleton() {
  return (
    <div className="space-y-3">
      <Skeleton className="h-5 w-48" />
      <Skeleton className="h-32 w-full" />
      <Skeleton className="h-5 w-40" />
      <Skeleton className="h-24 w-full" />
    </div>
  )
}
