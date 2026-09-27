import { ChevronRight } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { loadOverview } from '../api/overview'
import { EmptyState, ErrorNotice, Label, Loading, PageHeader, Panel, StatusDot } from '../components/ui'
import { fmtDateTime, strategyLabel, titleCase } from '../format'
import { useAsync } from '../useAsync'

export default function HistoryPage() {
  const [params, setParams] = useSearchParams()
  const fieldFilter = params.get('field') ?? ''
  const { data, error, loading, reload } = useAsync(loadOverview, [])

  const fields = (data ?? []).flatMap(({ farm, fields }) => fields.map((f) => ({ ...f, farm })))
  const rows = fields
    .filter((f) => !fieldFilter || String(f.field.id) === fieldFilter)
    .flatMap((f) => f.history.map((h) => ({ ...h, field: f.field, farm: f.farm })))
    .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))

  return (
    <>
      <PageHeader title="Recommendation history" meta="Every generated recommendation is stored exactly as it was computed." />
      {loading && <Loading />}
      {error && <ErrorNotice error={error} onRetry={reload} />}
      {data && (
        <>
          <div className="mb-4 max-w-xs">
            <Label htmlFor="history-field">Field</Label>
            <select
              id="history-field"
              className="input"
              value={fieldFilter}
              onChange={(e) => setParams(e.target.value ? { field: e.target.value } : {})}
            >
              <option value="">All fields</option>
              {fields.map(({ field, farm }) => (
                <option key={field.id} value={field.id}>
                  {field.name} ({farm.name})
                </option>
              ))}
            </select>
          </div>
          {rows.length === 0 ? (
            <EmptyState title="No recommendations yet">
              Open a field and choose Generate Recommendation. Results appear here.
            </EmptyState>
          ) : (
            <Panel className="overflow-x-auto">
              <table className="w-full min-w-[640px] text-left text-sm">
                <thead className="border-b border-line text-xs text-muted">
                  <tr>
                    <th className="px-4 py-2 font-medium">Generated</th>
                    <th className="px-4 py-2 font-medium">Field</th>
                    <th className="px-4 py-2 font-medium">Crop · stage</th>
                    <th className="px-4 py-2 font-medium">Result</th>
                    <th className="px-4 py-2 font-medium">Scoring</th>
                    <th className="w-8 px-2" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {rows.map((r) => (
                    <tr key={r.id} className="group hover:bg-canvas">
                      <td className="tabular px-4 py-2.5">
                        <Link to={`/recommendations/${r.id}`} className="font-medium group-hover:text-accent">
                          {fmtDateTime(r.createdAt)}
                        </Link>
                      </td>
                      <td className="px-4 py-2.5">{r.field.name}</td>
                      <td className="px-4 py-2.5 text-ink-2">
                        {titleCase(r.cropCode)} · {titleCase(r.stageCode)}
                      </td>
                      <td className="px-4 py-2.5">
                        {r.feasible ? (
                          <StatusDot tone="ok">{r.selectedStrategy ? strategyLabel(r.selectedStrategy) : titleCase(r.status)}</StatusDot>
                        ) : (
                          <StatusDot tone="danger">Infeasible</StatusDot>
                        )}
                      </td>
                      <td className="px-4 py-2.5 text-[13px] text-muted">{r.scoringMode ? titleCase(r.scoringMode) : '—'}</td>
                      <td className="px-2 text-right">
                        <Link to={`/recommendations/${r.id}`} aria-label="Open recommendation" className="text-faint hover:text-ink">
                          <ChevronRight className="size-4" />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Panel>
          )}
        </>
      )}
    </>
  )
}
