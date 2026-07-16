<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import DataTable, { type DataTableColumn } from '../components/DataTable.vue'
import EmptyState from '../components/EmptyState.vue'
import GapNotice from '../components/GapNotice.vue'
import Modal from '../components/Modal.vue'
import StatusBadge from '../components/StatusBadge.vue'
import Toolbar from '../components/Toolbar.vue'
import {
  knownGaps,
  tablesApi,
  type DiningAreaResponse,
  type DiningTableResponse,
  type ReservationStatus,
  type TableOccupancyResponse,
  type TableOccupancyState,
  type TableReservationResponse,
  type TableStatus,
} from '../api/modules'
import { formatDateTime, messageOf, truncateId } from '../lib/format'
import { isAdmin } from '../stores/auth'

const loading = ref(true)
const error = ref('')
const occupancy = ref<TableOccupancyResponse[]>([])
const areas = ref<DiningAreaResponse[]>([])
const tables = ref<DiningTableResponse[]>([])
const reservationsCreated = ref<TableReservationResponse[]>([])
const actionError = ref('')

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [occupancyResult, areasResult, tablesResult] = await Promise.all([
      tablesApi.listOccupancy(),
      tablesApi.listAreas(),
      tablesApi.listTables(),
    ])
    occupancy.value = occupancyResult
    areas.value = areasResult
    tables.value = tablesResult
  } catch (caught) {
    error.value = messageOf(caught, 'We could not load this data.')
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)

const gaps = knownGaps.tables

const occupancyColumns: DataTableColumn[] = [
  { key: 'tableCode', label: 'Table', mono: true },
  { key: 'areaName', label: 'Area' },
  { key: 'capacity', label: 'Capacity' },
  { key: 'state', label: 'Occupancy' },
  { key: 'session', label: 'Session' },
  { key: 'reservation', label: 'Reservation' },
  { key: 'actions', label: '' },
]

// -- Session/occupancy action modal --------------------------------------

type SessionMode = 'open' | 'close' | 'cancel' | 'occupancy'

const sessionModal = reactive({
  open: false,
  mode: 'open' as SessionMode,
  tableId: '',
  sessionId: '',
  tableLabel: '',
  partySize: undefined as number | undefined,
  note: '',
  nextState: 'AVAILABLE' as TableOccupancyState,
  reason: '',
  saving: false,
  error: '',
})

function openSessionModal(mode: SessionMode, row: TableOccupancyResponse) {
  sessionModal.mode = mode
  sessionModal.tableId = row.tableId
  sessionModal.sessionId = row.activeSession?.sessionId ?? ''
  sessionModal.tableLabel = `${row.tableCode} — ${row.tableName}`
  sessionModal.partySize = undefined
  sessionModal.note = ''
  sessionModal.nextState = 'AVAILABLE'
  sessionModal.reason = ''
  sessionModal.error = ''
  sessionModal.open = true
}

async function submitSessionModal() {
  sessionModal.saving = true
  sessionModal.error = ''
  try {
    if (sessionModal.mode === 'open') {
      await tablesApi.openSession(sessionModal.tableId, {
        partySize: sessionModal.partySize,
        note: sessionModal.note || undefined,
      })
    } else if (sessionModal.mode === 'close') {
      await tablesApi.closeSession(sessionModal.sessionId, {
        nextState: sessionModal.nextState,
        note: sessionModal.note || undefined,
      })
    } else if (sessionModal.mode === 'cancel') {
      await tablesApi.cancelSession(sessionModal.sessionId, { reason: sessionModal.reason || undefined })
    } else {
      await tablesApi.setOccupancy(sessionModal.tableId, {
        state: sessionModal.nextState,
        reason: sessionModal.reason || undefined,
      })
    }
    sessionModal.open = false
    await loadAll()
  } catch (caught) {
    sessionModal.error = messageOf(caught, 'We could not complete this action.')
  } finally {
    sessionModal.saving = false
  }
}

const sessionModalTitle = computed(() => {
  switch (sessionModal.mode) {
    case 'open':
      return 'Open table session'
    case 'close':
      return 'Close table session'
    case 'cancel':
      return 'Cancel table session'
    default:
      return 'Update occupancy'
  }
})

// -- Area management -------------------------------------------------------

const areaModalOpen = ref(false)
const areaForm = reactive({ name: '', sortOrder: 0, status: 'ACTIVE' as TableStatus })
const areaSaving = ref(false)
const areaFormError = ref('')
const editingAreaId = ref<string | null>(null)

const areaModalTitle = computed(() => (editingAreaId.value ? 'Edit dining area' : 'New area'))

