import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { fmt, inr } from '../format'

const SELECTED = 'var(--color-chart-1)'
const OTHER = 'var(--color-chart-2)'

/**
 * Three small single-measure charts (cost, predicted yield, excess) rather than one chart with two y-axes.
 * Plan names are on the axis, so identity never depends on colour; the recommended plan is the darker bar.
 */
export default function PlanCharts({ groups, yieldAvailable }) {
  const rows = groups.map((g) => ({
    name: g.members.map((m) => m.label).join(' / '),
    selected: g.selected,
    cost: Number(g.plan.costPerHa),
    yield: g.plan.yield?.available ? Number(g.plan.yield.predictedYieldTHa) : null,
    excess: Number(g.plan.totalExcessKgHa),
  }))

  const charts = [
    { key: 'cost', title: 'Fertilizer cost', unit: '₹/ha', format: (v) => inr(v) },
    yieldAvailable && { key: 'yield', title: 'Predicted yield', unit: 't/ha', format: (v) => fmt(v, 3) },
    { key: 'excess', title: 'Total nutrient excess', unit: 'kg/ha', format: (v) => fmt(v, 1) },
  ].filter(Boolean)

  return (
    <div className={`grid gap-6 ${charts.length === 3 ? 'md:grid-cols-3' : 'md:grid-cols-2'}`}>
      {charts.map((c) => (
        <figure key={c.key} className="min-w-0">
          <figcaption className="mb-2 text-[13px] font-medium text-ink-2">
            {c.title} <span className="font-normal text-muted">({c.unit})</span>
          </figcaption>
          <div className="h-44">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={rows} layout="vertical" margin={{ top: 0, right: 56, bottom: 0, left: 0 }} barCategoryGap={8}>
                <XAxis type="number" hide domain={[0, 'auto']} />
                <YAxis
                  type="category"
                  dataKey="name"
                  width={104}
                  tickLine={false}
                  axisLine={{ stroke: 'var(--color-line)' }}
                  tick={{ fontSize: 12, fill: 'var(--color-muted)' }}
                />
                <Tooltip
                  cursor={{ fill: 'var(--color-canvas)' }}
                  formatter={(v) => [`${c.format(v)} ${c.unit === '₹/ha' ? '/ha' : c.unit}`, c.title]}
                  contentStyle={{ fontSize: 12, borderRadius: 6, border: '1px solid var(--color-line)' }}
                />
                <Bar
                  dataKey={c.key}
                  radius={[0, 4, 4, 0]}
                  maxBarSize={22}
                  isAnimationActive={false}
                  label={{ position: 'right', fontSize: 12, fill: 'var(--color-ink-2)', formatter: (v) => (v == null ? '' : c.format(v)) }}
                >
                  {rows.map((r) => (
                    <Cell key={r.name} fill={r.selected ? SELECTED : OTHER} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </figure>
      ))}
    </div>
  )
}
