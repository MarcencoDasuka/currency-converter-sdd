import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Currency, ConversionResponse, UIStatus } from '../types/currency'
import { apiClient, ApiError } from '../api/apiClient'
import { SnapshotStorage } from '../storage/SnapshotStorage'

export type OfflineTier = 'none' | 'tier1_backend_cached' | 'tier2_client_snapshot'

export const useCurrencyStore = defineStore('currency', () => {
  // Ephemeral UI States
  const status = ref<UIStatus>('idle')
  const currencies = ref<Currency[]>([])
  const sourceCurrency = ref<string>('EUR')
  const targetCurrency = ref<string>('MDL')
  const amount = ref<number | null>(100)
  const conversionResult = ref<ConversionResponse | null>(null)
  const errorMessage = ref<string | null>(null)
  const offlineTier = ref<OfflineTier>('none')
  const selectedDate = ref<string>('')
  const rateDate = ref<string>('')
  const sourceDescription = ref<string>('')
  const rollbackDaysApplied = ref<number>(0)

  // Computed state
  const isFormValid = computed(() => {
    return (
      amount.value !== null &&
      amount.value > 0 &&
      Boolean(sourceCurrency.value) &&
      Boolean(targetCurrency.value)
    )
  })

  const isLoading = computed(() => status.value === 'loading')

  // Actions
  async function loadCurrencies(date?: string): Promise<void> {
    status.value = 'loading'
    errorMessage.value = null
    if (date !== undefined) {
      selectedDate.value = date
    }

    try {
      const data = await apiClient.getCurrencies(date)
      currencies.value = data.currencies
      rateDate.value = data.rateDate
      sourceDescription.value = data.source
      rollbackDaysApplied.value = data.rollbackDaysApplied

      if (data.offline) {
        offlineTier.value = 'tier1_backend_cached'
      } else {
        offlineTier.value = 'none'
      }

      // Persist snapshot to browser storage adapter (outside Pinia)
      SnapshotStorage.saveSnapshot({
        rateDate: data.rateDate,
        source: data.source,
        currencies: data.currencies
      })

      status.value = 'idle'
    } catch (err) {
      console.warn('Network error fetching currencies. Checking local snapshot...', err)

      // Fallback to Tier 2: client-side localStorage snapshot
      const snapshot = SnapshotStorage.loadSnapshot()
      if (snapshot && snapshot.currencies.length > 0) {
        currencies.value = snapshot.currencies
        rateDate.value = snapshot.rateDate
        sourceDescription.value = snapshot.source
        offlineTier.value = 'tier2_client_snapshot'
        status.value = 'offline'
      } else {
        status.value = 'error'
        errorMessage.value =
          err instanceof ApiError ? err.message : 'Cannot connect to currency service and no local snapshot available.'
      }
    }
  }

  async function convert(): Promise<void> {
    if (!isFormValid.value || amount.value === null) {
      errorMessage.value = 'Please provide a valid amount and currencies.'
      return
    }

    status.value = 'loading'
    errorMessage.value = null

    // If already in Tier 2 offline (server completely down), compute locally
    if (offlineTier.value === 'tier2_client_snapshot') {
      const snapshot = SnapshotStorage.loadSnapshot()
      if (snapshot) {
        const localResult = SnapshotStorage.calculateOfflineConversion(
          amount.value,
          sourceCurrency.value,
          targetCurrency.value,
          snapshot
        )
        if (localResult) {
          conversionResult.value = localResult
          status.value = 'success'
          return
        }
      }
    }

    try {
      const response = await apiClient.convert({
        amount: amount.value,
        sourceCurrency: sourceCurrency.value,
        targetCurrency: targetCurrency.value,
        date: selectedDate.value || undefined
      })

      conversionResult.value = response
      rateDate.value = response.rateDate
      sourceDescription.value = response.source
      rollbackDaysApplied.value = response.rollbackDaysApplied

      if (response.offline) {
        offlineTier.value = 'tier1_backend_cached'
      } else {
        offlineTier.value = 'none'
      }

      status.value = 'success'
    } catch (err) {
      console.warn('Conversion network error. Attempting offline fallback...', err)

      // Check if we can fallback to Tier 2
      const snapshot = SnapshotStorage.loadSnapshot()
      if (snapshot) {
        const offlineResult = SnapshotStorage.calculateOfflineConversion(
          amount.value,
          sourceCurrency.value,
          targetCurrency.value,
          snapshot
        )
        if (offlineResult) {
          conversionResult.value = offlineResult
          offlineTier.value = 'tier2_client_snapshot'
          status.value = 'success'
          return
        }
      }

      status.value = 'error'
      errorMessage.value = err instanceof ApiError ? err.message : 'Conversion failed. Please try again.'
    }
  }

  function swapCurrencies(): void {
    const temp = sourceCurrency.value
    sourceCurrency.value = targetCurrency.value
    targetCurrency.value = temp

    if (conversionResult.value && isFormValid.value) {
      convert()
    }
  }

  return {
    status,
    currencies,
    sourceCurrency,
    targetCurrency,
    amount,
    conversionResult,
    errorMessage,
    offlineTier,
    selectedDate,
    rateDate,
    sourceDescription,
    rollbackDaysApplied,
    isFormValid,
    isLoading,
    loadCurrencies,
    convert,
    swapCurrencies
  }
})
