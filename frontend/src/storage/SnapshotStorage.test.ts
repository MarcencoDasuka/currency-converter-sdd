import { describe, it, expect, beforeEach } from 'vitest'
import { SnapshotStorage, type StorageSnapshot } from './SnapshotStorage'

// In-memory mock for localStorage in Node test environment
const storeMap = new Map<string, string>()
const localStorageMock = {
  getItem: (key: string) => storeMap.get(key) ?? null,
  setItem: (key: string, value: string) => storeMap.set(key, value),
  removeItem: (key: string) => storeMap.delete(key),
  clear: () => storeMap.clear()
}
globalThis.localStorage = localStorageMock as unknown as Storage

describe('SnapshotStorage', () => {
  const sampleSnapshot: Omit<StorageSnapshot, 'savedAt'> = {
    rateDate: '2026-09-28',
    source: 'National Bank of Moldova',
    currencies: [
      { code: 'MDL', name: 'Moldovan Leu', nominal: 1, rate: 1.0 },
      { code: 'USD', name: 'US Dollar', nominal: 1, rate: 17.8250 },
      { code: 'EUR', name: 'Euro', nominal: 1, rate: 19.4500 },
      { code: 'JPY', name: 'Japanese Yen', nominal: 100, rate: 11.8500 }
    ]
  }

  beforeEach(() => {
    localStorage.clear()
  })

  it('should save and load snapshot to/from localStorage', () => {
    SnapshotStorage.saveSnapshot(sampleSnapshot)
    const loaded = SnapshotStorage.loadSnapshot()

    expect(loaded).not.toBeNull()
    expect(loaded?.rateDate).toBe('2026-09-28')
    expect(loaded?.currencies).toHaveLength(4)
    expect(loaded?.savedAt).toBeDefined()
  })

  it('should return identity conversion for identical currencies in offline mode', () => {
    const fullSnapshot: StorageSnapshot = {
      ...sampleSnapshot,
      savedAt: new Date().toISOString()
    }

    const result = SnapshotStorage.calculateOfflineConversion(150, 'EUR', 'EUR', fullSnapshot)

    expect(result).not.toBeNull()
    expect(result?.convertedAmount).toBe(150.0000)
    expect(result?.effectiveRate).toBe(1.0)
    expect(result?.offline).toBe(true)
  })

  it('should accurately calculate cross rates offline accounting for nominal', () => {
    const fullSnapshot: StorageSnapshot = {
      ...sampleSnapshot,
      savedAt: new Date().toISOString()
    }

    // 10 USD to JPY: 10 * (17.8250 / (11.8500 / 100)) = 1504.2194
    const result = SnapshotStorage.calculateOfflineConversion(10, 'USD', 'JPY', fullSnapshot)

    expect(result).not.toBeNull()
    expect(result?.convertedAmount).toBe(1504.2194)
    expect(result?.effectiveRate).toBe(150.421941)
    expect(result?.source).toContain('Client Offline Snapshot')
  })

  it('should return null when requested currency is missing from snapshot', () => {
    const fullSnapshot: StorageSnapshot = {
      ...sampleSnapshot,
      savedAt: new Date().toISOString()
    }

    const result = SnapshotStorage.calculateOfflineConversion(100, 'USD', 'NON_EXISTENT', fullSnapshot)
    expect(result).toBeNull()
  })

  it('should reject corrupted or invalid snapshot schema from localStorage', () => {
    // Malformed JSON data in localStorage
    localStorage.setItem('currency_converter_last_snapshot', JSON.stringify({ rateDate: '2026-09-28', currencies: 'invalid' }))
    const loaded = SnapshotStorage.loadSnapshot()
    expect(loaded).toBeNull()

    // Currency with negative rate
    localStorage.setItem('currency_converter_last_snapshot', JSON.stringify({
      rateDate: '2026-09-28',
      source: 'test',
      currencies: [{ code: 'USD', name: 'Dollar', nominal: 1, rate: -5 }]
    }))
    expect(SnapshotStorage.loadSnapshot()).toBeNull()
  })

  it('should return null for non-positive or invalid offline conversion amounts', () => {
    const fullSnapshot: StorageSnapshot = {
      ...sampleSnapshot,
      savedAt: new Date().toISOString()
    }

    expect(SnapshotStorage.calculateOfflineConversion(-10, 'USD', 'EUR', fullSnapshot)).toBeNull()
    expect(SnapshotStorage.calculateOfflineConversion(0, 'USD', 'EUR', fullSnapshot)).toBeNull()
    expect(SnapshotStorage.calculateOfflineConversion(NaN, 'USD', 'EUR', fullSnapshot)).toBeNull()
  })
})
