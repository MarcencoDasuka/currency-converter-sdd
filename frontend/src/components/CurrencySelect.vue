<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Currency } from '../types/currency'

const props = defineProps<{
  label: string
  modelValue: string
  currencies: Currency[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const searchQuery = ref('')
const isOpen = ref(false)

const filteredCurrencies = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) return props.currencies
  return props.currencies.filter(
    c => c.code.toLowerCase().includes(query) || c.name.toLowerCase().includes(query)
  )
})

const selectedCurrency = computed(() => {
  return props.currencies.find(c => c.code.toUpperCase() === props.modelValue.toUpperCase())
})

function select(code: string) {
  emit('update:modelValue', code)
  isOpen.value = false
  searchQuery.value = ''
}

function toggleDropdown() {
  if (!props.disabled) {
    isOpen.value = !isOpen.value
  }
}
</script>

<template>
  <div class="currency-select-container">
    <label class="select-label">{{ label }}</label>
    
    <div
      class="selected-box"
      :class="{ 'is-open': isOpen, 'is-disabled': disabled }"
      @click="toggleDropdown"
    >
      <div v-if="selectedCurrency" class="currency-info">
        <span class="currency-code">{{ selectedCurrency.code }}</span>
        <span class="currency-name">{{ selectedCurrency.name }}</span>
        <span v-if="selectedCurrency.nominal > 1" class="nominal-badge">
          ×{{ selectedCurrency.nominal }}
        </span>
      </div>
      <div v-else class="currency-info placeholder">
        <span>Select currency...</span>
      </div>
      <span class="chevron">{{ isOpen ? '▲' : '▼' }}</span>
    </div>

    <!-- Dropdown Modal / List -->
    <div v-if="isOpen" class="dropdown-menu">
      <div class="search-box" @click.stop>
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search currency..."
          class="search-input"
          autofocus
        />
      </div>

      <ul class="currency-list">
        <li
          v-for="currency in filteredCurrencies"
          :key="currency.code"
          :class="{ 'is-active': currency.code === modelValue }"
          class="currency-item"
          @click="select(currency.code)"
        >
          <div class="item-left">
            <span class="item-code">{{ currency.code }}</span>
            <span class="item-name">{{ currency.name }}</span>
          </div>
          <div class="item-right">
            <span v-if="currency.nominal > 1" class="item-nominal">Nominal: {{ currency.nominal }}</span>
            <span v-if="currency.code !== 'MDL'" class="item-rate">{{ currency.rate.toFixed(4) }} MDL</span>
          </div>
        </li>
        <li v-if="filteredCurrencies.length === 0" class="empty-notice">
          No currency matches "{{ searchQuery }}"
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.currency-select-container {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.select-label {
  font-size: 0.875rem;
  font-weight: 600;
  color: #374151;
}

.selected-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #ffffff;
  border: 1.5px solid #d1d5db;
  border-radius: 8px;
  cursor: pointer;
  user-select: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  min-height: 48px;
}

.selected-box:hover:not(.is-disabled) {
  border-color: #9ca3af;
}

.selected-box.is-open {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

.selected-box.is-disabled {
  background-color: #f3f4f6;
  cursor: not-allowed;
  opacity: 0.7;
}

.currency-info {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.currency-code {
  font-weight: 700;
  font-size: 1rem;
  color: #111827;
}

.currency-name {
  font-size: 0.85rem;
  color: #6b7280;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 140px;
}

.nominal-badge {
  font-size: 0.7rem;
  font-weight: 600;
  background-color: #e0e7ff;
  color: #3730a3;
  padding: 2px 6px;
  border-radius: 4px;
}

.chevron {
  font-size: 0.75rem;
  color: #6b7280;
}

.dropdown-menu {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 50;
  margin-top: 4px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.12);
  max-height: 280px;
  display: flex;
  flex-direction: column;
}

.search-box {
  padding: 8px;
  border-bottom: 1px solid #f3f4f6;
}

.search-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.875rem;
  outline: none;
  box-sizing: border-box;
}

.search-input:focus {
  border-color: #2563eb;
}

.currency-list {
  list-style: none;
  margin: 0;
  padding: 4px 0;
  overflow-y: auto;
  max-height: 220px;
}

.currency-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.currency-item:hover {
  background-color: #f3f4f6;
}

.currency-item.is-active {
  background-color: #eff6ff;
}

.item-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-code {
  font-weight: 600;
  color: #111827;
}

.item-name {
  font-size: 0.8rem;
  color: #4b5563;
}

.item-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.item-nominal {
  font-size: 0.7rem;
  color: #4338ca;
}

.item-rate {
  font-size: 0.75rem;
  color: #9ca3af;
}

.empty-notice {
  padding: 12px;
  font-size: 0.85rem;
  color: #9ca3af;
  text-align: center;
}
</style>
