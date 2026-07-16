<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import DataTable, { type DataTableColumn } from '../components/DataTable.vue'
import EmptyState from '../components/EmptyState.vue'
import Modal from '../components/Modal.vue'
import StatusBadge from '../components/StatusBadge.vue'
import Toolbar from '../components/Toolbar.vue'
import {
  inventoryApi,
  type IngredientStatus,
  type InventoryMovementType,
  type StockBalanceResponse,
  type StockMovementResponse,
} from '../api/modules'
import { formatDateTime, formatMoney, messageOf } from '../lib/format'

const loading = ref(true)
const error = ref('')
const stock = ref<StockBalanceResponse[]>([])
const lowStockOnly = ref(false)
const actionError = ref('')

const movementsLoading = ref(true)
const movementsError = ref('')
const movements = ref<StockMovementResponse[]>([])

async function loadStock() {
  loading.value = true
  error.value = ''
  try {
    stock.value = lowStockOnly.value ? await inventoryApi.listLowStock() : await inventoryApi.listStock()
  } catch (caught) {
    error.value = messageOf(caught, 'We could not load this data.')
  } finally {
    loading.value = false
  }
}

async function loadMovements() {
  movementsLoading.value = true
  movementsError.value = ''
  try {
    movements.value = await inventoryApi.listMovements(undefined, 20)
  } catch (caught) {
    movementsError.value = messageOf(caught, 'We could not load this data.')
  } finally {
    movementsLoading.value = false
  }
}

onMounted(() => {
  loadStock()
  loadMovements()
})

function toggleLowStock() {
  lowStockOnly.value = !lowStockOnly.value
  loadStock()
}

const stockColumns: DataTableColumn[] = [
  { key: 'ingredientName', label: 'Ingredient' },
  { key: 'quantityOnHand', label: 'On hand' },
  { key: 'baseUnit', label: 'Unit' },
  { key: 'lowStockThreshold', label: 'Threshold' },
  { key: 'lowStock', label: 'Low stock' },
  { key: 'ingredientStatus', label: 'Status' },
  { key: 'actions', label: '' },
]

const movementColumns: DataTableColumn[] = [
  { key: 'ingredientName', label: 'Ingredient' },
  { key: 'type', label: 'Type' },
  { key: 'quantity', label: 'Qty' },
  { key: 'resultingBalance', label: 'Balance after' },
  { key: 'note', label: 'Note' },
  { key: 'createdAt', label: 'When' },
]

// -- New ingredient -------------------------------------------------------

const ingredientModalOpen = ref(false)
const ingredientForm = reactive({ name: '', baseUnit: '', description: '', status: 'ACTIVE' as IngredientStatus })
const ingredientSaving = ref(false)
const ingredientFormError = ref('')
const editingIngredientId = ref<string | null>(null)

const ingredientModalTitle = computed(() => (editingIngredientId.value ? 'Edit ingredient' : 'New ingredient'))

function openIngredientModal(row?: StockBalanceResponse) {
  if (row) {
    editingIngredientId.value = row.ingredientId
    ingredientForm.name = row.ingredientName
    ingredientForm.baseUnit = row.baseUnit
    ingredientForm.description = ''
    ingredientForm.status = row.ingredientStatus
  } else {
    editingIngredientId.value = null
    ingredientForm.name = ''
    ingredientForm.baseUnit = ''
    ingredientForm.description = ''
    ingredientForm.status = 'ACTIVE'
  }
  ingredientFormError.value = ''
  ingredientModalOpen.value = true
}

async function submitIngredient() {
  ingredientSaving.value = true
  ingredientFormError.value = ''
  try {
    const payload = {
      name: ingredientForm.name,
      baseUnit: ingredientForm.baseUnit,
      description: ingredientForm.description || undefined,
      status: ingredientForm.status,
    }
    if (editingIngredientId.value) {
      await inventoryApi.updateIngredient(editingIngredientId.value, payload)
    } else {
      await inventoryApi.createIngredient(payload)
    }
    editingIngredientId.value = null
    ingredientModalOpen.value = false
    await loadStock()
  } catch (caught) {
    ingredientFormError.value = messageOf(caught, 'We could not save this ingredient.')
  } finally {
    ingredientSaving.value = false
  }
}

const archiveTarget = ref<{ id: string; name: string } | null>(null)
const archivePending = ref(false)

async function confirmArchiveIngredient() {
  if (!archiveTarget.value) {
    return
  }
  archivePending.value = true
  try {
    await inventoryApi.archiveIngredient(archiveTarget.value.id)
    archiveTarget.value = null
    await loadStock()
  } catch (caught) {
    actionError.value = messageOf(caught, 'We could not archive this ingredient.')
  } finally {
    archivePending.value = false
  }
}

// -- Record movement --------------------------------------------------

const movementModalOpen = ref(false)
const movementForm = reactive({
  ingredientId: '',
  type: 'RECEIPT' as InventoryMovementType,
  quantity: 0,
  unit: '',
  note: '',
  lowStockThreshold: undefined as number | undefined,
})
const movementSaving = ref(false)
const movementFormError = ref('')

