<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { X } from '@lucide/vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import DataTable, { type DataTableColumn } from '../components/DataTable.vue'
import EmptyState from '../components/EmptyState.vue'
import GapNotice from '../components/GapNotice.vue'
import Modal from '../components/Modal.vue'
import StatusBadge from '../components/StatusBadge.vue'
import Toolbar from '../components/Toolbar.vue'
import { ApiError } from '../api/client'
import {
  knownGaps,
  menuApi,
  type MenuCostingItem,
  type MenuStatus,
  type PublicMenuResponse,
} from '../api/modules'
import { formatMoney, formatPercent, messageOf } from '../lib/format'

const loading = ref(true)
const error = ref('')
const menu = ref<PublicMenuResponse>({ categories: [] })
const search = ref('')

const costingLoading = ref(true)
const costingError = ref('')
const costing = ref<MenuCostingItem[]>([])

async function loadMenu() {
  loading.value = true
  error.value = ''
  try {
    menu.value = await menuApi.getPublicMenu()
  } catch (caught) {
    error.value = messageOf(caught, 'We could not load this data.')
  } finally {
    loading.value = false
  }
}

async function loadCosting() {
  costingLoading.value = true
  costingError.value = ''
  try {
    const response = await menuApi.getMenuCosting()
    costing.value = response.items
  } catch (caught) {
    costingError.value = messageOf(caught, 'We could not load this data.')
  } finally {
    costingLoading.value = false
  }
}

onMounted(() => {
  loadMenu()
  loadCosting()
})

type DishRow = {
  id: string
  name: string
  basePrice: number
  sortOrder: number
  categoryId: string
  categoryName: string
}

const dishRows = computed<DishRow[]>(() =>
  menu.value.categories.flatMap((category) =>
    category.dishes.map((dish) => ({
      id: dish.id,
      name: dish.name,
      basePrice: dish.basePrice,
      sortOrder: dish.sortOrder,
      categoryId: category.id,
      categoryName: category.name,
    })),
  ),
)

const filteredDishRows = computed(() => {
  const term = search.value.trim().toLowerCase()
  if (!term) {
    return dishRows.value
  }
  return dishRows.value.filter(
    (row) => row.name.toLowerCase().includes(term) || row.categoryName.toLowerCase().includes(term),
  )
})

const dishColumns: DataTableColumn[] = [
  { key: 'categoryName', label: 'Category' },
  { key: 'name', label: 'Dish' },
  { key: 'basePrice', label: 'Price' },
  { key: 'sortOrder', label: 'Sort' },
  { key: 'actions', label: '' },
]

const costingColumns: DataTableColumn[] = [
  { key: 'dishName', label: 'Dish' },
  { key: 'sellPrice', label: 'Sell price' },
  { key: 'estimatedCost', label: 'Est. cost' },
  { key: 'grossMarginPercent', label: 'Margin' },
  { key: 'fullyCosted', label: 'Costed' },
]

const gaps = knownGaps.menu

// -- Category creation -------------------------------------------------

const categoryModalOpen = ref(false)
const categoryForm = reactive({ name: '', description: '', sortOrder: 0, status: 'ACTIVE' as MenuStatus })
const categorySaving = ref(false)
const categoryFormError = ref('')

function openCategoryModal() {
  categoryForm.name = ''
  categoryForm.description = ''
  categoryForm.sortOrder = 0
  categoryForm.status = 'ACTIVE'
  categoryFormError.value = ''
  categoryModalOpen.value = true
}

async function submitCategory() {
  categorySaving.value = true
  categoryFormError.value = ''
  try {
    await menuApi.createCategory({
      name: categoryForm.name,
      description: categoryForm.description || undefined,
      sortOrder: categoryForm.sortOrder,
      status: categoryForm.status,
    })
    categoryModalOpen.value = false
    await loadMenu()
  } catch (caught) {
    categoryFormError.value = caught instanceof ApiError ? caught.message : 'We could not save this category.'
  } finally {
    categorySaving.value = false
  }
}

// -- Dish creation -------------------------------------------------------

const dishModalOpen = ref(false)
const dishForm = reactive({
  categoryId: '',
  name: '',
  description: '',
  basePrice: 0,
  sortOrder: 0,
  status: 'ACTIVE' as MenuStatus,
})
const dishSaving = ref(false)
const dishFormError = ref('')

