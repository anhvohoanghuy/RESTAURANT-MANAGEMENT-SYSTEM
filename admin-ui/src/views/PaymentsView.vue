<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import DataTable, { type DataTableColumn } from '../components/DataTable.vue'
import EmptyState from '../components/EmptyState.vue'
import GapNotice from '../components/GapNotice.vue'
import Modal from '../components/Modal.vue'
import StatusBadge from '../components/StatusBadge.vue'
import Toolbar from '../components/Toolbar.vue'
import { knownGaps, paymentsApi, type PaymentMethod, type PaymentResponse } from '../api/modules'
import { formatDateTime, formatMoney, messageOf, newIdempotencyKey, truncateId } from '../lib/format'

const loading = ref(true)
const error = ref('')
const items = ref<PaymentResponse[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const loadingMore = ref(false)

const filters = reactive({ orderId: '', orderUserId: '' })

const gaps = knownGaps.payments

async function loadPayments(reset = true) {
  if (reset) {
    loading.value = true
    error.value = ''
    items.value = []
    nextCursor.value = null
  } else {
    loadingMore.value = true
  }
  try {
    const response = await paymentsApi.listPayments({
      orderId: filters.orderId || undefined,
      orderUserId: filters.orderUserId || undefined,
      cursor: reset ? undefined : (nextCursor.value ?? undefined),
    })
    items.value = reset ? response.items : [...items.value, ...response.items]
    nextCursor.value = response.nextCursor
    hasMore.value = response.hasMore
  } catch (caught) {
    error.value = messageOf(caught, 'We could not load this data.')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

onMounted(() => loadPayments())

function applyFilters() {
  loadPayments(true)
}

function clearFilters() {
  filters.orderId = ''
  filters.orderUserId = ''
  loadPayments(true)
}

const paymentColumns: DataTableColumn[] = [
  { key: 'paymentId', label: 'Payment', mono: true },
  { key: 'orderId', label: 'Order', mono: true },
  { key: 'amount', label: 'Amount' },
  { key: 'method', label: 'Method' },
  { key: 'status', label: 'Status' },
  { key: 'refunded', label: 'Refunded' },
  { key: 'createdAt', label: 'When' },
  { key: 'actions', label: '' },
]

function refundedTotal(payment: PaymentResponse): number {
  return payment.refunds.reduce((sum, refund) => sum + refund.amount, 0)
}

// -- Record payment -------------------------------------------------------

const paymentModalOpen = ref(false)
const paymentForm = reactive({
  orderId: '',
  amount: 0,
  method: 'CASH' as PaymentMethod,
  reference: '',
  note: '',
})
const paymentSaving = ref(false)
const paymentFormError = ref('')

function openPaymentModal() {
  paymentForm.orderId = filters.orderId
  paymentForm.amount = 0
  paymentForm.method = 'CASH'
  paymentForm.reference = ''
  paymentForm.note = ''
  paymentFormError.value = ''
  paymentModalOpen.value = true
}

async function submitPayment() {
  if (!paymentForm.orderId) {
    paymentFormError.value = 'Order ID is required.'
    return
  }
  paymentSaving.value = true
  paymentFormError.value = ''
  try {
    await paymentsApi.recordPayment(paymentForm.orderId, {
      amount: paymentForm.amount,
      method: paymentForm.method,
      idempotencyKey: newIdempotencyKey(),
      reference: paymentForm.reference || undefined,
      note: paymentForm.note || undefined,
    })
    paymentModalOpen.value = false
    await loadPayments(true)
  } catch (caught) {
    paymentFormError.value = messageOf(caught, 'We could not record this payment.')
  } finally {
    paymentSaving.value = false
  }
}

// -- Record refund -------------------------------------------------------

const refundModalOpen = ref(false)
const refundTargetLabel = ref('')
const refundForm = reactive({ paymentId: '', amount: 0, reason: '' })
const refundSaving = ref(false)
const refundFormError = ref('')

function openRefundModal(payment: PaymentResponse) {
  refundForm.paymentId = payment.paymentId
  refundForm.amount = payment.amount - refundedTotal(payment)
  refundForm.reason = ''
  refundTargetLabel.value = truncateId(payment.paymentId)
  refundFormError.value = ''
  refundModalOpen.value = true
}

async function submitRefund() {
  refundSaving.value = true
  refundFormError.value = ''
  try {
    await paymentsApi.recordRefund(refundForm.paymentId, {
      amount: refundForm.amount,
      idempotencyKey: newIdempotencyKey(),
      reason: refundForm.reason || undefined,
    })
    refundModalOpen.value = false
    await loadPayments(true)
  } catch (caught) {
    refundFormError.value = messageOf(caught, 'We could not record this refund.')
  } finally {
    refundSaving.value = false
  }
}
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Admin module</p>
        <h2>Payments</h2>
        <p>Payment history, manual records, refunds, and known filter gaps.</p>
      </div>
    </div>

    <GapNotice v-for="gap in gaps" :key="gap.label" :label="gap.label" :detail="gap.detail" />

    <Toolbar>
      <template #filters>
        <label>
          Order ID
          <input v-model="filters.orderId" placeholder="Filter by order ID" />
        </label>
        <label>
          Order user ID
          <input v-model="filters.orderUserId" placeholder="Filter by customer ID" />
        </label>
        <label>
          Status
          <select disabled>
            <option>All (gap)</option>
          </select>
        </label>
        <label>
          Method
          <select disabled>
            <option>All (gap)</option>
          </select>
        </label>
        <label>
          Date range
          <input type="date" disabled />
        </label>
        <div class="toolbar-actions">
          <button class="ghost-button small" type="button" @click="applyFilters">Apply</button>
          <button class="ghost-button small" type="button" @click="clearFilters">Clear</button>
        </div>
      </template>
      <template #actions>
        <button class="primary-button" type="button" @click="openPaymentModal">Record payment</button>
      </template>
    </Toolbar>

    <section class="panel">
      <div class="panel-header">
        <h3>Payment history</h3>
        <StatusBadge :tone="loading ? 'muted' : error ? 'danger' : 'success'">
          {{ loading ? 'Loading' : error ? 'Needs backend' : 'Ready' }}
        </StatusBadge>
      </div>
      <EmptyState v-if="error" tone="error" :body="error" @retry="() => loadPayments(true)" />
      <div v-else-if="loading" class="skeleton-table" />
      <template v-else>
        <DataTable :columns="paymentColumns" :rows="items" row-key="paymentId" empty-text="No payments recorded yet.">
          <template #cell-paymentId="{ value }">{{ truncateId(value as string) }}</template>
          <template #cell-orderId="{ value }">{{ truncateId(value as string) }}</template>
          <template #cell-amount="{ value }">{{ formatMoney(value as number) }}</template>
          <template #cell-status="{ value }">
            <StatusBadge :status="value as string" />
          </template>
          <template #cell-refunded="{ row }">{{ formatMoney(refundedTotal(row as unknown as PaymentResponse)) }}</template>
          <template #cell-createdAt="{ value }">{{ formatDateTime(value as string) }}</template>
          <template #cell-actions="{ row }">
            <button class="ghost-button small" type="button" @click="openRefundModal(row as unknown as PaymentResponse)">
              Record refund
            </button>
          </template>
        </DataTable>
        <div v-if="hasMore" class="toolbar-actions">
          <button class="ghost-button" type="button" :disabled="loadingMore" @click="loadPayments(false)">
            {{ loadingMore ? 'Loading…' : 'Load more' }}
          </button>
        </div>
      </template>
    </section>

    <Modal :open="paymentModalOpen" title="Record payment" @close="paymentModalOpen = false">
      <form class="field-grid" @submit.prevent="submitPayment">
        <label class="span-2">
          Order ID
          <input v-model="paymentForm.orderId" required />
        </label>
        <label>
          Amount
          <input v-model.number="paymentForm.amount" type="number" step="0.01" min="0" required />
        </label>
        <label>
          Method
          <select v-model="paymentForm.method">
            <option value="CASH">CASH</option>
            <option value="BANK_TRANSFER">BANK_TRANSFER</option>
            <option value="QR_CODE">QR_CODE</option>
          </select>
        </label>
        <label class="span-2">
          Reference
          <input v-model="paymentForm.reference" placeholder="Optional" />
        </label>
        <label class="span-2">
          Note
          <input v-model="paymentForm.note" placeholder="Optional" />
        </label>
        <p v-if="paymentFormError" class="form-error span-2">{{ paymentFormError }}</p>
        <div class="modal-footer span-2">
          <button class="ghost-button" type="button" @click="paymentModalOpen = false">Cancel</button>
          <button class="primary-button" type="submit" :disabled="paymentSaving">
            {{ paymentSaving ? 'Saving…' : 'Save changes' }}
          </button>
        </div>
      </form>
    </Modal>

    <Modal :open="refundModalOpen" :title="`Record refund — ${refundTargetLabel}`" @close="refundModalOpen = false">
      <form class="field-grid" @submit.prevent="submitRefund">
        <label>
          Amount
          <input v-model.number="refundForm.amount" type="number" step="0.01" min="0" required />
        </label>
        <label>
          Reason
          <input v-model="refundForm.reason" placeholder="Optional" />
        </label>
        <p v-if="refundFormError" class="form-error span-2">{{ refundFormError }}</p>
        <div class="modal-footer span-2">
          <button class="ghost-button" type="button" @click="refundModalOpen = false">Cancel</button>
          <button class="primary-button danger-button" type="submit" :disabled="refundSaving">
            {{ refundSaving ? 'Working…' : 'Record refund' }}
          </button>
        </div>
      </form>
    </Modal>
  </section>
</template>
