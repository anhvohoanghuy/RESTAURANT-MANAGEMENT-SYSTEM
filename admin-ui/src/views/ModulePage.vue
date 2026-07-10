<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getModuleSample, moduleEndpoints, type ModuleEndpoint } from '../api/modules'
import DataTable from '../components/DataTable.vue'
import EmptyState from '../components/EmptyState.vue'
import StatusBadge from '../components/StatusBadge.vue'

const props = defineProps<{
  moduleKey: string
  title: string
  subtitle: string
}>()

const loading = ref(false)
const error = ref('')
const sample = ref<Record<string, unknown>[]>([])

const endpoints = computed<ModuleEndpoint[]>(() => moduleEndpoints[props.moduleKey] ?? [])
const columns = computed(() => (sample.value[0] ? Object.keys(sample.value[0]).slice(0, 6) : []))

onMounted(async () => {
  loading.value = true
  try {
    const value = await getModuleSample(props.moduleKey)
    sample.value = Array.isArray(value) ? normalizeRows(value) : normalizeRows([value as Record<string, unknown>])
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'We could not load this data.'
  } finally {
    loading.value = false
  }
})

function normalizeRows(rows: unknown[]): Record<string, unknown>[] {
  return rows.filter((row): row is Record<string, unknown> => Boolean(row) && typeof row === 'object')
}
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Admin module</p>
        <h2>{{ title }}</h2>
        <p>{{ subtitle }}</p>
      </div>
      <button class="primary-button" type="button">New item</button>
    </div>

    <div class="endpoint-grid">
      <article v-for="endpoint in endpoints" :key="`${endpoint.method}-${endpoint.path}-${endpoint.label}`" class="endpoint-card">
        <div>
          <StatusBadge :tone="endpoint.status === 'gap' ? 'warning' : 'info'">{{ endpoint.method }}</StatusBadge>
          <h3>{{ endpoint.label }}</h3>
        </div>
        <code>{{ endpoint.path }}</code>
        <p v-if="endpoint.note">{{ endpoint.note }}</p>
      </article>
    </div>

    <section class="panel">
      <div class="panel-header">
        <h3>Sample read</h3>
        <StatusBadge :tone="loading ? 'muted' : error ? 'danger' : 'success'">
          {{ loading ? 'Loading' : error ? 'Needs backend' : 'Ready' }}
        </StatusBadge>
      </div>
      <p v-if="error" class="form-error">{{ error }}</p>
      <div v-else-if="loading" class="skeleton-table" />
      <DataTable v-else-if="columns.length" :columns="columns" :rows="sample" />
      <EmptyState v-else />
    </section>
  </section>
</template>
