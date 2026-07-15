<script setup lang="ts">
export type DataTableColumn = {
  key: string
  label: string
  mono?: boolean
}

const props = defineProps<{
  columns: DataTableColumn[]
  rows: Record<string, unknown>[]
  rowKey?: string
  emptyText?: string
}>()

function keyFor(row: Record<string, unknown>, index: number): string {
  if (props.rowKey) {
    const value = row[props.rowKey]
    if (value !== undefined && value !== null) {
      return String(value)
    }
  }
  return String(index)
}

function display(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}
</script>

<template>
  <div class="table-wrap">
    <table v-if="rows.length" class="data-table">
      <thead>
        <tr>
          <th v-for="column in columns" :key="column.key">{{ column.label }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in rows" :key="keyFor(row, index)">
          <td v-for="column in columns" :key="column.key">
            <slot :name="`cell-${column.key}`" :row="row" :value="row[column.key]">
              <span :class="{ mono: column.mono }">{{ display(row[column.key]) }}</span>
            </slot>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-else class="empty-inline">{{ emptyText ?? 'Nothing to review yet' }}</div>
  </div>
</template>