const movementTypes: InventoryMovementType[] = [
  'RECEIPT',
  'ADJUSTMENT_IN',
  'ADJUSTMENT_OUT',
  'WASTE',
  'STOCK_COUNT',
]

function openMovementModal() {
  const first = stock.value[0]
  movementForm.ingredientId = first?.ingredientId ?? ''
  movementForm.type = 'RECEIPT'
  movementForm.quantity = 0
  movementForm.unit = first?.baseUnit ?? ''
  movementForm.note = ''
  movementForm.lowStockThreshold = undefined
  movementFormError.value = ''
  movementModalOpen.value = true
}

function onMovementIngredientChange() {
  const selected = stock.value.find((row) => row.ingredientId === movementForm.ingredientId)
  if (selected) {
    movementForm.unit = selected.baseUnit
  }
}

async function submitMovement() {
  if (!movementForm.ingredientId) {
    movementFormError.value = 'Create an ingredient first.'
    return
  }
  movementSaving.value = true
  movementFormError.value = ''
  try {
    await inventoryApi.recordMovement({
      ingredientId: movementForm.ingredientId,
      type: movementForm.type,
      quantity: movementForm.quantity,
      unit: movementForm.unit,
      note: movementForm.note || undefined,
      lowStockThreshold: movementForm.lowStockThreshold,
    })
    movementModalOpen.value = false
    await Promise.all([loadStock(), loadMovements()])
  } catch (caught) {
    movementFormError.value = messageOf(caught, 'We could not record this movement.')
  } finally {
    movementSaving.value = false
  }
}

// -- Add cost -----------------------------------------------------------

const costModalOpen = ref(false)
const costTargetName = ref('')
const costForm = reactive({ ingredientId: '', unitCost: 0, costUnit: '', source: '', note: '' })
const costSaving = ref(false)
const costFormError = ref('')
const costSavedNotice = ref('')

function openCostModal(row: StockBalanceResponse) {
  costForm.ingredientId = row.ingredientId
  costForm.unitCost = 0
  costForm.costUnit = row.baseUnit
  costForm.source = ''
  costForm.note = ''
  costTargetName.value = row.ingredientName
  costFormError.value = ''
  costSavedNotice.value = ''
  costModalOpen.value = true
}

async function submitCost() {
  costSaving.value = true
  costFormError.value = ''
  try {
    await inventoryApi.addCost(costForm.ingredientId, {
      unitCost: costForm.unitCost,
      costUnit: costForm.costUnit,
      source: costForm.source || undefined,
      note: costForm.note || undefined,
    })
    costModalOpen.value = false
    costSavedNotice.value = `Recorded cost for ${costTargetName.value}.`
  } catch (caught) {
    costFormError.value = messageOf(caught, 'We could not save this cost record.')
  } finally {
    costSaving.value = false
  }
}

