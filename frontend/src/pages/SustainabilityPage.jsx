import React from 'react'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, TrendingUp, ShieldCheck, Leaf, Coins, CheckCircle, Info } from 'lucide-react'
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceLine,
} from 'recharts'
import { sustainabilityApi } from '../api/endpoints'
import { useAsync } from '../useAsync'
import { ErrorNotice, Loading, PageHeader, Panel, Section, Stat } from '../components/ui'
import { fmt, inr } from '../format'

export default function SustainabilityPage() {
  const { fieldId } = useParams()
  const { data, error, loading, reload } = useAsync(() => sustainabilityApi.get(fieldId), [fieldId])

  if (loading && !data) return <Loading label="Loading sustainability metrics…" />
  if (error) return <ErrorNotice error={error} onRetry={reload} />

  const {
    fieldName,
    farmName,
    cropName,
    areaHa,
    soilHealthScore,
    soilHealthRating,
    currentOrganicCarbon,
    targetOrganicCarbon,
    chemicalReductionPct,
    projectedAnnualSavingsInr,
    timeline,
    insights,
  } = data

  const scoreTone =
    soilHealthScore >= 75 ? 'text-accent' : soilHealthScore >= 50 ? 'text-warn' : 'text-danger'

  return (
    <>
      <PageHeader
        back={
          <Link
            to={`/fields/${fieldId}`}
            className="mb-2 inline-flex items-center gap-1 text-[13px] text-muted hover:text-ink"
          >
            <ArrowLeft className="size-3.5" aria-hidden />
            Back to {fieldName}
          </Link>
        }
        title="Multi-Season Soil Organic Carbon & Sustainability Tracker"
        meta={`${farmName} · ${cropName} · ${fmt(areaHa)} ha`}
      />

      {/* Top Key Metrics Strip */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4 mb-8">
        <Panel className="p-5 flex items-start gap-4">
          <div className="rounded-lg bg-accent-soft p-3 text-accent shrink-0">
            <ShieldCheck className="size-6" />
          </div>
          <div>
            <p className="text-xs text-muted font-medium uppercase tracking-wider">Soil Health Score</p>
            <p className={`text-2xl font-bold mt-1 ${scoreTone}`}>
              {soilHealthScore} <span className="text-sm font-normal text-muted">/ 100</span>
            </p>
            <p className="text-xs text-muted mt-1 font-medium">{soilHealthRating}</p>
          </div>
        </Panel>

        <Panel className="p-5 flex items-start gap-4">
          <div className="rounded-lg bg-accent-soft p-3 text-accent shrink-0">
            <Leaf className="size-6" />
          </div>
          <div>
            <p className="text-xs text-muted font-medium uppercase tracking-wider">Organic Carbon (OC %)</p>
            <p className="text-2xl font-bold mt-1 text-ink">
              {currentOrganicCarbon}%{' '}
              <span className="text-sm font-normal text-muted">→ {targetOrganicCarbon}%</span>
            </p>
            <p className="text-xs text-accent mt-1 font-medium">+0.28% Regenerative Target</p>
          </div>
        </Panel>

        <Panel className="p-5 flex items-start gap-4">
          <div className="rounded-lg bg-accent-soft p-3 text-accent shrink-0">
            <TrendingUp className="size-6" />
          </div>
          <div>
            <p className="text-xs text-muted font-medium uppercase tracking-wider">Chemical N Reduction</p>
            <p className="text-2xl font-bold mt-1 text-ink">
              {chemicalReductionPct}%
            </p>
            <p className="text-xs text-muted mt-1 font-medium">Replaced via IPNS Organic Blends</p>
          </div>
        </Panel>

        <Panel className="p-5 flex items-start gap-4">
          <div className="rounded-lg bg-accent-soft p-3 text-accent shrink-0">
            <Coins className="size-6" />
          </div>
          <div>
            <p className="text-xs text-muted font-medium uppercase tracking-wider">Field Fertilizer Savings</p>
            <p className="text-2xl font-bold mt-1 text-accent">
              {inr(projectedAnnualSavingsInr)}
            </p>
            <p className="text-xs text-muted mt-1 font-medium">Cumulative Input Cost Benefit</p>
          </div>
        </Panel>
      </div>

      {/* Visual Charts */}
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-2 mb-8">
        {/* Chart 1: Soil Organic Carbon (%) Trajectory */}
        <Section
          title="Soil Organic Carbon (SOC %) Trajectory"
          description="Progressive restoration of soil organic matter under 25% IPNS substitution."
        >
          <Panel className="p-5">
            <div className="h-72 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={timeline} margin={{ top: 10, right: 30, left: 0, bottom: 25 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e5e7eb" />
                  <XAxis
                    dataKey="seasonLabel"
                    tick={{ fontSize: 11 }}
                    angle={-20}
                    textAnchor="end"
                    interval={0}
                  />
                  <YAxis domain={[0.2, 1.0]} tick={{ fontSize: 12 }} unit="%" />
                  <Tooltip
                    formatter={(val) => [`${val}%`, 'Organic Carbon']}
                    labelFormatter={(label) => `Season: ${label}`}
                  />
                  <ReferenceLine
                    y={0.75}
                    stroke="#10b981"
                    strokeDasharray="3 3"
                    label={{ value: 'High Fertility Benchmark (0.75%)', fill: '#059669', fontSize: 11 }}
                  />
                  <Line
                    type="monotone"
                    dataKey="organicCarbonPct"
                    stroke="#10b981"
                    strokeWidth={3}
                    dot={{ r: 5, fill: '#10b981' }}
                    activeDot={{ r: 7 }}
                    name="Organic Carbon %"
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </Panel>
        </Section>

        {/* Chart 2: Chemical vs Organic Nitrogen Balance */}
        <Section
          title="Nitrogen Source Evolution (kg/ha)"
          description="Transition from purely synthetic urea to balanced mineral + organic nitrogen."
        >
          <Panel className="p-5">
            <div className="h-72 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={timeline} margin={{ top: 10, right: 30, left: 0, bottom: 25 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e5e7eb" />
                  <XAxis
                    dataKey="seasonLabel"
                    tick={{ fontSize: 11 }}
                    angle={-20}
                    textAnchor="end"
                    interval={0}
                  />
                  <YAxis tick={{ fontSize: 12 }} unit=" kg" />
                  <Tooltip
                    formatter={(val, name) => [`${val} kg/ha`, name]}
                    labelFormatter={(label) => `Season: ${label}`}
                  />
                  <Legend verticalAlign="top" height={36} />
                  <Bar dataKey="chemicalNitrogenKgHa" name="Chemical Nitrogen" fill="#3b82f6" stackId="n" />
                  <Bar dataKey="organicNitrogenKgHa" name="Organic Nitrogen (IPNS)" fill="#10b981" stackId="n" />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </Panel>
        </Section>
      </div>

      {/* Chart 3: Fertilizer Cost vs Yield Trajectory */}
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-2 mb-8">
        <Section
          title="Fertilizer Expenditure per Hectare (₹/ha)"
          description="Steady reduction in chemical fertilizer cost through natural soil nutrient cycling."
        >
          <Panel className="p-5">
            <div className="h-72 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={timeline} margin={{ top: 10, right: 30, left: 15, bottom: 25 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e5e7eb" />
                  <XAxis
                    dataKey="seasonLabel"
                    tick={{ fontSize: 11 }}
                    angle={-20}
                    textAnchor="end"
                    interval={0}
                  />
                  <YAxis tick={{ fontSize: 12 }} unit=" ₹" />
                  <Tooltip
                    formatter={(val) => [`₹${val}/ha`, 'Fertilizer Cost']}
                    labelFormatter={(label) => `Season: ${label}`}
                  />
                  <Line
                    type="monotone"
                    dataKey="fertilizerCostInrHa"
                    stroke="#f59e0b"
                    strokeWidth={2.5}
                    dot={{ r: 4, fill: '#f59e0b' }}
                    name="Cost (₹/ha)"
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </Panel>
        </Section>

        <Section
          title="Crop Yield Trajectory (t/ha)"
          description="Sustained and increasing crop productivity from improved soil biology and root aeration."
        >
          <Panel className="p-5">
            <div className="h-72 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={timeline} margin={{ top: 10, right: 30, left: 0, bottom: 25 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e5e7eb" />
                  <XAxis
                    dataKey="seasonLabel"
                    tick={{ fontSize: 11 }}
                    angle={-20}
                    textAnchor="end"
                    interval={0}
                  />
                  <YAxis domain={[4.0, 6.0]} tick={{ fontSize: 12 }} unit=" t" />
                  <Tooltip
                    formatter={(val) => [`${val} t/ha`, 'Yield']}
                    labelFormatter={(label) => `Season: ${label}`}
                  />
                  <Line
                    type="monotone"
                    dataKey="yieldTHa"
                    stroke="#6366f1"
                    strokeWidth={2.5}
                    dot={{ r: 4, fill: '#6366f1' }}
                    name="Yield (t/ha)"
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </Panel>
        </Section>
      </div>

      {/* Multi-Season Tabular Overview */}
      <Section
        title="Multi-Season Regenerative Trajectory Schedule"
        description="Comprehensive seasonal breakdown of soil organic carbon, nutrient inputs, and economic returns."
      >
        <Panel className="overflow-x-auto">
          <table className="tabular w-full min-w-[620px] text-left text-sm">
            <thead className="border-b border-line text-xs text-muted">
              <tr>
                <th className="px-5 py-3 font-medium">Season / Phase</th>
                <th className="px-4 py-3 text-right font-medium">SOC (%)</th>
                <th className="px-4 py-3 text-right font-medium">Chemical N (kg/ha)</th>
                <th className="px-4 py-3 text-right font-medium">Organic N (kg/ha)</th>
                <th className="px-4 py-3 text-right font-medium">Cost (₹/ha)</th>
                <th className="px-4 py-3 text-right font-medium">Yield (t/ha)</th>
                <th className="px-5 py-3 text-right font-medium">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {timeline.map((row) => (
                <tr key={row.seasonLabel} className={row.historical ? 'bg-canvas/50 font-medium' : ''}>
                  <td className="px-5 py-3">
                    {row.seasonLabel}
                    {row.historical && (
                      <span className="ml-2 rounded bg-line px-1.5 py-0.5 text-[10px] text-muted font-normal">
                        Current Baseline
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right font-semibold text-accent">{row.organicCarbonPct}%</td>
                  <td className="px-4 py-3 text-right">{row.chemicalNitrogenKgHa}</td>
                  <td className="px-4 py-3 text-right text-accent font-medium">+{row.organicNitrogenKgHa}</td>
                  <td className="px-4 py-3 text-right">{inr(row.fertilizerCostInrHa)}</td>
                  <td className="px-4 py-3 text-right font-semibold text-ink">{fmt(row.yieldTHa, 2)}</td>
                  <td className="px-5 py-3 text-right">
                    {row.historical ? (
                      <span className="text-xs text-muted">Measured</span>
                    ) : (
                      <span className="text-xs text-accent font-medium">IPNS Projected</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
      </Section>

      {/* Agronomic Insights Panel */}
      <Section title="Agronomic Insights & Sustainability Roadmap">
        <Panel className="p-5">
          <div className="space-y-3">
            {insights.map((insight, idx) => (
              <div key={idx} className="flex items-start gap-3 text-sm text-ink-2">
                <CheckCircle className="size-4 text-accent shrink-0 mt-0.5" />
                <span>{insight}</span>
              </div>
            ))}
          </div>
        </Panel>
      </Section>
    </>
  )
}