function openDishModal() {
  dishForm.categoryId = menu.value.categories[0]?.id ?? ''
  dishForm.name = ''
  dishForm.description = ''
  dishForm.basePrice = 0
  dishForm.sortOrder = 0
  dishForm.status = 'ACTIVE'
  dishFormError.value = ''
  dishModalOpen.value = true
}

async function submitDish() {
  if (!dishForm.categoryId) {
    dishFormError.value = 'Create a category first.'
    return
  }
  dishSaving.value = true
  dishFormError.value = ''
  try {
    await menuApi.createDish({
      categoryId: dishForm.categoryId,
      name: dishForm.name,
      description: dishForm.description || undefined,
      basePrice: dishForm.basePrice,
      status: dishForm.status,
      sortOrder: dishForm.sortOrder,
    })
    dishModalOpen.value = false
    await loadMenu()
  } catch (caught) {
    dishFormError.value = caught instanceof ApiError ? caught.message : 'We could not save this dish.'
  } finally {
    dishSaving.value = false
  }
}

// -- Archive (category or dish) ------------------------------------------

const confirmTarget = ref<{ kind: 'category' | 'dish'; id: string; name: string } | null>(null)
const confirmPending = ref(false)

function requestArchive(kind: 'category' | 'dish', id: string, name: string) {
  confirmTarget.value = { kind, id, name }
}

