import type { Currency, ConversionResponse } from '../types/currency'

const SNAPSHOT_KEY = 'currency_converter_last_snapshot'

export interface StorageSnapshot {
  savedAt: string
  rateDate: string
  source: string
  currencies: Currency[]
}

function isValidSnapshot(data: unknown): data is StorageSnapshot {
  if (!data || typeof data !== 'object') return false
  const s = data as Partial<StorageSnapshot>
  if (typeof s.rateDate !== 'string' || typeof s.source !== 'string' || !Array.isArray(s.currencies)) {
    return false
  }
  return (
    s.currencies.length > 0 &&
    s.currencies.every(
      c =>
        typeof c === 'object' &&
        c !== null &&
        typeof c.code === 'string' &&
        c.code.trim().length === 3 &&
        typeof c.nominal === 'number' &&
        c.nominal > 0 &&
        typeof c.rate === 'number' &&
        c.rate > 0
    )
  )
}

function divideAndRoundHalfUp(num: bigint, den: bigint, targetDecimals: number): number {
  if (den === 0n) throw new Error('Division by zero')
  const scale = 10n ** BigInt(targetDecimals)
  const scaledNum = num * scale * 10n
  const scaledDen = den * 10n
  const half = scaledDen / 2n
  const quotient = (scaledNum + half) / scaledDen
  return Number(quotient) / Number(scale)
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
      const parsed: unknown = JSON.parse(item)
      if (isValidSnapshot(parsed)) {
        return parsed
      }
      console.warn('Invalid snapshot format in localStorage. Ignoring corrupted data.')
      return null
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
    if (!amount || amount <= 0 || isNaN(amount)) {
      return null
    }

    const sCode = sourceCode.trim().toUpperCase()
    const tCode = targetCode.trim().toUpperCase()

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

    if (!source || !target || source.rate <= 0 || target.rate <= 0) {
      return null
    }

    // Exact rational arithmetic to prevent IEEE-754 precision loss:
    // MDL_per_Source = source.rate / source.nominal
    // MDL_per_Target = target.rate / target.nominal
    // EffectiveRate = (source.rate * target.nominal) / (target.rate * source.nominal)
    const rateScale = 1_000_000n
    const sourceRateMicro = BigInt(Math.round(source.rate * 1_000_000))
    const targetRateMicro = BigInt(Math.round(target.rate * 1_000_000))

    const num = sourceRateMicro * BigInt(target.nominal)
    const den = targetRateMicro * BigInt(source.nominal)

    const effectiveRate = divideAndRoundHalfUp(num, den, 6)

    // Converted Amount = amount * EffectiveRate
    const amountCents = BigInt(Math.round(amount * 10_000))
    const convertedAmount = divideAndRoundHalfUp(num * amountCents, den * 10_000n, 4)

    return {
      amount,
      sourceCurrency: sCode,
      targetCurrency: tCode,
      convertedAmount,
      effectiveRate,
      rateDate: snapshot.rateDate,
      source: `${snapshot.source} (Client Offline Snapshot)`,
      cached: true,
      offline: true,
      rollbackDaysApplied: 0
    }
  }
}
