import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { groupPlans } from '../recommendation/plans'
import RecommendationView from '../recommendation/RecommendationView'
import identical from './fixtures/recommendation-identical-plans.json'
import threePlans from './fixtures/recommendation-three-plans.json'

// Fixtures are real responses captured from POST /api/fields/{id}/recommendations (Spring Boot + FastAPI, 2026-09-27).

const planTable = () => screen.getAllByRole('table')[0]

describe('recommendation view', () => {
  it('renders the requirement, the three distinct plans and highlights the selected one', () => {
    render(<RecommendationView rec={threePlans} />)

    const req = screen.getByRole('heading', { name: 'Nutrient requirement' }).closest('section')
    expect(within(req).getByText('P₂O₅')).toBeInTheDocument()
    expect(within(req).getByText('60')).toBeInTheDocument()

    const table = planTable()
    const headers = within(table).getAllByRole('columnheader').map((h) => h.textContent)
    expect(headers).toEqual(['', 'RecommendedLowest cost', 'Minimum excess', 'Balanced'])
    const costRow = within(table).getAllByRole('row').find((r) => r.textContent.startsWith('Cost ('))
    expect(within(costRow).getAllByRole('cell').map((c) => c.textContent)).toEqual(['₹5,697', '₹6,392', '₹6,044'])
    expect(within(table).getByText('3.32')).toBeInTheDocument() // predicted yield 3.317 t/ha
    expect(within(table).getByText('23.2')).toBeInTheDocument() // N excess of the lowest-cost plan

    const selected = screen.getByRole('heading', { name: 'Recommended plan' }).closest('section')
    expect(within(selected).getByText('Diammonium Phosphate (DAP)')).toBeInTheDocument()
    expect(within(selected).getByText('43.48')).toBeInTheDocument()
    expect(within(selected).getByText('3.32 t/ha')).toBeInTheDocument()
    expect(
      within(selected).getByText("Recommended based on the system's cost, predicted yield and nutrient-excess scoring."),
    ).toBeInTheDocument()
    expect(within(selected).getByText(threePlans.selectedPlan.reason)).toBeInTheDocument()
  })

  it('collapses plans marked sameAs into one column', () => {
    render(<RecommendationView rec={identical} />)
    const headers = within(planTable()).getAllByRole('columnheader')
    expect(headers).toHaveLength(2)
    expect(headers[1]).toHaveTextContent('Lowest cost · Minimum excess · Balanced')
    expect(headers[1]).toHaveTextContent('Recommended')
    expect(screen.getByText('All strategies produced the same plan, so there is nothing to chart.')).toBeInTheDocument()
  })

  it('keeps the selected plan as the representative of its group', () => {
    const plans = identical.plans.map((p) => ({ ...p, selected: p.strategy === 'BALANCED' }))
    const [group] = groupPlans(plans)
    expect(group.plan.strategy).toBe('BALANCED')
    expect(group.members).toHaveLength(3)
  })

  it('shows every backend warning and assumption', () => {
    render(<RecommendationView rec={threePlans} />)
    const section = screen.getByRole('heading', { name: 'Warnings & assumptions' }).closest('section')
    for (const w of threePlans.warnings) expect(within(section).getByText(w)).toBeInTheDocument()
    for (const a of threePlans.assumptions) expect(within(section).getByText(a)).toBeInTheDocument()
    expect(within(section).getByText(threePlans.disclaimer)).toBeInTheDocument()
  })

  it('explains an infeasible result instead of showing plans', () => {
    const infeasible = {
      ...threePlans,
      status: 'INFEASIBLE',
      feasible: false,
      infeasibilityReason: 'The requirement cannot be met within 500 kg/ha per product.',
      infeasibility: [{ nutrient: 'N', requiredKgHa: 300, maxSupplyKgHa: 230, reason: 'Urea capped at 500 kg/ha' }],
      plans: [],
      selectedPlan: null,
      scoring: null,
    }
    render(<RecommendationView rec={infeasible} />)
    expect(screen.getByText('No feasible fertilizer plan')).toBeInTheDocument()
    expect(screen.getByText(infeasible.infeasibilityReason)).toBeInTheDocument()
    expect(screen.getByText('Urea capped at 500 kg/ha')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Recommended plan' })).not.toBeInTheDocument()
  })

  it('renders confidence badge, soil deficiency explainability chart, and over-fertilization impact alert', () => {
    render(<RecommendationView rec={threePlans} />)

    // 1. Confidence badge
    expect(screen.getByText(/High Confidence — ICAR STCR Grounded/i)).toBeInTheDocument()

    // 2. Soil Deficiency Explainability Chart
    expect(screen.getByText(/Soil Health vs. Agronomic Critical Benchmarks/i)).toBeInTheDocument()
    expect(screen.getByText(/ICAR Medium Range: 280 – 560 kg\/ha/i)).toBeInTheDocument()
    expect(screen.getAllByText(/ADEQUATE \(Maintenance Dose\)/i)).toHaveLength(3)

    // 3. Over-fertilization / Soil degradation impact alert
    expect(screen.getByRole('heading', { name: /Over-Fertilization & Soil Degradation Impact Analysis/i })).toBeInTheDocument()
    expect(screen.getAllByText(/Soil Acidification/i).length).toBeGreaterThan(0)
    expect(screen.getByText(/Nitrate Leaching/i)).toBeInTheDocument()
    expect(screen.getByText(/Biological Degradation/i)).toBeInTheDocument()
  })
})
