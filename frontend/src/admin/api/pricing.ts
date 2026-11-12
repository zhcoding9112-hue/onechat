import { http } from '@/api/http'

export interface SpecView {
  id: number
  serviceItemId: number
  code: string
  name: string
  durationMinutes: number | null
  sortOrder: number
  enabled: boolean
}

export interface ItemView {
  id: number
  code: string
  name: string
  description: string | null
  sortOrder: number
  enabled: boolean
  specs: SpecView[]
}

export interface PriceView {
  id: number
  talentId: number
  serviceSpecId: number
  unitPriceCents: number
  enabled: boolean
}

export interface CommissionView {
  id: number
  talentId: number | null
  rateBp: number
  rateText: string
}

export interface CommissionSettings {
  defaultConfig: CommissionView
  overrides: CommissionView[]
}

const BASE = '/api/admin/pricing'

export const pricingApi = {
  listItems: () => http.get<ItemView[]>(`${BASE}/items`),

  createItem: (body: { code: string; name: string; description?: string; sortOrder?: number }) =>
    http.post<ItemView>(`${BASE}/items`, body),

  updateItem: (id: number, body: { name?: string; description?: string; sortOrder?: number }) =>
    http.put<ItemView>(`${BASE}/items/${id}`, body),

  setItemEnabled: (id: number, enabled: boolean) =>
    http.put<ItemView>(`${BASE}/items/${id}/enabled/${enabled}`),

  createSpec: (
    itemId: number,
    body: { code: string; name: string; durationMinutes?: number; sortOrder?: number },
  ) => http.post<SpecView>(`${BASE}/items/${itemId}/specs`, body),

  updateSpec: (id: number, body: { name?: string; durationMinutes?: number; sortOrder?: number }) =>
    http.put<SpecView>(`${BASE}/specs/${id}`, body),

  setSpecEnabled: (id: number, enabled: boolean) =>
    http.put<SpecView>(`${BASE}/specs/${id}/enabled/${enabled}`),

  listPrices: (talentId: number) => http.get<PriceView[]>(`${BASE}/talents/${talentId}/prices`),

  setPrice: (talentId: number, specId: number, unitPriceCents: number, enabled = true) =>
    http.put<PriceView>(`${BASE}/talents/${talentId}/prices/${specId}`, { unitPriceCents, enabled }),

  setPriceEnabled: (talentId: number, specId: number, enabled: boolean) =>
    http.put<PriceView>(`${BASE}/talents/${talentId}/prices/${specId}/enabled/${enabled}`),

  getCommission: () => http.get<CommissionSettings>(`${BASE}/commission`),

  setDefaultRate: (rateBp: number) =>
    http.put<CommissionView>(`${BASE}/commission/default`, { rateBp }),

  setTalentRate: (talentId: number, rateBp: number) =>
    http.put<CommissionView>(`${BASE}/commission/talents/${talentId}`, { rateBp }),

  removeTalentRate: (talentId: number) =>
    http.delete<void>(`${BASE}/commission/talents/${talentId}`),
}
