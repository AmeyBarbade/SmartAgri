import { useState } from 'react'
import { AlertTriangle, ChevronDown, Download, FileText, Info } from 'lucide-react'
import { Button, Notice, Panel, Section, Stat } from '../components/ui'
import { fmt, fmtDate, fmtFixed, inr, strategyLabel, titleCase } from '../format'
import { recommendationApi } from '../api/endpoints'
import PlanCharts from './PlanCharts'
import { groupPlans } from './plans'

const NUTRIENTS = [
  ['n', 'N'],
  ['p2o5', 'P₂O₅'],
  ['k2o', 'K₂O'],
]

/** Renders one RecommendationResponse exactly as returned by the backend. */
export default function RecommendationView({ rec }) {
  const groups = groupPlans(rec.plans)
  const selected = rec.plans.find((p) => p.selected)
  const yieldAvailable = rec.plans.some((p) => p.yield?.available)

  return (
    <>
      <FieldSummary rec={rec} />
      {rec.weather && <WeatherBanner weather={rec.weather} />}
      <Requirement rec={rec} />

      {!rec.feasible && <Infeasible rec={rec} />}

      {rec.feasible && rec.plans.length === 0 && (
        <Section title="Fertilizer plans">
          <Notice tone="warn" title="Nothing to apply at this stage">
            {rec.status === 'NOTHING_REQUIRED'
              ? 'The requirement due now is zero, so the optimiser returned no plan.'
              : 'The backend returned no plans for this run.'}
          </Notice>
        </Section>
      )}

      {rec.plans.length > 0 && (
        <Section
          title="Fertilizer plans"
          description={
            groups.length < rec.plans.length
              ? `${rec.plans.length} strategies were evaluated; identical plans are shown once.`
              : `${rec.plans.length} strategies were evaluated.`
          }
        >
          <PlanTable groups={groups} yieldAvailable={yieldAvailable} />
          {groups.length > 1 ? (
            <Panel className="mt-4 px-5 py-5">
              <PlanCharts groups={groups} yieldAvailable={yieldAvailable} />
            </Panel>
          ) : (
            <p className="mt-3 text-[13px] text-muted">All strategies produced the same plan, so there is nothing to chart.</p>
          )}
        </Section>
      )}

      {selected && <SelectedPlan rec={rec} plan={selected} />}
      <ExcessImpactAlert plans={rec.plans} selectedPlan={selected} />
      {rec.ipns && <IpnsAdvisory ipns={rec.ipns} />}
      {rec.micronutrients && rec.micronutrients.length > 0 && (
        <MicronutrientSection micronutrients={rec.micronutrients} />
      )}
      <WarningsAndAssumptions rec={rec} />
    </>
  )
}

function FieldSummary({ rec }) {
  const { field, soil, requirement } = rec
  const soilText = soil?.soilTestUsed ? (
    `Tested ${fmtDate(soil.sampleDate)}`
  ) : (
    'No soil test'
  )
  const soilSub = soil?.soilTestUsed
    ? `${soil.ageDays != null ? `${soil.ageDays} days old · ` : ''}N ${titleCase(soil.nClass)} · P ${titleCase(soil.pClass)} · K ${titleCase(soil.kClass)} · pH ${fmtFixed(soil.ph, 1)}`
    : 'Medium fertility assumed'
  return (
    <Section title="Field summary" actions={<ConfidenceBadge rec={rec} />}>
      <Panel className="px-5 py-4">
        <dl className="grid grid-cols-2 gap-x-6 gap-y-4 md:grid-cols-3 xl:grid-cols-6">
          <Stat label="Field" sub={field.farmName}>
            {field.name}
          </Stat>
          <Stat label="Crop" sub={field.season && titleCase(field.season)}>
            {rec.crop?.name ?? '—'}
          </Stat>
          <Stat label="Growth stage" sub={rec.growthStage && `Stage ${rec.growthStage.seq}`}>
            {rec.growthStage?.name ?? '—'}
          </Stat>
          <Stat label="Area" sub={field.irrigationType && titleCase(field.irrigationType)}>
            {fmt(field.areaHa, 3)} ha
          </Stat>
          <Stat label="Soil status" sub={soilSub}>
            {soilText}
          </Stat>
          <Stat label="Profile" sub={requirement?.profileCode}>
            {requirement?.profileName ?? '—'}
          </Stat>
        </dl>
      </Panel>
    </Section>
  )
}

