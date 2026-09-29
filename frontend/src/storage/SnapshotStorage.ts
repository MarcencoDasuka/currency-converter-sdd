import type { Currency, ConversionResponse } from '../types/currency'

const SNAPSHOT_KEY = 'currency_converter_last_snapshot'

export interface StorageSnapshot {
  savedAt: string
  rateDate: string
  source: string
  currencies: Currency[]
}

export class SnapshotStorage {
  static saveSnapshot(snapshot: Omit<StorageSnapshot, 'savedAt'>): void {
    try {
      const data: StorageSnapshot = {
        ...snapshot,
        savedAt: new Date().toISOString()
      }
      localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(data))
    } catch (e) {
      console.warn('Failed to save snapshot to localStorage', e)
    }
  }

  static loadSnapshot(): StorageSnapshot | null {
    try {
      const item = localStorage.getItem(SNAPSHOT_KEY)
      if (!item) return null
      return JSON.parse(item) as StorageSnapshot
    } catch (e) {
      console.warn('Failed to load snapshot from localStorage', e)
      return null
    }
  }

  static calculateOfflineConversion(
    amount: number,
    sourceCode: string,
    targetCode: string,
    snapshot: StorageSnapshot
  ): ConversionResponse | null {
    const sCode = sourceCode.toUpperCase()
    const tCode = targetCode.toUpperCase()

    if (sCode === tCode) {
      return {
        amount,
        sourceCurrency: sCode,
        targetCurrency: tCode,
        convertedAmount: Number(amount.toFixed(4)),
        effectiveRate: 1.0,
        rateDate: snapshot.rateDate,
        source: `${snapshot.source} (Client Offline Snapshot)`,
        cached: true,
        offline: true,
        rollbackDaysApplied: 0
      }
    }

    const source = snapshot.currencies.find(c => c.code.toUpperCase() === sCode)
    const target = snapshot.currencies.find(c => c.code.toUpperCase() === tCode)

    if (!source || !target) {
      return null
    }

    // MDL per 1 unit of currency = rate / nominal
    const mdlPerSource = source.rate / source.nominal
    const mdlPerTarget = target.rate / target.nominal

    const effectiveRate = mdlPerSource / mdlPerTarget
    const convertedAmount = amount * effectiveRate

    return {
      amount,
      sourceCurrency: sCode,
      targetCurrency: tCode,
      convertedAmount: Number(convertedAmount.toFixed(4)),
      effectiveRate: Number(effectiveRate.toFixed(6)),
      rateDate: snapshot.rateDate,
      source: `${snapshot.source} (Client Offline Snapshot)`,
      cached: true,
      offline: true,
      rollbackDaysApplied: 0
    }
  }
}
