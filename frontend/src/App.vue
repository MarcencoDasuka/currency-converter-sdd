<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useCurrencyStore } from './stores/useCurrencyStore'
import OfflineBanner from './components/OfflineBanner.vue'
import DatePicker from './components/DatePicker.vue'
import CurrencyInput from './components/CurrencyInput.vue'
import CurrencySelect from './components/CurrencySelect.vue'
import ConversionResult from './components/ConversionResult.vue'

const store = useCurrencyStore()
const selectedDate = ref<string>('')

onMounted(() => {
  store.loadCurrencies()
})

function onDateChange() {
  if (selectedDate.value) {
    store.loadCurrencies(selectedDate.value)
  } else {
    store.loadCurrencies()
  }
}

function handleConvert() {
  store.convert()
}
</script>

<template>
  <main class="app-container">
    <div class="converter-card">
      <header class="card-header">
        <div class="logo-area">
          <span class="logo-icon">💱</span>
          <div>
            <h1 class="app-title">Currency Converter</h1>
            <p class="app-subtitle">Spec-Driven Development & National Bank of Moldova</p>
          </div>
        </div>
      </header>

      <OfflineBanner />

      <DatePicker
        v-model="selectedDate"
        :disabled="store.isLoading"
        :rollback-days-applied="store.rollbackDaysApplied"
        :rate-date="store.rateDate"
        @change="onDateChange"
      />

      <form @submit.prevent="handleConvert">
        <CurrencyInput
          v-model="store.amount"
          :disabled="store.isLoading"
        />

        <div class="selectors-row">
          <CurrencySelect
            label="From"
            v-model="store.sourceCurrency"
            :currencies="store.currencies"
            :disabled="store.isLoading"
          />

          <button
            type="button"
            class="swap-button"
            title="Swap currencies"
            :disabled="store.isLoading"
            @click="store.swapCurrencies"
          >
            ⇄
          </button>

          <CurrencySelect
            label="To"
            v-model="store.targetCurrency"
            :currencies="store.currencies"
            :disabled="store.isLoading"
          />
        </div>

        <div v-if="store.errorMessage" class="error-banner">
          ⚠️ {{ store.errorMessage }}
        </div>

        <button
          type="submit"
          class="convert-button"
          :disabled="!store.isFormValid || store.isLoading"
        >
          <span v-if="store.isLoading" class="spinner"></span>
          <span>{{ store.isLoading ? 'Calculating...' : 'Convert' }}</span>
        </button>
      </form>

      <ConversionResult
        v-if="store.conversionResult"
        :result="store.conversionResult"
      />
    </div>

    <footer class="app-footer">
      <p>Data provided by <strong>National Bank of Moldova (BNM)</strong> official exchange bulletin.</p>
      <p>Built with Spring Boot 3, Java 21, PostgreSQL & Vue 3 via GitHub Spec Kit.</p>
    </footer>
  </main>
</template>

<style>
/* Global Reset & Base Styles */
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
  background: #f1f5f9;
  color: #1e293b;
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>

<style scoped>
.app-container {
  width: 100%;
  max-width: 640px;
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.converter-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.08), 0 8px 10px -6px rgba(0, 0, 0, 0.04);
  border: 1px solid #e2e8f0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  gap: 16px;
  flex-wrap: wrap;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  font-size: 2.5rem;
}

.app-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #0f172a;
}

.app-subtitle {
  font-size: 0.825rem;
  color: #64748b;
  margin-top: 2px;
}

.selectors-row {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 20px;
}

.swap-button {
  width: 48px;
  height: 48px;
  border: 1.5px solid #d1d5db;
  border-radius: 8px;
  background: #f8fafc;
  font-size: 1.4rem;
  color: #475569;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.swap-button:hover:not(:disabled) {
  background: #e2e8f0;
  color: #1e293b;
  border-color: #94a3b8;
}

.swap-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.convert-button {
  width: 100%;
  padding: 14px;
  background-color: #2563eb;
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 1.05rem;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: background-color 0.2s, transform 0.1s;
}

.convert-button:hover:not(:disabled) {
  background-color: #1d4ed8;
}

.convert-button:active:not(:disabled) {
  transform: scale(0.99);
}

.convert-button:disabled {
  background-color: #93c5fd;
  cursor: not-allowed;
  opacity: 0.7;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid #ffffff;
  border-bottom-color: transparent;
  border-radius: 50%;
  display: inline-block;
  animation: rotation 1s linear infinite;
}

@keyframes rotation {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.error-banner {
  padding: 12px;
  background-color: #fee2e2;
  border: 1px solid #fca5a5;
  border-radius: 8px;
  color: #991b1b;
  font-size: 0.875rem;
  margin-bottom: 16px;
}

.app-footer {
  text-align: center;
  font-size: 0.75rem;
  color: #94a3b8;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
