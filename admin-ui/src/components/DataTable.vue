<script setup lang="ts">
defineProps<{
  columns: string[]
  rows: Record<string, unknown>[]
  emptyText?: string
}>()
</script>

<template>
  <div class="table-wrap">
    <table v-if="rows.length" class="data-table">
      <thead>
        <tr>
          <th v-for="column in columns" :key="column">{{ column }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, rowIndex) in rows" :key="rowIndex">
          <td v-for="column in columns" :key="column">
            <span :class="{ mono: column.toLowerCase().includes('id') }">{{ row[column] ?? '-' }}</span>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-else class="empty-inline">{{ emptyText ?? 'Nothing to review yet' }}</div>
  </div>
</template>
