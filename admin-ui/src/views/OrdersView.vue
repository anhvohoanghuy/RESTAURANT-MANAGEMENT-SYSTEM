<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import DataTable, { type DataTableColumn } from '../components/DataTable.vue'
import GapNotice from '../components/GapNotice.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { kitchenApi, knownGaps, ordersApi, type OrderCancellationResponse } from '../api/modules'
import { formatMoney, messageOf, truncateId } from '../lib/format'

const gaps = knownGaps.orders

const knownOrderIds = ref<string[]>([])

async function loadKnownOrderIds() {
  try {
    const board = await kitchenApi.getBoard()
    knownOrderIds.value = [...new Set(board.map((item) => item.orderId))]
  } catch {
    knownOrderIds.value = []
  }
}

onMounted(loadKnownOrderIds)

const results = ref<Array<OrderCancellationResponse & { action: string }>>([])

// -- Cancel whole order ---------------------------------------------------

const orderIdInput = ref('')
const cancelOrderPending = ref(false)
const cancelOrderConfirmOpen = ref(false)
const cancelOrderError = ref('')

function requestCancelOrder() {
  if (!orderIdInput.value) {
    cancelOrderError.value = 'Enter an order ID first.'
    return
  }
  cancelOrderError.value = ''
  cancelOrderConfirmOpen.value = true
}

async function confirmCancelOrder() {
  cancelOrderPending.value = true
  cancelOrderError.value = ''
  try {
    const response = await ordersApi.cancelOrder(orderIdInput.value)
    results.value = [{ ...response, action: 'Cancel order' }, ...results.value]
    cancelOrderConfirmOpen.value = false
  } catch (caught) {
    cancelOrderError.value = messageOf(caught, 'We could not cancel this order.')
  } finally {
    cancelOrderPending.value = false
  }
}

// -- Cancel a single line -------------------------------------------------

const lineOrderIdInput = ref('')
const lineIdInput = ref('')
const cancelLinePending = ref(false)
const cancelLineConfirmOpen = ref(false)
const cancelLineError = ref('')

function requestCancelLine() {
  if (!lineOrderIdInput.value || !lineIdInput.value) {
    cancelLineError.value = 'Enter both order ID and line ID.'
    return
  }
  cancelLineError.value = ''
  cancelLineConfirmOpen.value = true
}

async function confirmCancelLine() {
  cancelLinePending.value = true
  cancelLineError.value = ''
  try {
    const response = await ordersApi.cancelOrderLine(lineOrderIdInput.value, lineIdInput.value)
    results.value = [{ ...response, action: 'Cancel line' }, ...results.value]
    cancelLineConfirmOpen.value = false
  } catch (caught) {
    cancelLineError.value = messageOf(caught, 'We could not cancel this line.')
  } finally {
    cancelLinePending.value = false
  }
}

function useOrderId(orderId: string) {
  orderIdInput.value = orderId
  lineOrderIdInput.value = orderId
}

const resultColumns: DataTableColumn[] = [
  { key: 'action', label: 'Action' },
  { key: 'orderId', label: 'Order', mono: true },
  { key: 'status', label: 'Status' },
  { key: 'total', label: 'Total' },
  { key: 'cancelledLineIds', label: 'Cancelled lines' },
]
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Admin module</p>
        <h2>Orders</h2>
        <p>Admin cancellation workflows and backend order-listing follow-up gap.</p>
      </div>
    </div>

    <GapNotice v-for="gap in gaps" :key="gap.label" :label="gap.label" :detail="gap.detail" />

    <section v-if="knownOrderIds.length" class="panel">
      <div class="panel-header">
        <h3>Order IDs seen on the kitchen board</h3>
      </div>
      <div class="tag-list">
        <button v-for="orderId in knownOrderIds" :key="orderId" type="button" class="tag-chip" @click="useOrderId(orderId)">
          {{ truncateId(orderId, 12) }}
        </button>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h3>Cancel order</h3>
      </div>
      <form class="field-grid" @submit.prevent="requestCancelOrder">
        <label class="span-2">
          Order ID
          <input v-model="orderIdInput" required />
        </label>
        <p v-if="cancelOrderError" class="form-error span-2">{{ cancelOrderError }}</p>
        <div class="modal-footer span-2">
          <button class="primary-button danger-button" type="submit">Cancel order</button>
        </div>
      </form>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h3>Cancel order line</h3>
      </div>
      <form class="field-grid" @submit.prevent="requestCancelLine">
        <label>
          Order ID
          <input v-model="lineOrderIdInput" required />
        </label>
        <label>
          Line ID
          <input v-model="lineIdInput" required />
        </label>
        <p v-if="cancelLineError" class="form-error span-2">{{ cancelLineError }}</p>
        <div class="modal-footer span-2">
          <button class="primary-button danger-button" type="submit">Cancel line</button>
        </div>
      </form>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h3>Recent cancellation results (this session)</h3>
      </div>
      <DataTable :columns="resultColumns" :rows="results" empty-text="No cancellation actions performed yet.">
        <template #cell-orderId="{ value }">{{ truncateId(value as string) }}</template>
        <template #cell-status="{ value }">
          <StatusBadge :status="value as string" />
        </template>
        <template #cell-total="{ value }">{{ formatMoney(value as number) }}</template>
        <template #cell-cancelledLineIds="{ value }">{{ (value as string[]).length }}</template>
      </DataTable>
    </section>

    <ConfirmDialog
      :open="cancelOrderConfirmOpen"
      title="Confirm action"
      :message="`Confirm action: cancelling order '${orderIdInput}' may affect active restaurant operations.`"
      confirm-label="Cancel order"
      danger
      :pending="cancelOrderPending"
      @close="cancelOrderConfirmOpen = false"
      @confirm="confirmCancelOrder"
    />

    <ConfirmDialog
      :open="cancelLineConfirmOpen"
      title="Confirm action"
      :message="`Confirm action: cancelling line '${lineIdInput}' on order '${lineOrderIdInput}' may affect active restaurant operations.`"
      confirm-label="Cancel line"
      danger
      :pending="cancelLinePending"
      @close="cancelLineConfirmOpen = false"
      @confirm="confirmCancelLine"
    />
  </section>
</template>