function openAreaModal(row?: DiningAreaResponse) {
  if (row) {
    editingAreaId.value = row.id
    areaForm.name = row.name
    areaForm.sortOrder = row.sortOrder
    areaForm.status = row.status
  } else {
    editingAreaId.value = null
    areaForm.name = ''
    areaForm.sortOrder = 0
    areaForm.status = 'ACTIVE'
  }
  areaFormError.value = ''
  areaModalOpen.value = true
}

async function submitArea() {
  areaSaving.value = true
  areaFormError.value = ''
  try {
    const payload = { name: areaForm.name, sortOrder: areaForm.sortOrder, status: areaForm.status }
    if (editingAreaId.value) {
      await tablesApi.updateArea(editingAreaId.value, payload)
    } else {
      await tablesApi.createArea(payload)
    }
    editingAreaId.value = null
    areaModalOpen.value = false
    await loadAll()
  } catch (caught) {
    areaFormError.value = messageOf(caught, 'We could not save this area.')
  } finally {
    areaSaving.value = false
  }
}

async function archiveArea(id: string) {
  actionError.value = ''
  try {
    await tablesApi.archiveArea(id)
    await loadAll()
  } catch (caught) {
    actionError.value = messageOf(caught, 'We could not archive this area.')
  }
}

// -- Table management --------------------------------------------------

const tableModalOpen = ref(false)
const tableForm = reactive({
  areaId: '',
  code: '',
  name: '',
  capacity: 2,
  sortOrder: 0,
  status: 'ACTIVE' as TableStatus,
})
const tableSaving = ref(false)
const tableFormError = ref('')
const editingTableId = ref<string | null>(null)

const tableModalTitle = computed(() => (editingTableId.value ? 'Edit table' : 'New table'))

function openTableModal(row?: DiningTableResponse) {
  if (row) {
    editingTableId.value = row.id
    tableForm.areaId = row.areaId
    tableForm.code = row.code
    tableForm.name = row.name
    tableForm.capacity = row.capacity ?? 2
    tableForm.sortOrder = row.sortOrder
    tableForm.status = row.status
  } else {
    editingTableId.value = null
    tableForm.areaId = areas.value[0]?.id ?? ''
    tableForm.code = ''
    tableForm.name = ''
    tableForm.capacity = 2
    tableForm.sortOrder = 0
    tableForm.status = 'ACTIVE'
  }
  tableFormError.value = ''
  tableModalOpen.value = true
}

async function submitTable() {
  if (!tableForm.areaId) {
    tableFormError.value = 'Create a dining area first.'
    return
  }
  tableSaving.value = true
  tableFormError.value = ''
  try {
    const payload = {
      areaId: tableForm.areaId,
      code: tableForm.code,
      name: tableForm.name,
      capacity: tableForm.capacity,
      sortOrder: tableForm.sortOrder,
      status: tableForm.status,
    }
    if (editingTableId.value) {
      await tablesApi.updateTable(editingTableId.value, payload)
    } else {
      await tablesApi.createTable(payload)
    }
    editingTableId.value = null
    tableModalOpen.value = false
    await loadAll()
  } catch (caught) {
    tableFormError.value = messageOf(caught, 'We could not save this table.')
  } finally {
    tableSaving.value = false
  }
}

const archiveTableTarget = ref<{ id: string; label: string } | null>(null)
const archiveTablePending = ref(false)

async function confirmArchiveTable() {
  if (!archiveTableTarget.value) {
    return
  }
  archiveTablePending.value = true
  try {
    await tablesApi.archiveTable(archiveTableTarget.value.id)
    archiveTableTarget.value = null
    await loadAll()
  } catch (caught) {
    actionError.value = messageOf(caught, 'We could not archive this table.')
  } finally {
    archiveTablePending.value = false
  }
}

const tableColumns: DataTableColumn[] = [
  { key: 'code', label: 'Code', mono: true },
  { key: 'name', label: 'Name' },
  { key: 'areaName', label: 'Area' },
  { key: 'capacity', label: 'Capacity' },
  { key: 'status', label: 'Status' },
  { key: 'actions', label: '' },
]

// -- Reservations -------------------------------------------------------

const reservationModalOpen = ref(false)
const reservationForm = reactive({
  tableId: '',
  customerName: '',
  customerPhone: '',
  customerEmail: '',
  partySize: 2,
  startTime: '',
  endTime: '',
  note: '',
})
const reservationSaving = ref(false)
const reservationFormError = ref('')

