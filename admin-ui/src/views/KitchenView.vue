<script setup lang="ts">
import { onMounted, ref } from 'vue'
import DataTable, { type DataTableColumn } from '../components/DataTable.vue'
import EmptyState from '../components/EmptyState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import Toolbar from '../components/Toolbar.vue'
import { kitchenApi, kitchenForwardStatus, type KitchenBoardItem } from '../api/modules'
import { messageOf, truncateId } from '../lib/format'

const loading = ref(true)
const error = ref('')
const board = ref<KitchenBoardItem[]>([])
const advancingId = ref('')
const actionError = ref('')

async function loadBoard() {
  loading.value = true
  error.value = ''
  try {
    board.value = await kitchenApi.getBoard()
  } catch (caught) {
    error.value = messageOf(caught, 'We could not load this data.')
  } finally {
    loading.value = false
  }
}

onMounted(loadBoard)

const columns: DataTableColumn[] = [
  { key: 'orderId', label: 'Order', mono: true },
  { key: 'dishName', label: 'Dish' },
  { key: 'quantity', label: 'Qty' },
  { key: 'status', label: 'Status' },
  { key: 'actions', label: '' },
]

function nextStatusFor(item: KitchenBoardItem) {
  return kitchenForwardStatus[item.status]
}

async function advance(item: KitchenBoardItem) {
  const target = nextStatusFor(item)
  if (!target) {
    return
  }
  advancingId.value = item.itemId
  actionError.value = ''
  try {
    await kitchenApi.advanceItem(item.orderId, item.itemId, target)
    await loadBoard()
  } catch (caught) {
    actionError.value = messageOf(caught, 'We could not advance this item.')
  } finally {
    advancingId.value = ''
  }
}
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Admin module</p>
        <h2>Kitchen</h2>
        <p>Kitchen board and fulfillment status advancement.</p>
      </div>
    </div>

    <p v-if="actionError" class="form-error">{{ actionError }}</p>

    <Toolbar>
      <template #actions>
        <button class="ghost-button" type="button" @click="loadBoard">Refresh board</button>
      </template>
    </Toolbar>

    <section class="panel">
      <div class="panel-header">
        <h3>Active tickets</h3>
        <StatusBadge :tone="loading ? 'muted' : error ? 'danger' : 'success'">
          {{ loading ? 'Loading' : error ? 'Needs backend' : 'Ready' }}
        </StatusBadge>
      </div>
      <EmptyState v-if="error" tone="error" :body="error" @retry="loadBoard" />
      <div v-else-if="loading" class="skeleton-table" />
      <DataTable v-else :columns="columns" :rows="board" row-key="itemId" empty-text="No active kitchen items right now.">
        <template #cell-orderId="{ value }">{{ truncateId(value as string) }}</template>
        <template #cell-status="{ value }">
          <StatusBadge :status="value as string" />
        </template>
        <template #cell-actions="{ row }">
          <button
            v-if="nextStatusFor(row as unknown as KitchenBoardItem)"
            class="primary-button small"
            type="button"
            :disabled="advancingId === row.itemId"
            @click="advance(row as unknown as KitchenBoardItem)"
          >
            {{ advancingId === row.itemId ? 'Advancing…' : `Advance to ${nextStatusFor(row as unknown as KitchenBoardItem)}` }}
          </button>
          <span v-else class="field-hint">Terminal</span>
        </template>
      </DataTable>
    </section>
  </section>
</template>