function Requirement({ rec }) {
  const r = rec.requirement
  if (!r) return null
  return (
    <Section title="Nutrient requirement" description="Due at the current growth stage, after soil-test adjustment and fertilizer already applied.">
      <Panel>
        <div className="grid grid-cols-3 divide-x divide-line">
          {NUTRIENTS.map(([k, label]) => (
            <div key={k} className="px-5 py-4">
              <p className="text-xs text-muted">{label}</p>
              <p className="tabular mt-0.5 text-2xl font-semibold tracking-tight text-ink">
                {fmt(r.dueNowKgHa?.[k])}
                <span className="ml-1 text-sm font-normal text-muted">kg/ha</span>
              </p>
              <p className="tabular mt-1 text-xs text-muted">{fmt(r.dueNowFieldKg?.[k])} kg for the field</p>
            </div>
          ))}
        </div>
        <div className="tabular grid grid-cols-1 gap-2 border-t border-line px-5 py-3 text-[13px] text-muted sm:grid-cols-2">
          <p>
            Already applied this season:{' '}
            <span className="text-ink-2">
              {NUTRIENTS.map(([k, l]) => `${l} ${fmt(r.alreadyAppliedKgHa?.[k])}`).join(' · ')} kg/ha
            </span>
          </p>
          <p>
            Remaining this season:{' '}
            <span className="text-ink-2">
              {NUTRIENTS.map(([k, l]) => `${l} ${fmt(r.remainingSeasonKgHa?.[k])}`).join(' · ')} kg/ha
            </span>
          </p>
        </div>
        <SoilDeficiencyChart soil={rec.soil} requirement={r} />
      </Panel>
    </Section>
  )
}

