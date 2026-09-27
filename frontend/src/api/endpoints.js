import { api } from './client'

const data = (p) => p.then((r) => r.data)

export const authApi = {
  login: (email, password) => data(api.post('/api/auth/login', { email, password })),
  me: () => data(api.get('/api/auth/me')),
}

export const farmApi = {
  list: () => data(api.get('/api/farms')),
  get: (farmId) => data(api.get(`/api/farms/${farmId}`)),
  fields: (farmId) => data(api.get(`/api/farms/${farmId}/fields`)),
}

export const fieldApi = {
  get: (fieldId) => data(api.get(`/api/fields/${fieldId}`)),
  update: (fieldId, body) => data(api.put(`/api/fields/${fieldId}`, body)),
  soilRecords: (fieldId) => data(api.get(`/api/fields/${fieldId}/soil-records`)),
  addSoilRecord: (fieldId, body) => data(api.post(`/api/fields/${fieldId}/soil-records`, body)),
  requirement: (fieldId, profile) =>
    data(api.get(`/api/fields/${fieldId}/nutrient-requirement`, { params: profile ? { profile } : {} })),
}

export const referenceApi = {
  crops: () => data(api.get('/api/crops')),
  stages: (cropId) => data(api.get(`/api/crops/${cropId}/stages`)),
  knowledgeBase: () => data(api.get('/api/knowledge-base')),
}

export const recommendationApi = {
  generate: (fieldId, profile) =>
    data(api.post(`/api/fields/${fieldId}/recommendations`, null, { params: profile ? { profile } : {} })),
  history: (fieldId) => data(api.get(`/api/fields/${fieldId}/recommendations`)),
  get: (id) => data(api.get(`/api/recommendations/${id}`)),
}

/** Converts a FieldResponse back into the FieldRequest body that PUT /api/fields/{id} expects. */
export function fieldToRequest(field, overrides = {}) {
  return {
    name: field.name,
    areaHa: field.areaHa,
    soilType: field.soilType ?? null,
    irrigationType: field.irrigationType ?? null,
    cropId: field.crop?.id ?? null,
    growthStageId: field.growthStage?.id ?? null,
    season: field.season ?? null,
    sowingDate: field.sowingDate ?? null,
    previousCrop: field.previousCrop ?? null,
    ...overrides,
  }
}