function openReservationModal() {
  reservationForm.tableId = tables.value[0]?.id ?? ''
  reservationForm.customerName = ''
  reservationForm.customerPhone = ''
  reservationForm.customerEmail = ''
  reservationForm.partySize = 2
  reservationForm.startTime = ''
  reservationForm.endTime = ''
  reservationForm.note = ''
  reservationFormError.value = ''
  reservationModalOpen.value = true
}

async function submitReservation() {
  if (!reservationForm.tableId || !reservationForm.startTime || !reservationForm.endTime) {
    reservationFormError.value = 'Table, start time, and end time are required.'
    return
  }
  reservationSaving.value = true
  reservationFormError.value = ''
  try {
    const created = await tablesApi.createReservation({
      tableId: reservationForm.tableId,
      customerName: reservationForm.customerName,
      customerPhone: reservationForm.customerPhone || undefined,
      customerEmail: reservationForm.customerEmail || undefined,
      partySize: reservationForm.partySize,
      startTime: new Date(reservationForm.startTime).toISOString(),
      endTime: new Date(reservationForm.endTime).toISOString(),
      note: reservationForm.note || undefined,
    })
    reservationsCreated.value = [created, ...reservationsCreated.value]
    reservationModalOpen.value = false
  } catch (caught) {
    reservationFormError.value = messageOf(caught, 'We could not create this reservation.')
  } finally {
    reservationSaving.value = false
  }
}

async function seatReservation(reservationId: string) {
  actionError.value = ''
  try {
    await tablesApi.seatReservation(reservationId)
    reservationsCreated.value = reservationsCreated.value.map((reservation) =>
      reservation.reservationId === reservationId ? { ...reservation, status: 'SEATED' as ReservationStatus } : reservation,
    )
    await loadAll()
  } catch (caught) {
    actionError.value = messageOf(caught, 'We could not seat this reservation.')
  }
}

async function cancelReservation(reservationId: string) {
  actionError.value = ''
  try {
    const updated = await tablesApi.updateReservationStatus(reservationId, { status: 'CANCELLED' })
    reservationsCreated.value = reservationsCreated.value.map((reservation) =>
      reservation.reservationId === reservationId ? updated : reservation,
    )
  } catch (caught) {
    actionError.value = messageOf(caught, 'We could not cancel this reservation.')
  }
}