function Infeasible({ rec }) {
  return (
    <Section title="Fertilizer plans">
      <Notice tone="danger" title="No feasible fertilizer plan">
        <p>{rec.infeasibilityReason ?? 'The optimiser could not meet the requirement with the available fertilizers.'}</p>
        {rec.infeasibility?.length > 0 && (
          <table className="tabular mt-3 w-full text-left text-[13px]">
            <thead className="text-xs text-muted">
              <tr>
                <th className="py-1 pr-4 font-medium">Nutrient</th>
                <th className="py-1 pr-4 text-right font-medium">Required (kg/ha)</th>
                <th className="py-1 pr-4 text-right font-medium">Max supply (kg/ha)</th>
                <th className="py-1 font-medium">Reason</th>
              </tr>
            </thead>
            <tbody>
              {rec.infeasibility.map((s) => (
                <tr key={s.nutrient} className="border-t border-danger-line">
                  <td className="py-1.5 pr-4">{s.nutrient}</td>
                  <td className="py-1.5 pr-4 text-right">{fmt(s.requiredKgHa)}</td>
                  <td className="py-1.5 pr-4 text-right">{fmt(s.maxSupplyKgHa)}</td>
                  <td className="py-1.5">{s.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Notice>
    </Section>
  )
}

function PlanTable({ groups, yieldAvailable }) {
  const rows = [
    {
      label: 'Fertilizers',
      cell: (p) => (
        <ul className="space-y-0.5">
          {p.items.map((i) => (
            <li key={i.code}>
              {i.name.replace(/\s*\(.*\)$/, '')} <span className="text-muted">{fmt(i.kgHa, 1)} kg/ha</span>
            </li>
          ))}
        </ul>
      ),
    },
    { label: 'Cost', unit: '₹/ha', cell: (p) => inr(p.costPerHa) },
    { label: 'Field cost', unit: '₹', cell: (p) => inr(p.fieldCost) },
    {
      label: 'Predicted yield',
      unit: 't/ha',
      cell: (p) => (p.yield?.available ? fmtFixed(p.yield.predictedYieldTHa, 2) : <span className="text-muted">n/a</span>),
      hide: !yieldAvailable,
    },
    { label: 'N excess', unit: 'kg/ha', cell: (p) => fmtFixed(p.excessKgHa?.n, 1) },
    { label: 'P₂O₅ excess', unit: 'kg/ha', cell: (p) => fmtFixed(p.excessKgHa?.p2o5, 1) },
    { label: 'K₂O excess', unit: 'kg/ha', cell: (p) => fmtFixed(p.excessKgHa?.k2o, 1) },
    { label: 'Total fertilizer mass', unit: 'kg/ha', cell: (p) => fmt(p.totalMassKgHa, 1) },
    { label: 'Score', unit: '₹/ha', cell: (p) => (p.score ? inr(p.score.scorePerHa) : '—') },
  ].filter((r) => !r.hide)

  return (
    <Panel className={`overflow-x-auto ${groups.length === 1 ? 'max-w-2xl' : ''}`}>
      <table className={`tabular w-full text-left text-sm ${groups.length > 1 ? 'min-w-[560px]' : ''}`}>
        <thead>
          <tr className="border-b border-line">
            <th className="w-44 px-4 py-3" />
            {groups.map((g) => (
              <th
                key={g.strategies.join()}
                className={`px-4 py-3 align-bottom font-medium ${g.selected ? 'border-x border-t border-accent-line bg-accent-soft' : ''}`}
              >
                {g.selected && <span className="mb-1 block text-[11px] font-medium uppercase tracking-wide text-accent">Recommended</span>}
                <span className="text-ink">{g.label}</span>
                {g.members.length > 1 && <span className="block text-xs font-normal text-muted">Identical plans</span>}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={r.label} className={i > 0 ? 'border-t border-line' : ''}>
              <th scope="row" className="px-4 py-2.5 text-left align-top text-[13px] font-normal text-muted">
                {r.label}
                {r.unit && <span className="text-faint"> ({r.unit})</span>}
              </th>
              {groups.map((g) => (
                <td
                  key={g.strategies.join()}
                  className={`px-4 py-2.5 align-top text-ink ${g.selected ? 'border-x border-accent-line bg-accent-soft/60' : ''} ${
                    g.selected && i === rows.length - 1 ? 'border-b' : ''
                  }`}
                >
                  {r.cell(g.plan)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </Panel>
  )
}

function SelectedPlan({ rec, plan }) {
  const [downloading, setDownloading] = useState(false)
  const withYield = rec.scoring?.mode !== 'COST_AND_EXCESS_ONLY' && plan.yield?.available

  const handleDownloadPdf = async () => {
    try {
      setDownloading(true)
      const res = await recommendationApi.downloadPdf(rec.id)
      const blob = new Blob([res.data], { type: 'application/pdf' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `agrioptima_prescription_${rec.id}.pdf`
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

  return (
    <Section
      title="Recommended plan"
      actions={
        <Button variant="secondary" onClick={handleDownloadPdf} loading={downloading}>
          <Download className="size-4" aria-hidden />
          Download Prescription (PDF)
        </Button>
      }
    >
      <div className="rounded-lg border border-accent-line bg-surface">
        <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-line px-5 py-3">
          <p className="font-medium">
            {rec.selectedPlan?.label ?? strategyLabel(plan.strategy)}
            {plan.sameAs?.length > 0 && (
              <span className="font-normal text-muted"> (same as {plan.sameAs.map(strategyLabel).join(', ').toLowerCase()})</span>
            )}
          </p>
          <p className="text-[13px] text-muted">{plan.description}</p>
        </div>
        <div className="grid gap-6 px-5 py-4 lg:grid-cols-[1.4fr_1fr]">
          <div className="overflow-x-auto">
          <table className="tabular w-full min-w-[440px] text-left text-sm">
            <thead className="text-xs text-muted">
              <tr>
                <th className="pb-2 font-medium">Fertilizer</th>
                <th className="pb-2 text-right font-medium">kg/ha</th>
                <th className="pb-2 text-right font-medium">Field total</th>
                <th className="pb-2 text-right font-medium">Cost/ha</th>
              </tr>
            </thead>
            <tbody>
              {plan.items.map((i) => (
                <tr key={i.code} className="border-t border-line">
                  <td className="py-2 pr-3 font-medium">{i.name}</td>
                  <td className="py-2 text-right">{fmt(i.kgHa, 2)}</td>
                  <td className="py-2 text-right text-ink-2">
                    {fmt(i.fieldKg, 1)} kg
                    <span className="block text-xs text-muted">
                      {fmt(i.fieldBags, 1)} × {fmt(i.bagKg)} kg bags
                    </span>
                  </td>
                  <td className="py-2 text-right">{inr(i.costPerHa)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
          <dl className="grid grid-cols-2 gap-x-6 gap-y-4 lg:border-l lg:border-line lg:pl-6">
            <Stat label="Expected yield" sub={plan.yield?.available && `${fmt(plan.yield.fieldProductionT, 2)} t for the field`}>
              {plan.yield?.available ? `${fmtFixed(plan.yield.predictedYieldTHa, 2)} t/ha` : 'Not available'}
            </Stat>
            <Stat label="Fertilizer cost" sub={`${inr(plan.fieldCost)} for the field`}>
              {inr(plan.costPerHa)}/ha
            </Stat>
            <Stat label="Total fertilizer" sub={`${fmt(plan.totalMassFieldKg, 1)} kg for the field`}>
              {fmt(plan.totalMassKgHa, 1)} kg/ha
            </Stat>
            <Stat label="Nutrient excess" sub={NUTRIENTS.map(([k, l]) => `${l} ${fmtFixed(plan.excessKgHa?.[k], 1)}`).join(' · ')}>
              {fmtFixed(plan.totalExcessKgHa, 1)} kg/ha
            </Stat>
          </dl>
        </div>
        <div className="border-t border-line bg-canvas/60 px-5 py-3 text-[13px] text-ink-2">
          <p>
            {withYield
              ? "Recommended based on the system's cost, predicted yield and nutrient-excess scoring."
              : "Recommended based on the system's cost and nutrient-excess scoring. No yield prediction was used."}
          </p>
          {rec.selectedPlan?.reason && <p className="tabular mt-1 text-muted">{rec.selectedPlan.reason}</p>}
        </div>
      </div>

      {/* Massive Primary CTA at bottom of recommended plan */}
      <div className="mt-4 rounded-xl border border-emerald-300 bg-emerald-50/70 p-4 sm:p-5 shadow-sm">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3.5">
            <div className="rounded-xl bg-emerald-700 p-3 text-white shadow-xs shrink-0">
              <FileText className="size-6" />
            </div>
            <div>
              <h4 className="font-bold text-slate-900 text-sm sm:text-base">Official Agronomic Fertilizer Prescription</h4>
              <p className="text-xs text-slate-600 mt-0.5">
                Print-ready 1-page A4 document with crop stage targets, split doses, IPNS organic blend, and ICAR guidelines.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={handleDownloadPdf}
            disabled={downloading}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-700 px-6 py-3.5 text-sm font-bold text-white shadow-md hover:bg-emerald-800 transition-all shrink-0 active:scale-98"
          >
            <Download className="size-4.5" />
            {downloading ? 'Generating Official PDF…' : 'Download Prescription (PDF)'}
          </button>
        </div>
      </div>
    </Section>
  )
}

function WarningsAndAssumptions({ rec }) {
  const yp = rec.yieldPrediction
  const extrapolated = rec.plans.filter((p) => p.yield?.extrapolation)
  const modelNotes = []
  // An unavailable prediction is already reported in rec.warnings by the backend; only per-plan flags are added here.
  for (const p of extrapolated) {
    modelNotes.push(
      `${strategyLabel(p.strategy)}: the yield model extrapolated beyond its training range` +
        (p.yield.clippedFeatures?.length ? ` (clipped: ${p.yield.clippedFeatures.join(', ')}).` : '.'),
    )
  }
  const warnings = [...(rec.warnings ?? []), ...modelNotes]
  const defaults = (yp?.inputs ?? []).filter((i) => i.source === 'PROTOTYPE_DEFAULT' || i.source === 'NOT_RECORDED')

  return (
    <Section title="Warnings & assumptions" description="Limitations reported by the backend for this recommendation.">
      <details className="group rounded-xl border border-line bg-surface shadow-sm overflow-hidden" open={false}>
        <summary className="flex cursor-pointer list-none items-center justify-between px-5 py-4 font-semibold text-ink hover:bg-canvas/50 transition-colors">
          <div className="flex items-center gap-2.5 text-sm font-semibold text-ink">
            <AlertTriangle className="size-4 text-warn" aria-hidden />
            <span>View Engine Assumptions & Warnings ({warnings.length} warnings, {rec.assumptions?.length ?? 0} assumptions)</span>
          </div>
          <ChevronDown className="size-4 text-muted transition-transform group-open:rotate-180" aria-hidden />
        </summary>
        <div className="border-t border-line divide-y divide-line">
          <div className="px-5 py-4">
            <h3 className="mb-2 flex items-center gap-2 text-[13px] font-medium text-warn">
              <AlertTriangle className="size-4" aria-hidden />
              Warnings ({warnings.length})
            </h3>
            {warnings.length === 0 ? (
              <p className="text-[13px] text-muted">No warnings.</p>
            ) : (
              <ul className="list-disc space-y-1 pl-5 text-[13px] text-ink-2 marker:text-faint">
                {warnings.map((w, i) => (
                  <li key={i}>{w}</li>
                ))}
              </ul>
            )}
          </div>

          {defaults.length > 0 && (
            <div className="px-5 py-4">
              <h3 className="mb-2 text-[13px] font-medium text-ink-2">Default or missing yield-model inputs</h3>
              <ul className="space-y-1 text-[13px] text-ink-2">
                {defaults.map((d) => (
                  <li key={d.feature}>
                    <span className="font-medium">{d.feature}</span>: {d.value || 'not recorded'}
                    <span className="text-muted"> — {d.note ?? titleCase(d.source)}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className="px-5 py-4">
            <h3 className="mb-2 flex items-center gap-2 text-[13px] font-medium text-ink-2">
              <Info className="size-4 text-muted" aria-hidden />
              Assumptions ({rec.assumptions?.length ?? 0})
            </h3>
            <ul className="list-disc space-y-1 pl-5 text-[13px] text-ink-2 marker:text-faint">
              {rec.assumptions?.map((a, i) => (
                <li key={i}>{a}</li>
              ))}
            </ul>
          </div>

          {rec.scoring && (
            <div className="px-5 py-4 text-[13px]">
              <h3 className="mb-2 font-medium text-ink-2">Scoring</h3>
              <p className="font-mono text-xs text-ink-2">{rec.scoring.formula}</p>
              <p className="mt-2 text-muted">
                Crop price {inr(rec.scoring.cropPriceInrPerTonne)}/t ({rec.scoring.cropPriceSource}) · excess penalty{' '}
                {inr(rec.scoring.excessPenaltyInrPerKg)}/kg · mode {titleCase(rec.scoring.mode)}
              </p>
              <p className="mt-1 text-muted">Ties: {rec.scoring.tieBreak}</p>
            </div>
          )}

          {yp?.inputs?.length > 0 && (
            <details className="px-5 py-4 text-[13px]">
              <summary className="cursor-pointer font-medium text-ink-2">All yield-model inputs ({yp.inputs.length})</summary>
              <table className="mt-3 w-full text-left">
                <thead className="text-xs text-muted">
                  <tr>
                    <th className="py-1 pr-4 font-medium">Input</th>
                    <th className="py-1 pr-4 font-medium">Value</th>
                    <th className="py-1 pr-4 font-medium">Source</th>
                    <th className="py-1 font-medium">Note</th>
                  </tr>
                </thead>
                <tbody className="text-ink-2">
                  {yp.inputs.map((i) => (
                    <tr key={i.feature} className="border-t border-line align-top">
                      <td className="py-1.5 pr-4 font-medium">{i.feature}</td>
                      <td className="tabular py-1.5 pr-4">{i.value || '—'}</td>
                      <td className="py-1.5 pr-4 text-muted">{i.source}</td>
                      <td className="py-1.5 text-muted">{i.note}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </details>
          )}

          <div className="px-5 py-4 text-xs text-muted">
            <p>{rec.disclaimer}</p>
            <p className="mt-2">
              Knowledge base {rec.knowledgeBase?.id} v{rec.knowledgeBase?.version} ({rec.knowledgeBase?.status})
              {rec.modelVersion && ` · yield model ${rec.modelVersion}`}
              {rec.optimizerSolver && ` · solver ${rec.optimizerSolver}`}
            </p>
          </div>
        </div>
      </details>
    </Section>
  )
}

function WeatherBanner({ weather }) {
  if (!weather) return null
  const isWarning = weather.heavyRainWarning
  return (
    <div className={`mt-4 rounded-xl border p-4 ${isWarning ? 'border-amber-300 bg-amber-50/70 text-amber-900' : 'border-line bg-surface text-ink'}`}>
      <div className="flex items-start gap-3">
        <span className="text-xl">{isWarning ? '⚠️' : '🌦️'}</span>
        <div className="flex-1">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h4 className="font-semibold text-sm">
              {isWarning ? 'Weather Hazard: Leaching & Runoff Risk' : 'Local Agro-Meteorological Forecast'}
            </h4>
            <span className="text-xs text-muted">Open-Meteo 7-Day Window</span>
          </div>
          <p className="mt-1 text-xs">
            <strong>3-Day Avg Max Temp:</strong> {weather.temperature}°C &nbsp;·&nbsp;
            <strong>7-Day Cumulative Rain:</strong> {weather.rainfall7dMm} mm
          </p>
          {isWarning && (
            <p className="mt-2 text-xs font-medium text-amber-800">
              {weather.warningReason} <em>Delay water-soluble top dressing (Urea) until after heavy rain ceases.</em>
            </p>
          )}
        </div>
      </div>
    </div>
  )
}

function IpnsAdvisory({ ipns }) {
  if (!ipns) return null
  return (
    <Section
      title="Sustainable IPNS Alternative (Organic Blending)"
      description="Integrated Plant Nutrient System: Replaces 25% of synthetic Nitrogen with organic amendments."
      actions={
        <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-300 bg-emerald-100/90 px-3 py-1 text-xs font-semibold text-emerald-900 shadow-xs">
          <span className="size-1.5 rounded-full bg-emerald-600" />
          Sustainable Practice USP · Soil Organic Carbon Booster
        </span>
      }
    >
      <div className="rounded-xl border-2 border-emerald-400 bg-[#ECFDF5] p-5 text-emerald-950 shadow-sm">
        <div className="flex items-start gap-3">
          <span className="text-2xl">🌿</span>
          <div className="flex-1">
            <h4 className="font-bold text-sm text-emerald-900">
              25% Chemical Nitrogen Substitution Plan
            </h4>
            <p className="mt-1 text-xs text-emerald-800 leading-relaxed">
              {ipns.note}
            </p>
            <div className="mt-3.5 grid grid-cols-2 gap-4 sm:grid-cols-4">
              <div className="rounded-lg bg-white/85 p-3 border border-emerald-200/90 shadow-2xs">
                <p className="text-[11px] text-emerald-700 uppercase tracking-wider font-semibold">Reduced Chemical N</p>
                <p className="text-base font-bold text-emerald-900 mt-0.5">{fmt(ipns.chemicalNKgHa)} <span className="text-xs font-normal">kg/ha</span></p>
              </div>
              <div className="rounded-lg bg-white/85 p-3 border border-emerald-200/90 shadow-2xs">
                <p className="text-[11px] text-emerald-700 uppercase tracking-wider font-semibold">Organic N Target</p>
                <p className="text-base font-bold text-emerald-900 mt-0.5">{fmt(ipns.organicNKgHa)} <span className="text-xs font-normal">kg/ha</span></p>
              </div>
              <div className="rounded-lg bg-white/85 p-3 border border-emerald-200/90 shadow-2xs">
                <p className="text-[11px] text-emerald-700 uppercase tracking-wider font-semibold">Farm Yard Manure</p>
                <p className="text-base font-bold text-emerald-900 mt-0.5">{fmt(ipns.fymKgHa)} <span className="text-xs font-normal">kg/ha</span></p>
                <p className="text-[11px] text-emerald-700">{fmt(ipns.fymFieldKg)} kg field</p>
              </div>
              <div className="rounded-lg bg-white/85 p-3 border border-emerald-200/90 shadow-2xs">
                <p className="text-[11px] text-emerald-700 uppercase tracking-wider font-semibold">Vermicompost (Alt)</p>
                <p className="text-base font-bold text-emerald-900 mt-0.5">{fmt(ipns.vermicompostKgHa)} <span className="text-xs font-normal">kg/ha</span></p>
                <p className="text-[11px] text-emerald-700">{fmt(ipns.vermicompostFieldKg)} kg field</p>
              </div>
            </div>
            <p className="mt-3.5 text-[11px] text-emerald-800 bg-white/70 rounded-md p-2.5 border border-emerald-200/70">
              💡 <strong>Soil Health Benefit:</strong> Blending Farm Yard Manure increases cation exchange capacity (CEC), enhances soil microbial activity, and improves soil moisture retention.
            </p>
          </div>
        </div>
      </div>
    </Section>
  )
}

function MicronutrientSection({ micronutrients }) {
  if (!micronutrients || micronutrients.length === 0) return null
  return (
    <Section title="Secondary & Micronutrient Status" description="Evaluation against Indian Council of Agricultural Research (ICAR) critical deficiency thresholds.">
      <Panel className="overflow-x-auto p-4">
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b border-line text-muted">
              <th className="pb-2 font-medium">Nutrient</th>
              <th className="pb-2 font-medium">Tested Level</th>
              <th className="pb-2 font-medium">Critical Benchmark</th>
              <th className="pb-2 font-medium">Status</th>
              <th className="pb-2 font-medium">Agronomic Advisory</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line/60">
            {micronutrients.map((m) => {
              const isDef = m.status === 'DEFICIENT' || m.status === 'HIGH_SALINITY'
              return (
                <tr key={m.nutrient} className="hover:bg-subtle/30">
                  <td className="py-2 font-semibold text-ink">{m.name} ({m.nutrient})</td>
                  <td className="py-2 tabular font-medium">{fmt(m.value)} {m.unit}</td>
                  <td className="py-2 text-muted">{m.threshold} {m.unit}</td>
                  <td className="py-2">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium ${
                      isDef ? 'bg-amber-100 text-amber-800' : 'bg-emerald-100 text-emerald-800'
                    }`}>
                      {m.status}
                    </span>
                  </td>
                  <td className="py-2 text-muted">{m.note}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </Panel>
    </Section>
  )
}

function ConfidenceBadge({ rec }) {
  const hasExtrapolation = rec.plans?.some((p) => p.yield?.extrapolation)
  const isSoilTested = rec.soil?.soilTestUsed

  let text = '🎯 High Confidence — ICAR STCR Grounded'
  let toneClass = 'border-emerald-300 bg-emerald-50 text-emerald-800'

  if (hasExtrapolation) {
    text = '⚡ ML-Estimated (Extrapolated Prediction)'
    toneClass = 'border-amber-300 bg-amber-50 text-amber-800'
  } else if (!isSoilTested) {
    text = 'ℹ️ Medium Confidence — Regional Soil Estimate'
    toneClass = 'border-blue-300 bg-blue-50 text-blue-800'
  }

  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium shadow-xs ${toneClass}`}>
      <span className="size-1.5 rounded-full bg-current" aria-hidden />
      {text}
    </span>
  )
}

function SoilDeficiencyChart({ soil, requirement }) {
  const rows = [
    {
      key: 'n',
      name: 'Nitrogen (N)',
      tested: soil?.availableNKgHa,
      rating: soil?.nClass || 'MEDIUM',
      benchmark: '280 – 560 kg/ha',
      due: requirement?.dueNowKgHa?.n,
      pct: soil?.availableNKgHa ? Math.min(100, Math.round((soil.availableNKgHa / 560) * 100)) : 50,
    },
    {
      key: 'p2o5',
      name: 'Phosphorus (P₂O₅)',
      tested: soil?.availablePKgHa,
      rating: soil?.pClass || 'MEDIUM',
      benchmark: '10 – 25 kg/ha',
      due: requirement?.dueNowKgHa?.p2o5,
      pct: soil?.availablePKgHa ? Math.min(100, Math.round((soil.availablePKgHa / 30) * 100)) : 50,
    },
    {
      key: 'k2o',
      name: 'Potassium (K₂O)',
      tested: soil?.availableKKgHa,
      rating: soil?.kClass || 'MEDIUM',
      benchmark: '110 – 280 kg/ha',
      due: requirement?.dueNowKgHa?.k2o,
      pct: soil?.availableKKgHa ? Math.min(100, Math.round((soil.availableKKgHa / 350) * 100)) : 50,
    },
  ]

  const classBadges = {
    LOW: { label: 'DEFICIENT (Dose +25%)', color: 'bg-red-100 text-red-800 border-red-200', bar: 'bg-red-500' },
    MEDIUM: { label: 'ADEQUATE (Maintenance Dose)', color: 'bg-amber-100 text-amber-800 border-amber-200', bar: 'bg-amber-500' },
    HIGH: { label: 'SURPLUS (Dose -25%)', color: 'bg-emerald-100 text-emerald-800 border-emerald-200', bar: 'bg-emerald-500' },
  }

  return (
    <div className="border-t border-line px-5 py-4 bg-surface/50">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wider text-ink">
            Soil Health vs. Agronomic Critical Benchmarks
          </h4>
          <p className="text-[12px] text-muted">
            Shows why this recommendation was tailored: Indian Council of Agricultural Research (ICAR) fertility classification.
          </p>
        </div>
      </div>
      <div className="space-y-3">
        {rows.map((row) => {
          const badge = classBadges[row.rating] || classBadges.MEDIUM
          return (
            <div key={row.key} className="rounded-lg border border-line bg-canvas/40 p-3">
              <div className="flex flex-wrap items-center justify-between gap-1 text-xs">
                <span className="font-semibold text-ink">{row.name}</span>
                <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] font-medium ${badge.color}`}>
                  {row.rating}: {badge.label}
                </span>
              </div>
              <div className="mt-2 flex items-center gap-3">
                <div className="h-2 flex-1 rounded-full bg-line overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all ${badge.bar}`}
                    style={{ width: `${Math.max(12, row.pct)}%` }}
                  />
                </div>
                <span className="tabular text-xs font-medium text-ink w-24 text-right">
                  {row.tested != null ? `${fmt(row.tested)} kg/ha` : 'Not tested'}
                </span>
              </div>
              <div className="mt-1.5 flex flex-wrap items-center justify-between text-[11px] text-muted">
                <span>ICAR Medium Range: {row.benchmark}</span>
                <span className="text-ink-2 font-medium">Calculated Need: {fmt(row.due)} kg/ha due</span>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function ExcessImpactAlert({ plans, selectedPlan }) {
  const plan = selectedPlan || plans?.[0]
  if (!plan) return null

  const nExcess = plan.excessKgHa?.n ?? 0
  const pExcess = plan.excessKgHa?.p2o5 ?? 0
  const kExcess = plan.excessKgHa?.k2o ?? 0
  const totalExcess = plan.totalExcessKgHa ?? (nExcess + pExcess + kExcess)

  const hasExcess = totalExcess > 0.05

  return (
    <Section
      title="Over-Fertilization & Soil Degradation Impact Analysis"
      description="Agronomic consequences of nutrient excess, soil acidification, and ecological hazards."
    >
      <Panel className={`p-5 ${hasExcess ? 'border-amber-300 bg-amber-50/40' : 'border-emerald-300 bg-emerald-50/40'}`}>
        <div className="flex items-start gap-3">
          <span className="text-2xl">{hasExcess ? '⚠️' : '🛡️'}</span>
          <div className="flex-1">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h4 className="font-semibold text-sm text-ink">
                {hasExcess
                  ? `Identified Nutrient Excess in ${plan.label || strategyLabel(plan.strategy)} (${fmt(totalExcess, 1)} kg/ha)`
                  : 'Balanced Application — Zero Nutrient Waste'}
              </h4>
              <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium ${
                hasExcess ? 'bg-amber-100 text-amber-900 border border-amber-300' : 'bg-emerald-100 text-emerald-900 border border-emerald-300'
              }`}>
                {hasExcess ? 'Soil Risk Detected' : 'Soil Integrity Preserved'}
              </span>
            </div>

            {hasExcess ? (
              <div className="mt-3 space-y-2.5 text-xs text-ink-2">
                <p>
                  Applying chemical fertilizers above crop uptake capacity harms soil biology and degrades productivity:
                </p>
                <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                  {nExcess > 0.05 && (
                    <div className="rounded-lg border border-amber-200 bg-white/80 p-3">
                      <p className="font-semibold text-amber-900 flex items-center gap-1">
                        🧪 Excess Nitrogen ({fmt(nExcess, 1)} kg/ha)
                      </p>
                      <ul className="mt-1 space-y-1 text-muted text-[11px] list-disc pl-4">
                        <li><strong>Soil Acidification:</strong> Nitrification releases H⁺ ions, dropping soil pH.</li>
                        <li><strong>Lodging & Pests:</strong> Weak stems fall over; succulent foliage invites aphids & borers.</li>
                        <li><strong>Nitrate Leaching:</strong> Leaches into shallow groundwater and drinking wells.</li>
                      </ul>
                    </div>
                  )}

                  {pExcess > 0.05 && (
                    <div className="rounded-lg border border-amber-200 bg-white/80 p-3">
                      <p className="font-semibold text-amber-900 flex items-center gap-1">
                        🔒 Excess Phosphorus ({fmt(pExcess, 1)} kg/ha)
                      </p>
                      <ul className="mt-1 space-y-1 text-muted text-[11px] list-disc pl-4">
                        <li><strong>Micronutrient Lock-up:</strong> Binds Zinc (Zn) and Iron (Fe) into insoluble salts.</li>
                        <li><strong>Eutrophication:</strong> Surface runoff causes toxic algal blooms in water bodies.</li>
                      </ul>
                    </div>
                  )}

                  {kExcess > 0.05 && (
                    <div className="rounded-lg border border-amber-200 bg-white/80 p-3">
                      <p className="font-semibold text-amber-900 flex items-center gap-1">
                        ⚖️ Excess Potassium ({fmt(kExcess, 1)} kg/ha)
                      </p>
                      <ul className="mt-1 space-y-1 text-muted text-[11px] list-disc pl-4">
                        <li><strong>Nutrient Antagonism:</strong> Inhibits plant uptake of Magnesium (Mg) and Calcium (Ca).</li>
                      </ul>
                    </div>
                  )}

                  <div className="rounded-lg border border-amber-200 bg-white/80 p-3">
                    <p className="font-semibold text-amber-900 flex items-center gap-1">
                      🦠 Biological Degradation
                    </p>
                    <ul className="mt-1 space-y-1 text-muted text-[11px] list-disc pl-4">
                      <li>Excess synthetic salts reduce microbial biomass and mycorrhizal fungal colonization.</li>
                      <li>Soil becomes compacted with impaired moisture retention without organic amendments.</li>
                    </ul>
                  </div>
                </div>

                <p className="mt-2 text-[11px] text-amber-800 font-medium">
                  💡 <strong>Mitigation:</strong> Choose the <em>Minimum Excess</em> or <em>Balanced</em> strategy above, or adopt the <em>IPNS Organic Blending</em> plan below to eliminate surplus chemical loading.
                </p>
              </div>
            ) : (
              <p className="mt-1 text-xs text-emerald-800">
                This prescription achieves a 100% nutrient-balanced delivery. By supplying only what the plant can absorb, it prevents residual salt build-up, groundwater nitrate contamination, and long-term soil acidification.
              </p>
            )}
          </div>
        </div>
      </Panel>
    </Section>
  )
}

