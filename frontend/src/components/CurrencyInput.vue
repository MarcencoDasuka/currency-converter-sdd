<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  modelValue: number | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | null): void
}>()

const validationError = computed(() => {
  if (props.modelValue === null || props.modelValue === undefined) {
    return 'Amount is required'
  }
  if (isNaN(props.modelValue)) {
    return 'Amount must be a valid number'
  }
  if (props.modelValue <= 0) {
    return 'Amount must be strictly greater than zero'
  }
  return null
})

function onInput(event: Event) {
  const target = event.target as HTMLInputElement
  const raw = target.value.trim()

  if (raw === '') {
    emit('update:modelValue', null)
    return
  }

  const parsed = parseFloat(raw)
  if (!isNaN(parsed)) {
    emit('update:modelValue', parsed)
  } else {
    emit('update:modelValue', null)
  }
}
</script>

<template>
  <div class="currency-input-group">
    <label for="amount-input" class="input-label">Amount</label>
    <div class="input-wrapper" :class="{ 'has-error': !!validationError }">
      <input
        id="amount-input"
        type="number"
        step="any"
        min="0.01"
        :value="modelValue ?? ''"
        :disabled="disabled"
        placeholder="Enter amount (e.g. 100)"
        class="amount-field"
        @input="onInput"
      />
    </div>
    <span v-if="validationError" class="error-text">{{ validationError }}</span>
  </div>
</template>

<style scoped>
.currency-input-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
}

.input-label {
  font-size: 0.875rem;
  font-weight: 600;
  color: #374151;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  border: 1.5px solid #d1d5db;
  border-radius: 8px;
  background: #ffffff;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-wrapper:focus-within {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

.input-wrapper.has-error {
  border-color: #ef4444;
}

.input-wrapper.has-error:focus-within {
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.15);
}

.amount-field {
  width: 100%;
  padding: 12px 14px;
  font-size: 1.1rem;
  font-weight: 500;
  border: none;
  background: transparent;
  outline: none;
  color: #111827;
}

.amount-field:disabled {
  background-color: #f3f4f6;
  color: #9ca3af;
  cursor: not-allowed;
}

.error-text {
  font-size: 0.8rem;
  color: #dc2626;
  font-weight: 500;
}
</style>
