import { AlertTriangle, Info } from 'lucide-react'
import { Notice, Panel, Section, Stat } from '../components/ui'
import { fmt, fmtDate, fmtFixed, inr, strategyLabel, titleCase } from '../format'
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
    <Section title="Field summary">
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
  const withYield = rec.scoring?.mode !== 'COST_AND_EXCESS_ONLY' && plan.yield?.available
  return (
    <Section title="Recommended plan">
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
      <Panel className="divide-y divide-line">
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
      </Panel>
    </Section>
  )
}
