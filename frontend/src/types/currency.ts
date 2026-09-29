export interface Currency {
  code: string
  name: string
  nominal: number
  rate: number
}

export interface CurrenciesResponse {
  rateDate: string
  requestedDate: string
  source: string
  cached: boolean
  offline: boolean
  rollbackDaysApplied: number
  currencies: Currency[]
}

export interface ConversionRequest {
  amount: number
  sourceCurrency: string
  targetCurrency: string
  date?: string
}

export interface ConversionResponse {
  amount: number
  sourceCurrency: string
  targetCurrency: string
  convertedAmount: number
  effectiveRate: number
  rateDate: string
  source: string
  cached: boolean
  offline: boolean
  rollbackDaysApplied: number
}

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  invalidParams?: Array<{ name: string; reason: string }>
}

export type UIStatus = 'idle' | 'loading' | 'success' | 'error' | 'offline'
