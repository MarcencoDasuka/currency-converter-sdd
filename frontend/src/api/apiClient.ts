import type { CurrenciesResponse, ConversionRequest, ConversionResponse, ProblemDetail } from '../types/currency'

export class ApiError extends Error {
  problem?: ProblemDetail
  status?: number

  constructor(message: string, status?: number, problem?: ProblemDetail) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export const apiClient = {
  async getCurrencies(date?: string): Promise<CurrenciesResponse> {
    const url = date ? `/api/v1/currencies?date=${encodeURIComponent(date)}` : '/api/v1/currencies'
    const response = await fetch(url, {
      headers: {
        'Accept': 'application/json'
      }
    })

    if (!response.ok) {
      let problem: ProblemDetail | undefined
      try {
        problem = await response.json()
      } catch (_) {}
      throw new ApiError(
        problem?.detail || `Failed to fetch currencies (HTTP ${response.status})`,
        response.status,
        problem
      )
    }

    return response.json()
  },

  async convert(request: ConversionRequest): Promise<ConversionResponse> {
    const response = await fetch('/api/v1/convert', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json, application/problem+json'
      },
      body: JSON.stringify(request)
    })

    if (!response.ok) {
      let problem: ProblemDetail | undefined
      try {
        problem = await response.json()
      } catch (_) {}
      throw new ApiError(
        problem?.detail || `Conversion request failed (HTTP ${response.status})`,
        response.status,
        problem
      )
    }

    return response.json()
  }
}