const toggleLabel = computed(() => (lowStockOnly.value ? 'Show all ingredients' : 'Show low-stock only'))
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Admin module</p>
        <h2>Inventory</h2>
        <p>Ingredients, stock balances, movements, costs, and low-stock review.</p>
      </div>
    </div>

    <p v-if="actionError" class="form-error">{{ actionError }}</p>
    <p v-if="costSavedNotice" class="notice">{{ costSavedNotice }}</p>

    <Toolbar>
      <template #filters>
        <button class="ghost-button" type="button" @click="toggleLowStock">{{ toggleLabel }}</button>
      </template>
      <template #actions>
        <button class="ghost-button" type="button" @click="openIngredientModal()">New ingredient</button>
        <button class="primary-button" type="button" @click="openMovementModal">Record movement</button>
      </template>
    </Toolbar>

    <section class="panel">
      <div class="panel-header">
        <h3>Stock balances</h3>
        <StatusBadge :tone="loading ? 'muted' : error ? 'danger' : 'success'">
          {{ loading ? 'Loading' : error ? 'Needs backend' : 'Ready' }}
        </StatusBadge>
      </div>
      <EmptyState v-if="error" tone="error" :body="error" @retry="loadStock" />
      <div v-else-if="loading" class="skeleton-table" />
      <DataTable v-else :columns="stockColumns" :rows="stock" row-key="ingredientId" empty-text="No stock records yet.">
        <template #cell-lowStock="{ value }">
          <StatusBadge :tone="value ? 'warning' : 'success'">{{ value ? 'Low' : 'OK' }}</StatusBadge>
        </template>
        <template #cell-ingredientStatus="{ value }">
          <StatusBadge :status="value as string" />
        </template>
        <template #cell-actions="{ row }">
          <div class="table-actions">
            <button class="ghost-button small" type="button" @click="openIngredientModal(row as any)">Edit</button>
            <button class="ghost-button small" type="button" @click="openCostModal(row as any)">Add cost</button>
            <button
              class="ghost-button small"
              type="button"
              @click="archiveTarget = { id: row.ingredientId as string, name: row.ingredientName as string }"
            >
              Archive
            </button>
          </div>
        </template>
      </DataTable>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h3>Recent movements</h3>
        <StatusBadge :tone="movementsLoading ? 'muted' : movementsError ? 'danger' : 'success'">
          {{ movementsLoading ? 'Loading' : movementsError ? 'Needs backend' : 'Ready' }}
        </StatusBadge>
      </div>
      <EmptyState v-if="movementsError" tone="error" :body="movementsError" @retry="loadMovements" />
      <div v-else-if="movementsLoading" class="skeleton-table" />
      <DataTable v-else :columns="movementColumns" :rows="movements" row-key="movementId" empty-text="No movements recorded yet.">
        <template #cell-resultingBalance="{ value }">{{ formatMoney(value as number) }}</template>
        <template #cell-createdAt="{ value }">{{ formatDateTime(value as string) }}</template>
      </DataTable>
    </section>

    <Modal :open="ingredientModalOpen" :title="ingredientModalTitle" @close="ingredientModalOpen = false">
      <form class="field-grid" @submit.prevent="submitIngredient">
        <label class="span-2">
          Name
          <input v-model="ingredientForm.name" required />
        </label>
        <label>
          Base unit
          <input v-model="ingredientForm.baseUnit" placeholder="kg, g, l, unit…" required />
        </label>
        <label>
          Status
          <select v-model="ingredientForm.status">
            <option value="ACTIVE">ACTIVE</option>
            <option value="ARCHIVED">ARCHIVED</option>
          </select>
        </label>
        <label class="span-2">
          Description
          <input v-model="ingredientForm.description" />
        </label>
        <p v-if="ingredientFormError" class="form-error span-2">{{ ingredientFormError }}</p>
        <div class="modal-footer span-2">
          <button class="ghost-button" type="button" @click="ingredientModalOpen = false">Cancel</button>
          <button class="primary-button" type="submit" :disabled="ingredientSaving">
            {{ ingredientSaving ? 'Saving…' : 'Save changes' }}
          </button>
        </div>
      </form>
    </Modal>

    <Modal :open="movementModalOpen" title="Record movement" @close="movementModalOpen = false">
      <form class="field-grid" @submit.prevent="submitMovement">
        <label class="span-2">
          Ingredient
          <select v-model="movementForm.ingredientId" required @change="onMovementIngredientChange">
            <option v-for="row in stock" :key="row.ingredientId" :value="row.ingredientId">
              {{ row.ingredientName }}
            </option>
          </select>
        </label>
        <label>
          Type
          <select v-model="movementForm.type">
            <option v-for="type in movementTypes" :key="type" :value="type">{{ type }}</option>
          </select>
        </label>
        <label>
          Quantity
          <input v-model.number="movementForm.quantity" type="number" step="0.01" required />
        </label>
        <label>
          Unit
          <input v-model="movementForm.unit" required />
        </label>
        <label>
          Low-stock threshold
          <input v-model.number="movementForm.lowStockThreshold" type="number" step="0.01" placeholder="Optional" />
        </label>
        <label class="span-2">
          Note
          <input v-model="movementForm.note" />
        </label>
        <p v-if="movementFormError" class="form-error span-2">{{ movementFormError }}</p>
        <div class="modal-footer span-2">
          <button class="ghost-button" type="button" @click="movementModalOpen = false">Cancel</button>
          <button class="primary-button" type="submit" :disabled="movementSaving">
            {{ movementSaving ? 'Saving…' : 'Save changes' }}
          </button>
        </div>
      </form>
    </Modal>

    <Modal :open="costModalOpen" :title="`Add cost — ${costTargetName}`" @close="costModalOpen = false">
      <form class="field-grid" @submit.prevent="submitCost">
        <label>
          Unit cost
          <input v-model.number="costForm.unitCost" type="number" step="0.01" min="0" required />
        </label>
        <label>
          Cost unit
          <input v-model="costForm.costUnit" required />
        </label>
        <label>
          Source
          <input v-model="costForm.source" placeholder="Optional" />
        </label>
        <label>
          Note
          <input v-model="costForm.note" placeholder="Optional" />
        </label>
        <p v-if="costFormError" class="form-error span-2">{{ costFormError }}</p>
        <div class="modal-footer span-2">
          <button class="ghost-button" type="button" @click="costModalOpen = false">Cancel</button>
          <button class="primary-button" type="submit" :disabled="costSaving">
            {{ costSaving ? 'Saving…' : 'Save changes' }}
          </button>
        </div>
      </form>
    </Modal>

    <ConfirmDialog
      :open="Boolean(archiveTarget)"
      title="Confirm action"
      :message="`Confirm action: archiving '${archiveTarget?.name}' may affect active restaurant operations.`"
      confirm-label="Archive"
      danger
      :pending="archivePending"
      @close="archiveTarget = null"
      @confirm="confirmArchiveIngredient"
    />
  </section>
</template>
