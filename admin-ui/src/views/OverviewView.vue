<script setup lang="ts">
import { AlertTriangle, CreditCard, LayoutGrid, Package, Soup } from '@lucide/vue'

const metrics = [
  { label: 'Open table sessions', value: '-', icon: LayoutGrid },
  { label: 'Low-stock ingredients', value: '-', icon: Package },
  { label: 'Kitchen queue', value: '-', icon: Soup },
  { label: 'Payments today', value: '-', icon: CreditCard },
]
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Live operations</p>
        <h2>Admin workbench</h2>
      </div>
    </div>

    <div class="metric-grid">
      <article v-for="metric in metrics" :key="metric.label" class="metric-card">
        <component :is="metric.icon" :size="20" />
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
      </article>
    </div>

    <section class="notice-panel">
      <AlertTriangle :size="18" />
      <div>
        <h3>Connect backend to populate live counts</h3>
        <p>Module pages call existing admin endpoints. Overview aggregate endpoints are not available yet, so counts stay neutral until a backend summary endpoint is added.</p>
      </div>
    </section>
  </section>
</template>
