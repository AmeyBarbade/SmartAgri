import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { farmApi, fieldApi, recommendationApi, referenceApi } from '../api/endpoints'
import threePlans from './fixtures/recommendation-three-plans.json'
import { deferred, renderApp, signIn } from './utils'

vi.mock('../api/endpoints', async (orig) => {
  const real = await orig()
  const mocked = (o) => Object.fromEntries(Object.keys(o).map((k) => [k, vi.fn()]))
  return {
    ...real,
    farmApi: mocked(real.farmApi),
    fieldApi: mocked(real.fieldApi),
    referenceApi: mocked(real.referenceApi),
    recommendationApi: mocked(real.recommendationApi),
  }
})

const FARM = { id: 7, name: 'Demo farm', locationName: 'Patna, Bihar', fieldCount: 2 }
const FIELD = {
  id: 163,
  farmId: 7,
  name: 'Wheat - P+K only',
  areaHa: 1.0,
  soilType: 'Loam',
  irrigationType: 'IRRIGATED',
  crop: { id: 2, code: 'WHEAT', name: 'Wheat' },
  growthStage: { id: 9, code: 'TILLERING', name: 'Tillering', seq: 3 },
  season: 'RABI',
  sowingDate: '2025-11-20',
  previousCrop: 'Rice',
  updatedAt: '2026-09-27T10:00:00',
}
const SOIL = { id: 1, fieldId: 163, sampleDate: '2025-11-10', nitrogen: 300, phosphorus: 15, potassium: 200, ph: 7.0, organicCarbon: 0.6 }

beforeEach(() => {
  signIn()
  vi.mocked(farmApi.list).mockResolvedValue([FARM])
  vi.mocked(farmApi.get).mockResolvedValue(FARM)
  vi.mocked(farmApi.fields).mockResolvedValue([FIELD, { ...FIELD, id: 164, name: 'Maize - sowing', crop: { id: 3, code: 'MAIZE', name: 'Maize' }, growthStage: { id: 20, code: 'SOWING', name: 'Sowing', seq: 1 } }])
  vi.mocked(fieldApi.get).mockResolvedValue(FIELD)
  vi.mocked(fieldApi.soilRecords).mockResolvedValue([SOIL])
  vi.mocked(fieldApi.requirement).mockResolvedValue({
    requirementForOptimizer: { kgPerHa: { n: 0, p2o5: 60, k2o: 40 } },
    availableProfiles: ['IRRIGATED_TIMELY_SOWN', 'RAINFED'],
    profile: { code: 'IRRIGATED_TIMELY_SOWN', selectedBecause: 'crop default' },
  })
  vi.mocked(referenceApi.knowledgeBase).mockResolvedValue({
    crops: [{ code: 'WHEAT', profiles: [{ code: 'IRRIGATED_TIMELY_SOWN', description: 'Irrigated, timely sown' }, { code: 'RAINFED', description: 'Rainfed (all zones)' }] }],
  })
  vi.mocked(recommendationApi.history).mockResolvedValue([])
  vi.mocked(recommendationApi.get).mockResolvedValue(threePlans)
})

describe('farm and field selection', () => {
  it('lists the fields of the selected farm with crop and stage, linking to the field', async () => {
    renderApp('/farms/7')
    const link = await screen.findByRole('link', { name: /Wheat - P\+K only/ })
    expect(link).toHaveAttribute('href', '/fields/163')
    expect(within(link).getByText('Wheat · Tillering')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Maize - sowing/ })).toHaveAttribute('href', '/fields/164')
  })
})

describe('field page and recommendation generation', () => {
  it('shows soil, crop and stage, then generates and renders the real response', async () => {
    const pending = deferred()
    vi.mocked(recommendationApi.generate).mockReturnValue(pending.promise)
    renderApp('/fields/163')

    expect(await screen.findByRole('heading', { name: 'Wheat - P+K only' })).toBeInTheDocument()
    expect(screen.getByText('300 kg/ha')).toBeInTheDocument()
    expect(screen.getByText('Tillering')).toBeInTheDocument()
    expect(await screen.findByRole('option', { name: 'Rainfed (all zones)' })).toBeInTheDocument()

    const user = userEvent.setup()
    await user.selectOptions(screen.getByLabelText('Recommendation profile'), 'RAINFED')
    await user.click(screen.getByRole('button', { name: 'Generate Recommendation' }))

    expect(screen.getByRole('button', { name: /Generating/ })).toBeDisabled()
    expect(screen.getByText('Optimising fertilizer plans')).toBeInTheDocument()
    expect(recommendationApi.generate).toHaveBeenCalledWith(163, 'RAINFED')

    pending.resolve(threePlans)
    expect(await screen.findByRole('heading', { name: 'Recommendation — Wheat - P+K only' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Recommended plan' })).toBeInTheDocument()
    // the stored response was passed along, not fetched again
    expect(recommendationApi.get).not.toHaveBeenCalled()
  })

  it('shows a useful message when the ML service is down', async () => {
    vi.mocked(recommendationApi.generate).mockRejectedValue({
      isApiError: true,
      status: 503,
      title: 'Optimisation service unavailable',
      message: 'The fertilizer optimiser and yield model are not reachable. Start the ML service (port 8001) and try again.',
    })
    renderApp('/fields/163')
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Generate Recommendation' }))
    expect(await screen.findByText('Optimisation service unavailable')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Generate Recommendation' })).toBeEnabled()
  })

  it('asks for crop and stage before a recommendation can be generated', async () => {
    vi.mocked(fieldApi.get).mockResolvedValue({ ...FIELD, crop: null, growthStage: null })
    renderApp('/fields/163')
    expect(await screen.findByText('Set the crop and growth stage to generate a recommendation.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Generate Recommendation' })).not.toBeInTheDocument()
  })

  it('shows an empty state when the field has no soil test', async () => {
    vi.mocked(fieldApi.soilRecords).mockResolvedValue([])
    renderApp('/fields/163')
    expect(await screen.findByText('No soil record available')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add soil record' })).toBeInTheDocument()
  })

  it('rejects out-of-range soil values before saving', async () => {
    renderApp('/fields/163')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Edit soil data' }))
    const ph = screen.getByLabelText('pH')
    await user.clear(ph)
    await user.type(ph, '14')
    await user.click(screen.getByRole('button', { name: 'Save soil test' }))
    expect(screen.getByText('Must be between 3 and 11.')).toBeInTheDocument()
    expect(fieldApi.addSoilRecord).not.toHaveBeenCalled()
  })
})

describe('history', () => {
  it('lists stored recommendations and opens one', async () => {
    vi.mocked(recommendationApi.history).mockImplementation(async (fieldId) =>
      fieldId === 163
        ? [{ id: 99, createdAt: '2026-09-27T13:15:00', status: 'OPTIMAL', feasible: true, cropCode: 'WHEAT', stageCode: 'TILLERING', selectedStrategy: 'LOWEST_COST', scoringMode: 'REVENUE_MINUS_COST_AND_EXCESS' }]
        : [],
    )
    renderApp('/history')
    const open = await screen.findByRole('link', { name: 'Open recommendation' })
    expect(open).toHaveAttribute('href', '/recommendations/99')
    await userEvent.setup().click(open)
    expect(await screen.findByRole('heading', { name: 'Recommendation — Wheat - P+K only' })).toBeInTheDocument()
    expect(recommendationApi.get).toHaveBeenCalledWith('99')
  })
})