async function confirmArchive() {
  if (!confirmTarget.value) {
    return
  }
  confirmPending.value = true
  try {
    if (confirmTarget.value.kind === 'category') {
      await menuApi.archiveCategory(confirmTarget.value.id)
    } else {
      await menuApi.archiveDish(confirmTarget.value.id)
    }
    confirmTarget.value = null
    await loadMenu()
  } catch (caught) {
    error.value = messageOf(caught, 'We could not archive this record.')
  } finally {
    confirmPending.value = false
  }
}
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Admin module</p>
        <h2>Menu</h2>
        <p>Catalog, recipes, toppings, and costing reads.</p>
      </div>
    </div>

    <GapNotice v-for="gap in gaps" :key="gap.label" :label="gap.label" :detail="gap.detail" />

    <section class="panel">
      <div class="panel-header">
        <h3>Categories</h3>
        <button class="ghost-button" type="button" @click="openCategoryModal">New category</button>
      </div>
      <div class="tag-list">
        <span v-for="category in menu.categories" :key="category.id" class="tag-chip">
          {{ category.name }}
          <button type="button" :aria-label="`Archive ${category.name}`" @click="requestArchive('category', category.id, category.name)">
            <X :size="12" />
          </button>
        </span>
        <span v-if="!menu.categories.length && !loading" class="field-hint">No categories yet.</span>
      </div>
    </section>

    <Toolbar>
      <template #filters>
        <label>
          Search dishes
          <input v-model="search" type="search" placeholder="Dish or category name" />
        </label>
      </template>
      <template #actions>
        <button class="primary-button" type="button" @click="openDishModal">New dish</button>
      </template>
    </Toolbar>

    <section class="panel">
      <div class="panel-header">
        <h3>Dishes</h3>
        <StatusBadge :tone="loading ? 'muted' : error ? 'danger' : 'success'">
          {{ loading ? 'Loading' : error ? 'Needs backend' : 'Ready' }}
        </StatusBadge>
      </div>
      <EmptyState v-if="error" tone="error" :body="error" @retry="loadMenu" />
      <div v-else-if="loading" class="skeleton-table" />
      <DataTable v-else :columns="dishColumns" :rows="filteredDishRows" row-key="id" empty-text="No active dishes yet.">
        <template #cell-basePrice="{ value }">{{ formatMoney(value as number) }}</template>
        <template #cell-actions="{ row }">
          <div class="table-actions">
            <button class="ghost-button small" type="button" @click="requestArchive('dish', row.id as string, row.name as string)">
              Archive
            </button>
          </div>
        </template>
      </DataTable>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h3>Menu costing</h3>
        <StatusBadge :tone="costingLoading ? 'muted' : costingError ? 'danger' : 'success'">
          {{ costingLoading ? 'Loading' : costingError ? 'Needs backend' : 'Ready' }}
        </StatusBadge>
      </div>
      <EmptyState v-if="costingError" tone="error" :body="costingError" @retry="loadCosting" />
      <div v-else-if="costingLoading" class="skeleton-table" />
      <DataTable v-else :columns="costingColumns" :rows="costing" row-key="dishId" empty-text="No costed dishes yet.">
        <template #cell-sellPrice="{ value }">{{ formatMoney(value as number) }}</template>
        <template #cell-estimatedCost="{ value }">{{ formatMoney(value as number) }}</template>
        <template #cell-grossMarginPercent="{ value }">{{ formatPercent(value as number) }}</template>
        <template #cell-fullyCosted="{ value }">
          <StatusBadge :tone="value ? 'success' : 'warning'">{{ value ? 'Yes' : 'Partial' }}</StatusBadge>
        </template>
      </DataTable>
    </section>

    <div class="notice-panel">
      <div>
        <h3>Topping groups, options, and recipes</h3>
        <p>
          Create/update endpoints for topping groups, topping options, and dish recipes are wired through
          <code>menuApi</code>, but a dedicated editor UI is deferred to a follow-up admin polish pass. Use the
          API directly, or extend this view, until that lands.
        </p>
      </div>
    </div>

    <Modal :open="categoryModalOpen" title="New category" @close="categoryModalOpen = false">
      <form class="field-grid" @submit.prevent="submitCategory">
        <label class="span-2">
          Name
          <input v-model="categoryForm.name" required />
        </label>
        <label class="span-2">
          Description
          <input v-model="categoryForm.description" />
        </label>
        <label>
          Sort order
          <input v-model.number="categoryForm.sortOrder" type="number" />
        </label>
        <label>
          Status
          <select v-model="categoryForm.status">
            <option value="ACTIVE">ACTIVE</option>
            <option value="INACTIVE">INACTIVE</option>
            <option value="ARCHIVED">ARCHIVED</option>
          </select>
        </label>
        <p v-if="categoryFormError" class="form-error span-2">{{ categoryFormError }}</p>
        <div class="modal-footer span-2">
          <button class="ghost-button" type="button" @click="categoryModalOpen = false">Cancel</button>
          <button class="primary-button" type="submit" :disabled="categorySaving">
            {{ categorySaving ? 'Saving…' : 'Save changes' }}
          </button>
        </div>
      </form>
    </Modal>

    <Modal :open="dishModalOpen" title="New dish" @close="dishModalOpen = false">
      <form class="field-grid" @submit.prevent="submitDish">
        <label class="span-2">
          Category
          <select v-model="dishForm.categoryId" required>
            <option v-for="category in menu.categories" :key="category.id" :value="category.id">
              {{ category.name }}
            </option>
          </select>
        </label>
        <label class="span-2">
          Name
          <input v-model="dishForm.name" required />
        </label>
        <label class="span-2">
          Description
          <input v-model="dishForm.description" />
        </label>
        <label>
          Base price
          <input v-model.number="dishForm.basePrice" type="number" step="0.01" min="0" required />
        </label>
        <label>
          Sort order
          <input v-model.number="dishForm.sortOrder" type="number" />
        </label>
        <label>
          Status
          <select v-model="dishForm.status">
            <option value="ACTIVE">ACTIVE</option>
            <option value="INACTIVE">INACTIVE</option>
            <option value="ARCHIVED">ARCHIVED</option>
          </select>
        </label>
        <p v-if="dishFormError" class="form-error span-2">{{ dishFormError }}</p>
        <div class="modal-footer span-2">
          <button class="ghost-button" type="button" @click="dishModalOpen = false">Cancel</button>
          <button class="primary-button" type="submit" :disabled="dishSaving">
            {{ dishSaving ? 'Saving…' : 'Save changes' }}
          </button>
        </div>
      </form>
    </Modal>

    <ConfirmDialog
      :open="Boolean(confirmTarget)"
      title="Confirm action"
      :message="`Confirm action: archiving '${confirmTarget?.name}' may affect active restaurant operations.`"
      confirm-label="Archive"
      danger
      :pending="confirmPending"
      @close="confirmTarget = null"
      @confirm="confirmArchive"
    />
  </section>
</template>
