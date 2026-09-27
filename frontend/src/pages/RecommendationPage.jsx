import { useState } from 'react'
import { ArrowLeft, Download } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { recommendationApi } from '../api/endpoints'
import { ButtonLink, ErrorNotice, Loading, PageHeader, StatusDot } from '../components/ui'
import { fmt, fmtDateTime, titleCase } from '../format'
import RecommendationView from '../recommendation/RecommendationView'
import { useAsync } from '../useAsync'

export default function RecommendationPage() {
  const { recommendationId } = useParams()
  const location = useLocation()
  const passed = location.state?.recommendation
  const fresh = passed && String(passed.id) === recommendationId
  const { data, error, loading, reload } = useAsync(
    () => (fresh ? Promise.resolve(passed) : recommendationApi.get(recommendationId)),
    [recommendationId],
  )

  const [downloading, setDownloading] = useState(false)

  const handleDownloadPdf = async () => {
    if (!data) return
    try {
      setDownloading(true)
      const res = await recommendationApi.downloadPdf(data.id)
      const blob = new Blob([res.data], { type: 'application/pdf' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `agrioptima_prescription_${data.id}.pdf`
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (err) {
      console.error('Failed to download PDF:', err)
      alert('Could not download prescription PDF. Please try again.')
    } finally {
      setDownloading(false)
    }
  }

  if (loading) return <Loading label="Loading recommendation…" />
  if (error) return <ErrorNotice error={error} onRetry={reload} />
  const rec = data

  return (
    <>
      <PageHeader
        back={
          <Link to={`/fields/${rec.field.id}`} className="mb-2 inline-flex items-center gap-1 text-[13px] text-muted hover:text-ink">
            <ArrowLeft className="size-3.5" aria-hidden />
            {rec.field.name}
          </Link>
        }
        title={`Recommendation — ${rec.field.name}`}
        meta={
          <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span>
              {[rec.crop?.name, rec.growthStage?.name, `${fmt(rec.field.areaHa, 3)} ha`].filter(Boolean).join(' · ')}
            </span>
            <span className="text-faint">|</span>
            <StatusDot tone={rec.feasible ? 'ok' : 'danger'}>{rec.feasible ? titleCase(rec.status) : 'Infeasible'}</StatusDot>
            <span className="text-faint">|</span>
            <span>
              {fresh ? 'Generated' : 'Stored'} {fmtDateTime(rec.createdAt)} · #{rec.id}
            </span>
          </span>
        }
        actions={
          <div className="flex items-center gap-2">
            {rec.feasible && (
              <button
                type="button"
                onClick={handleDownloadPdf}
                disabled={downloading}
                className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-700 px-3.5 py-1.5 text-xs font-bold text-white shadow-sm hover:bg-emerald-800 transition-colors"
              >
                <Download className="size-3.5" />
                {downloading ? 'Downloading…' : 'Download Prescription (PDF)'}
              </button>
            )}
            <ButtonLink variant="secondary" to={`/fields/${rec.field.id}`}>
              Back to field
            </ButtonLink>
            <ButtonLink variant="tertiary" to={`/history?field=${rec.field.id}`}>
              View history
            </ButtonLink>
          </div>
        }
      />
      <RecommendationView rec={rec} />
    </>
  )
}
