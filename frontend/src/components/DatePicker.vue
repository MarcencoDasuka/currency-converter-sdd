<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  modelValue: string
  disabled?: boolean
  rollbackDaysApplied?: number
  rateDate?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change'): void
}>()

const MIN_DATE = '1994-01-01'

function getLocalIsoDate(d: Date = new Date()): string {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getYesterdayIsoDate(): string {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  return getLocalIsoDate(d)
}

function getLastFridayIsoDate(): string {
  const d = new Date()
  const day = d.getDay()
  if (day === 0) {
    d.setDate(d.getDate() - 2)
  } else if (day === 6) {
    d.setDate(d.getDate() - 1)
  } else if (day === 5) {
    // Already Friday
  } else {
    d.setDate(d.getDate() - (day + 2))
  }
  return getLocalIsoDate(d)
}

const maxDate = computed(() => getLocalIsoDate())
const todayDate = computed(() => getLocalIsoDate())
const yesterdayDate = computed(() => getYesterdayIsoDate())
const lastFridayDate = computed(() => getLastFridayIsoDate())

const isToday = computed(() => props.modelValue === todayDate.value || !props.modelValue)

const dateValidationError = computed(() => {
  if (!props.modelValue) return null
  if (props.modelValue > maxDate.value) {
    return 'Дата не может быть в будущем — торги НБМ еще не проведены'
  }
  if (props.modelValue < MIN_DATE) {
    return 'Курсы молдавского лея доступны с 01.01.1994'
  }
  return null
})

const weekendNotice = computed(() => {
  if (!props.modelValue) return ''
  const parts = props.modelValue.split('-').map(Number)
  if (parts.length !== 3) return ''
  const dt = new Date(parts[0], parts[1] - 1, parts[2])
  const day = dt.getDay()
  if (day === 6) {
    return 'Суббота — небанковский день. НБМ применит официальный курс за предшествующую пятницу (откат на 1 день).'
  }
  if (day === 0) {
    return 'Воскресенье — небанковский день. НБМ применит официальный курс за предшествующую пятницу (откат на 2 дня).'
  }
  return ''
})

function onInput(e: Event) {
  const target = e.target as HTMLInputElement
  let val = target.value

  // Enforce max constraint if typed manually
  if (val && val > maxDate.value) {
    val = maxDate.value
  }

  emit('update:modelValue', val)
  emit('change')
}

function selectPreset(dateVal: string) {
  if (props.disabled) return
  emit('update:modelValue', dateVal)
  emit('change')
}
</script>

<template>
  <div class="date-picker-container">
    <div class="picker-header">
      <label for="date-input" class="picker-label">
        <span class="label-icon">📅</span>
        <span class="label-text">Дата котировок (Rate Date)</span>
      </label>

      <div class="preset-buttons">
        <button
          type="button"
          class="preset-btn"
          :class="{ active: isToday }"
          :disabled="disabled"
          title="Установить сегодняшнюю дату"
          @click="selectPreset(todayDate)"
        >
          Сегодня
        </button>
        <button
          type="button"
          class="preset-btn"
          :class="{ active: modelValue === yesterdayDate }"
          :disabled="disabled"
          title="Установить вчерашнюю дату"
          @click="selectPreset(yesterdayDate)"
        >
          Вчера
        </button>
        <button
          type="button"
          class="preset-btn"
          :class="{ active: modelValue === lastFridayDate }"
          :disabled="disabled"
          title="Установить последнюю пятницу (последний рабочий день)"
          @click="selectPreset(lastFridayDate)"
        >
          Пятница
        </button>
      </div>
    </div>

    <div class="input-wrapper">
      <input
        id="date-input"
        type="date"
        class="date-native-input"
        :class="{ 'has-error': dateValidationError }"
        :value="modelValue || todayDate"
        :min="MIN_DATE"
        :max="maxDate"
        :disabled="disabled"
        @input="onInput"
      />

      <span v-if="rateDate && rateDate !== modelValue && !weekendNotice" class="bulletin-indicator">
        Бюллетень: {{ rateDate }}
      </span>
    </div>

    <!-- Error notice -->
    <div v-if="dateValidationError" class="date-alert alert-error">
      ⚠️ {{ dateValidationError }}
    </div>

    <!-- Weekend notice -->
    <div v-else-if="weekendNotice" class="date-alert alert-warning">
      ℹ️ {{ weekendNotice }}
    </div>

    <!-- Rollback notice -->
    <div v-else-if="rollbackDaysApplied && rollbackDaysApplied > 0" class="date-alert alert-info">
      ↺ Применен откат на {{ rollbackDaysApplied }} дн. (официальный курс за {{ rateDate }})
    </div>
  </div>
</template>

<style scoped>
.date-picker-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;
  padding: 14px 16px;
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.picker-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.85rem;
  font-weight: 600;
  color: #334155;
  cursor: pointer;
}

.label-icon {
  font-size: 1rem;
}

.preset-buttons {
  display: flex;
  gap: 6px;
}

.preset-btn {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 0.75rem;
  font-weight: 500;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s ease-in-out;
}

.preset-btn:hover:not(:disabled) {
  background: #f1f5f9;
  border-color: #94a3b8;
  color: #0f172a;
}

.preset-btn.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #ffffff;
  font-weight: 600;
}

.preset-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.date-native-input {
  flex: 1;
  min-width: 180px;
  padding: 8px 12px;
  font-size: 0.9rem;
  font-family: inherit;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background-color: #ffffff;
  color: #1e293b;
  outline: none;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.date-native-input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

.date-native-input.has-error {
  border-color: #ef4444;
}

.date-native-input:disabled {
  background-color: #f1f5f9;
  cursor: not-allowed;
  opacity: 0.7;
}

.bulletin-indicator {
  font-size: 0.8rem;
  color: #64748b;
  background: #e2e8f0;
  padding: 4px 8px;
  border-radius: 4px;
}

.date-alert {
  font-size: 0.8rem;
  line-height: 1.35;
  padding: 6px 10px;
  border-radius: 6px;
}

.alert-error {
  background-color: #fee2e2;
  border: 1px solid #fca5a5;
  color: #991b1b;
}

.alert-warning {
  background-color: #fef3c7;
  border: 1px solid #fde68a;
  color: #92400e;
}

.alert-info {
  background-color: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1e40af;
}
</style>
