<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { X } from '@lucide/vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import DataTable, { type DataTableColumn } from '../components/DataTable.vue'
import EmptyState from '../components/EmptyState.vue'
import GapNotice from '../components/GapNotice.vue'
import Modal from '../components/Modal.vue'
import StatusBadge from '../components/StatusBadge.vue'
import Toolbar from '../components/Toolbar.vue'
import {
  knownGaps,
  menuApi,
  type MenuCostingItem,
  type MenuStatus,
  type PublicCategory,
  type PublicMenuResponse,
} from '../api/modules'
import { isAdmin } from '../stores/auth'
import { formatMoney, formatPercent, messageOf } from '../lib/format'

const router = useRouter()

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
  description: string
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
      description: dish.description,
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

// -- Category create + edit ----------------------------------------------

const categoryModalOpen = ref(false)
const editingCategoryId = ref<string | null>(null)
const categoryForm = reactive({ name: '', description: '', sortOrder: 0, status: 'ACTIVE' as MenuStatus })
const categorySaving = ref(false)
const categoryFormError = ref('')

const categoryModalTitle = computed(() => (editingCategoryId.value ? 'Edit category' : 'New category'))

function openCategoryModal(row?: PublicCategory) {
  editingCategoryId.value = row?.id ?? null
  categoryForm.name = row?.name ?? ''
  categoryForm.description = row?.description ?? ''
  categoryForm.sortOrder = row?.sortOrder ?? 0
  // Public menu categories are always ACTIVE (findActiveOrdered on the backend); safe default on edit.
  categoryForm.status = 'ACTIVE'
  categoryFormError.value = ''
  categoryModalOpen.value = true
}

async function submitCategory() {
  categorySaving.value = true
  categoryFormError.value = ''
  try {
    const payload = {
      name: categoryForm.name,
      description: categoryForm.description || undefined,
      sortOrder: categoryForm.sortOrder,
      status: categoryForm.status,
    }
    if (editingCategoryId.value) {
      await menuApi.updateCategory(editingCategoryId.value, payload)
    } else {
      await menuApi.createCategory(payload)
    }
    editingCategoryId.value = null
    categoryModalOpen.value = false
    await loadMenu()
  } catch (caught) {
    categoryFormError.value = messageOf(caught, 'We could not save this category.')
  } finally {
    categorySaving.value = false
  }
}

// -- Dish create + edit ---------------------------------------------------

const dishModalOpen = ref(false)
const editingDishId = ref<string | null>(null)
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

const dishModalTitle = computed(() => (editingDishId.value ? 'Edit dish' : 'New dish'))

function openDishModal(row?: DishRow) {
  editingDishId.value = row?.id ?? null
  dishForm.categoryId = row?.categoryId ?? menu.value.categories[0]?.id ?? ''
  dishForm.name = row?.name ?? ''
  dishForm.description = row?.description ?? ''
  dishForm.basePrice = row?.basePrice ?? 0
  dishForm.sortOrder = row?.sortOrder ?? 0
  // Dish rows come from the public menu (always ACTIVE); safe default on edit.
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
    const payload = {
      categoryId: dishForm.categoryId,
      name: dishForm.name,
      description: dishForm.description || undefined,
      basePrice: dishForm.basePrice,
      status: dishForm.status,
      sortOrder: dishForm.sortOrder,
    }
    if (editingDishId.value) {
      await menuApi.updateDish(editingDishId.value, payload)
    } else {
      await menuApi.createDish(payload)
    }
    editingDishId.value = null
    dishModalOpen.value = false
    await loadMenu()
  } catch (caught) {
    dishFormError.value = messageOf(caught, 'We could not save this dish.')
  } finally {
    dishSaving.value = false
  }
}

function goToRecipe(dishId: string) {
  router.push({ name: 'recipe', params: { dishId } })
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
        <button v-if="isAdmin" class="ghost-button" type="button" @click="openCategoryModal()">New category</button>
      </div>
      <div class="tag-list">
        <span v-for="category in menu.categories" :key="category.id" class="tag-chip">
          <button
            v-if="isAdmin"
            type="button"
            :aria-label="`Edit ${category.name}`"
            @click="openCategoryModal(category)"
          >
            {{ category.name }}
          </button>
          <template v-else>{{ category.name }}</template>
          <button
            v-if="isAdmin"
            type="button"
            :aria-label="`Archive ${category.name}`"
            @click="requestArchive('category', category.id, category.name)"
          >
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
        <button v-if="isAdmin" class="primary-button" type="button" @click="openDishModal()">New dish</button>
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
            <button
              v-if="isAdmin"
              class="ghost-button small"
              type="button"
              @click="openDishModal(row as unknown as DishRow)"
            >
              Edit
            </button>
            <button
              v-if="isAdmin"
              class="ghost-button small"
              type="button"
              @click="requestArchive('dish', row.id as string, row.name as string)"
            >
              Archive
            </button>
            <button v-if="isAdmin" class="ghost-button small" type="button" @click="goToRecipe(row.id as string)">
              Recipe
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

    <Modal :open="categoryModalOpen" :title="categoryModalTitle" @close="categoryModalOpen = false">
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

    <Modal :open="dishModalOpen" :title="dishModalTitle" @close="dishModalOpen = false">
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
