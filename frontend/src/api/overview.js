import { farmApi, fieldApi, recommendationApi } from './endpoints'

/**
 * Farms with their fields, each field's latest soil record and latest stored recommendation.
 * One request per field is fine at prototype scale (a handful of fields per user).
 */
export async function loadOverview() {
  const farms = await farmApi.list()
  return Promise.all(
    farms.map(async (farm) => {
      const fields = await farmApi.fields(farm.id)
      const rows = await Promise.all(
        fields.map(async (field) => {
          const [soil, history] = await Promise.all([
            fieldApi.soilRecords(field.id),
            recommendationApi.history(field.id),
          ])
          return { field, latestSoil: soil[0] ?? null, history, latestRecommendation: history[0] ?? null }
        }),
      )
      return { farm, fields: rows }
    }),
  )
}