const reservationColumns: DataTableColumn[] = [
  { key: 'reservationId', label: 'Reservation', mono: true },
  { key: 'tableCode', label: 'Table', mono: true },
  { key: 'customerName', label: 'Customer' },
  { key: 'partySize', label: 'Party' },
  { key: 'startTime', label: 'Start' },
  { key: 'status', label: 'Status' },
  { key: 'actions', label: '' },
]
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Admin module</p>
        <h2>Tables</h2>
        <p>Dining areas, tables, occupancy, sessions, reservations, and availability.</p>
      </div>
    </div>

    <GapNotice v-for="gap in gaps" :key="gap.label" :label="gap.label" :detail="gap.detail" />

    <p v-if="actionError" class="form-error">{{ actionError }}</p>

    <section class="panel">
      <div class="panel-header">
        <h3>Dining areas</h3>
        <button v-if="isAdmin" class="ghost-button" type="button" @click="openAreaModal()">New area</button>
      </div>
      <div class="tag-list">
        <span v-for="area in areas" :key="area.id" class="tag-chip">
          <button
            v-if="isAdmin"
            type="button"
            :aria-label="`Edit ${area.name}`"
            @click="openAreaModal(area)"
          >
            {{ area.name }}
          </button>
          <template v-else>{{ area.name }}</template>
          <button v-if="isAdmin" type="button" :aria-label="`Archive ${area.name}`" @click="archiveArea(area.id)">×</button>
        </span>
        <span v-if="!areas.length && !loading" class="field-hint">No dining areas yet.</span>
      </div>
    </section>

    <Toolbar>
      <template #actions>
        <button v-if="isAdmin" class="ghost-button" type="button" @click="openTableModal()">New table</button>
        <button class="primary-button" type="button" @click="openReservationModal">New reservation</button>
      </template>
    </Toolbar>

    <section class="panel">
      <div class="panel-header">
        <h3>Occupancy</h3>
        <StatusBadge :tone="loading ? 'muted' : error ? 'danger' : 'success'">
          {{ loading ? 'Loading' : error ? 'Needs backend' : 'Ready' }}
        </StatusBadge>
      </div>
      <EmptyState v-if="error" tone="error" :body="error" @retry="loadAll" />
      <div v-else-if="loading" class="skeleton-table" />
      <DataTable
        v-else
        :columns="occupancyColumns"
        :rows="occupancy"
        row-key="tableId"
        empty-text="No tables configured yet."
      >
        <template #cell-state="{ value }">
          <StatusBadge :status="value as string" />
        </template>
        <template #cell-session="{ row }">
          <StatusBadge v-if="row.activeSession" :status="(row.activeSession as any).status" />
          <span v-else class="field-hint">None</span>
        </template>
        <template #cell-reservation="{ row }">
          <StatusBadge v-if="row.activeReservation" :status="(row.activeReservation as any).status" />
          <span v-else class="field-hint">None</span>
        </template>
        <template #cell-actions="{ row }">
          <div class="table-actions">
            <button
              v-if="!row.activeSession"
              class="ghost-button small"
              type="button"
              @click="openSessionModal('open', row as any)"
            >
              Open session
            </button>
            <template v-else>
              <button class="ghost-button small" type="button" @click="openSessionModal('close', row as any)">
                Close
              </button>
              <button class="ghost-button small" type="button" @click="openSessionModal('cancel', row as any)">
                Cancel
              </button>
            </template>
            <button class="ghost-button small" type="button" @click="openSessionModal('occupancy', row as any)">
              Set state
            </button>
          </div>
        </template>
      </DataTable>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h3>Dining tables</h3>
      </div>
      <DataTable :columns="tableColumns" :rows="tables" row-key="id" empty-text="No tables configured yet.">
        <template #cell-status="{ value }">
          <StatusBadge :status="value as string" />
        </template>
        <template #cell-actions="{ row }">
          <div v-if="isAdmin" class="table-actions">
            <button class="ghost-button small" type="button" @click="openTableModal(row as DiningTableResponse)">
              Edit
            </button>
            <button
              class="ghost-button small"
              type="button"
              @click="archiveTableTarget = { id: row.id as string, label: `${row.code} — ${row.name}` }"
            >
              Archive
            </button>
          </div>
        </template>
      </DataTable>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h3>Reservations (this session)</h3>
      </div>
      <DataTable
        :columns="reservationColumns"
        :rows="reservationsCreated"
        row-key="reservationId"
        empty-text="No reservations created yet in this session."
      >
        <template #cell-reservationId="{ value }">{{ truncateId(value as string) }}</template>
        <template #cell-startTime="{ value }">{{ formatDateTime(value as string) }}</template>
        <template #cell-status="{ value }">
          <StatusBadge :status="value as string" />
        </template>
        <template #cell-actions="{ row }">
          <div class="table-actions">
            <button
              v-if="['PENDING', 'CONFIRMED'].includes(row.status as string)"
              class="ghost-button small"
              type="button"
              @click="seatReservation(row.reservationId as string)"
            >
              Seat
            </button>
            <button
              v-if="!['CANCELLED', 'SEATED', 'COMPLETED', 'NO_SHOW'].includes(row.status as string)"
              class="ghost-button small"
              type="button"
              @click="cancelReservation(row.reservationId as string)"
            >
              Cancel
            </button>
          </div>
        </template>
      </DataTable>
    </section>

    <Modal :open="sessionModal.open" :title="sessionModalTitle" @close="sessionModal.open = false">
      <form class="field-grid" @submit.prevent="submitSessionModal">
        <p class="span-2 field-hint">{{ sessionModal.tableLabel }}</p>
        <label v-if="sessionModal.mode === 'open'">
          Party size
          <input v-model.number="sessionModal.partySize" type="number" min="1" />
        </label>
        <label v-if="sessionModal.mode === 'open' || sessionModal.mode === 'close'" class="span-2">
          Note
          <input v-model="sessionModal.note" />
        </label>
        <label v-if="sessionModal.mode === 'close' || sessionModal.mode === 'occupancy'">
          {{ sessionModal.mode === 'close' ? 'Next state' : 'State' }}
          <select v-model="sessionModal.nextState">
            <option value="AVAILABLE">AVAILABLE</option>
            <option value="OCCUPIED">OCCUPIED</option>
            <option value="RESERVED">RESERVED</option>
            <option value="CLEANING">CLEANING</option>
            <option value="OUT_OF_SERVICE">OUT_OF_SERVICE</option>
          </select>
        </label>
        <label v-if="sessionModal.mode === 'cancel' || sessionModal.mode === 'occupancy'" class="span-2">
          Reason
          <input v-model="sessionModal.reason" />
        </label>
        <p v-if="sessionModal.error" class="form-error span-2">{{ sessionModal.error }}</p>
        <div class="modal-footer span-2">
          <button class="ghost-button" type="button" @click="sessionModal.open = false">Cancel</button>
          <button class="primary-button" type="submit" :disabled="sessionModal.saving">
            {{ sessionModal.saving ? 'Saving…' : 'Save changes' }}
          </button>
        </div>
      </form>
    </Modal>

    <Modal :open="areaModalOpen" :title="areaModalTitle" @close="areaModalOpen = false">
      <form class="field-grid" @submit.prevent="submitArea">
        <label class="span-2">
          Name
          <input v-model="areaForm.name" required />
        </label>
        <label>
          Sort order
          <input v-model.number="areaForm.sortOrder" type="number" />
        </label>
        <label>
          Status
          <select v-model="areaForm.status">
            <option value="ACTIVE">ACTIVE</option>
            <option value="INACTIVE">INACTIVE</option>
            <option value="ARCHIVED">ARCHIVED</option>
          </select>
        </label>
        <p v-if="areaFormError" class="form-error span-2">{{ areaFormError }}</p>
        <div class="modal-footer span-2">
          <button class="ghost-button" type="button" @click="areaModalOpen = false">Cancel</button>
          <button class="primary-button" type="submit" :disabled="areaSaving">
            {{ areaSaving ? 'Saving…' : 'Save changes' }}
          </button>
        </div>
      </form>
    </Modal>

    <Modal :open="tableModalOpen" :title="tableModalTitle" @close="tableModalOpen = false">
      <form class="field-grid" @submit.prevent="submitTable">
        <label class="span-2">
          Area
          <select v-model="tableForm.areaId" required>
            <option v-for="area in areas" :key="area.id" :value="area.id">{{ area.name }}</option>
          </select>
        </label>
        <label>
          Code
          <input v-model="tableForm.code" required />
        </label>
        <label>
          Name
          <input v-model="tableForm.name" required />
        </label>
        <label>
          Capacity
          <input v-model.number="tableForm.capacity" type="number" min="1" />
        </label>
        <label>
          Sort order
          <input v-model.number="tableForm.sortOrder" type="number" />
        </label>
        <label class="span-2">
          Status
          <select v-model="tableForm.status">
            <option value="ACTIVE">ACTIVE</option>
            <option value="INACTIVE">INACTIVE</option>
            <option value="ARCHIVED">ARCHIVED</option>
          </select>
        </label>
        <p v-if="tableFormError" class="form-error span-2">{{ tableFormError }}</p>
        <div class="modal-footer span-2">
          <button class="ghost-button" type="button" @click="tableModalOpen = false">Cancel</button>
          <button class="primary-button" type="submit" :disabled="tableSaving">
            {{ tableSaving ? 'Saving…' : 'Save changes' }}
          </button>
        </div>
      </form>
    </Modal>

    <Modal :open="reservationModalOpen" title="New reservation" @close="reservationModalOpen = false">
      <form class="field-grid" @submit.prevent="submitReservation">
        <label class="span-2">
          Table
          <select v-model="reservationForm.tableId" required>
            <option v-for="table in tables" :key="table.id" :value="table.id">{{ table.code }} — {{ table.name }}</option>
          </select>
        </label>
        <label class="span-2">
          Customer name
          <input v-model="reservationForm.customerName" required />
        </label>
        <label>
          Phone
          <input v-model="reservationForm.customerPhone" />
        </label>
        <label>
          Email
          <input v-model="reservationForm.customerEmail" type="email" />
        </label>
        <label>
          Party size
          <input v-model.number="reservationForm.partySize" type="number" min="1" required />
        </label>
        <label>
          Start time
          <input v-model="reservationForm.startTime" type="datetime-local" required />
        </label>
        <label class="span-2">
          End time
          <input v-model="reservationForm.endTime" type="datetime-local" required />
        </label>
        <label class="span-2">
          Note
          <input v-model="reservationForm.note" />
        </label>
        <p v-if="reservationFormError" class="form-error span-2">{{ reservationFormError }}</p>
        <div class="modal-footer span-2">
          <button class="ghost-button" type="button" @click="reservationModalOpen = false">Cancel</button>
          <button class="primary-button" type="submit" :disabled="reservationSaving">
            {{ reservationSaving ? 'Saving…' : 'Save changes' }}
          </button>
        </div>
      </form>
    </Modal>

    <ConfirmDialog
      :open="Boolean(archiveTableTarget)"
      title="Confirm action"
      :message="`Confirm action: archiving '${archiveTableTarget?.label}' may affect active restaurant operations.`"
      confirm-label="Archive"
      danger
      :pending="archiveTablePending"
      @close="archiveTableTarget = null"
      @confirm="confirmArchiveTable"
    />
  </section>
</template>
