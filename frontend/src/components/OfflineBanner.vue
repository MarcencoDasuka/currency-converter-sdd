<script setup lang="ts">
import { computed } from 'vue'
import { useCurrencyStore } from '../stores/useCurrencyStore'

const store = useCurrencyStore()

const isOffline = computed(() => store.offlineTier !== 'none')

const bannerTitle = computed(() => {
  if (store.offlineTier === 'tier1_backend_cached') {
    return 'Operating on Server-Side Cache'
  }
  if (store.offlineTier === 'tier2_client_snapshot') {
    return 'Full Offline Mode (Browser Snapshot)'
  }
  return ''
})

const bannerMessage = computed(() => {
  if (store.offlineTier === 'tier1_backend_cached') {
    return `Live BNM gateway unavailable. Rates successfully served from local PostgreSQL cache (Bulletin Date: ${store.rateDate || 'N/A'}).`
  }
  if (store.offlineTier === 'tier2_client_snapshot') {
    return `Backend server unreachable. Conversion calculated using browser localStorage snapshot (Bulletin Date: ${store.rateDate || 'N/A'}).`
  }
  return ''
})

const bannerClass = computed(() => {
  return store.offlineTier === 'tier2_client_snapshot'
    ? 'banner-tier2'
    : 'banner-tier1'
})
</script>

<template>
  <div v-if="isOffline" :class="['offline-banner', bannerClass]">
    <div class="banner-icon">
      <span v-if="store.offlineTier === 'tier2_client_snapshot'">📡⚠️</span>
      <span v-else>💾ℹ️</span>
    </div>
    <div class="banner-content">
      <strong>{{ bannerTitle }}</strong>
      <p>{{ bannerMessage }}</p>
    </div>
  </div>
</template>

<style scoped>
.offline-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 20px;
  font-size: 0.9rem;
  line-height: 1.4;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.banner-tier1 {
  background-color: #fff8e1;
  border: 1px solid #ffe082;
  color: #795548;
}

.banner-tier2 {
  background-color: #ffebee;
  border: 1px solid #ffcdd2;
  color: #c62828;
}

.banner-icon {
  font-size: 1.5rem;
}

.banner-content strong {
  display: block;
  font-weight: 600;
  margin-bottom: 2px;
}

.banner-content p {
  margin: 0;
}
</style>
