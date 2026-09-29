<script setup lang="ts">
import type { ConversionResponse } from '../types/currency'

defineProps<{
  result: ConversionResponse
}>()
</script>

<template>
  <div class="result-card">
    <div class="header-row">
      <span class="label">Conversion Result</span>
      <span v-if="result.cached" class="badge badge-cached">Cached</span>
      <span v-else class="badge badge-live">Live BNM</span>
    </div>

    <div class="main-result">
      <span class="source-summary">
        {{ result.amount }} {{ result.sourceCurrency }} =
      </span>
      <div class="converted-amount">
        <span class="amount-number">{{ result.convertedAmount.toFixed(4) }}</span>
        <span class="currency-code">{{ result.targetCurrency }}</span>
      </div>
    </div>

    <div class="details-grid">
      <div class="detail-item">
        <span class="detail-title">Exchange Rate</span>
        <span class="detail-value">
          1 {{ result.sourceCurrency }} = {{ result.effectiveRate.toFixed(6) }} {{ result.targetCurrency }}
        </span>
      </div>

      <div class="detail-item">
        <span class="detail-title">Bulletin Date</span>
        <span class="detail-value">{{ result.rateDate }}</span>
      </div>

      <div class="detail-item">
        <span class="detail-title">Data Source</span>
        <span class="detail-value">{{ result.source }}</span>
      </div>

      <div v-if="result.rollbackDaysApplied > 0" class="detail-item rollback-item">
        <span class="detail-title">Notice</span>
        <span class="detail-value rollback-badge">
          Rollback applied: {{ result.rollbackDaysApplied }} days earlier (non-banking day)
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.result-card {
  margin-top: 24px;
  padding: 20px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  animation: fadeIn 0.3s ease-in-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.label {
  font-size: 0.85rem;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.badge {
  font-size: 0.75rem;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 9999px;
}

.badge-live {
  background-color: #dcfce7;
  color: #166534;
}

.badge-cached {
  background-color: #fef3c7;
  color: #92400e;
}

.main-result {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.source-summary {
  font-size: 1rem;
  color: #64748b;
  font-weight: 500;
}

.converted-amount {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.amount-number {
  font-size: 2.25rem;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.currency-code {
  font-size: 1.25rem;
  font-weight: 700;
  color: #2563eb;
}

.details-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-title {
  font-size: 0.75rem;
  color: #64748b;
  font-weight: 500;
}

.detail-value {
  font-size: 0.875rem;
  color: #1e293b;
  font-weight: 600;
}

.rollback-item {
  grid-column: 1 / -1;
}

.rollback-badge {
  color: #b45309;
  background: #fef3c7;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 0.8rem;
  display: inline-block;
}
</style>
