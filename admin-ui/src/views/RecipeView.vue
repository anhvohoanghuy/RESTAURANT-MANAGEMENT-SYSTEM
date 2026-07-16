<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { X } from '@lucide/vue'
import DataTable, { type DataTableColumn } from '../components/DataTable.vue'
import EmptyState from '../components/EmptyState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { ApiError } from '../api/client'
import { inventoryApi, menuApi, type IngredientResponse, type RecipeCostResponse } from '../api/modules'
import { formatMoney, messageOf } from '../lib/format'
import { toRecipeRequest, type RecipeRowForm } from '../lib/recipe'

const route = useRoute()
const router = useRouter()

const dishId = String(route.params.dishId ?? '')

const loading = ref(true)
const error = ref('')
const dishName = ref('')
const rows = ref<RecipeRowForm[]>([])
const ingredients = ref<IngredientResponse[]>([])

const saving = ref(false)
const formError = ref('')

const costLoading = ref(false)
const costError = ref('')
const cost = ref<RecipeCostResponse | null>(null)

const costColumns: DataTableColumn[] = [
  { key: 'ingredientName', label: 'Ingredient' },
  { key: 'quantity', label: 'Quantity' },
  { key: 'unit', label: 'Unit' },
  { key: 'lineCost', label: 'Line cost' },
]

async function loadRecipe() {
  loading.value = true
  error.value = ''
  try {
    const [menu, ingredientList] = await Promise.all([menuApi.getPublicMenu(), inventoryApi.listIngredients()])
    ingredients.value = ingredientList
    const dish = menu.categories.flatMap((category) => category.dishes).find((candidate) => candidate.id === dishId)
    dishName.value = dish?.name ?? ''

    try {
      const recipe = await menuApi.getRecipe('DISH', dishId)
      dishName.value = recipe.name || dishName.value
      rows.value = recipe.lines
        .slice()
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((line) => ({ ingredientId: line.ingredientId, quantity: line.quantity, unit: line.unit }))
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 404) {
        // No recipe saved yet for this dish -- a valid initial state, not a load failure.
        rows.value = []
      } else {
        throw caught
      }
    }
  } catch (caught) {
    error.value = messageOf(caught, 'We could not load this data.')
  } finally {
    loading.value = false
  }
}

async function loadRecipeCost() {
  costLoading.value = true
  costError.value = ''
  try {
    cost.value = await menuApi.recipeCost('DISH', dishId)
  } catch (caught) {
    costError.value = messageOf(caught, 'We could not load this data.')
  } finally {
    costLoading.value = false
  }
}

onMounted(() => loadRecipe())

function addRow() {
  rows.value.push({ ingredientId: '', quantity: 0, unit: '' })
}

function removeRow(index: number) {
  rows.value.splice(index, 1)
}

function onIngredientChange(index: number) {
  const row = rows.value[index]
  if (!row) {
    return
  }
  const selected = ingredients.value.find((ingredient) => ingredient.ingredientId === row.ingredientId)
  if (selected) {
    row.unit = selected.baseUnit
  }
}

async function saveRecipe() {
  formError.value = ''
  if (rows.value.some((row) => !row.ingredientId || !row.quantity || !row.unit)) {
    formError.value = 'Every ingredient row needs an ingredient, quantity, and unit.'
    return
  }
  saving.value = true
  try {
    const payload = toRecipeRequest(dishId, dishName.value, rows.value, ingredients.value)
    await menuApi.upsertRecipe(payload)
    await loadRecipeCost()
  } catch (caught) {
    formError.value = messageOf(caught, 'We could not save this recipe.')
  } finally {
    saving.value = false
  }
}

function backToMenu() {
  router.push('/menu')
}
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Admin module</p>
        <h2>Recipe — {{ dishName || 'Dish' }}</h2>
        <p>Ingredients required to prepare this dish, and its computed cost.</p>
      </div>
      <button class="ghost-button" type="button" @click="backToMenu">Back to menu</button>
    </div>

    <section class="panel">
      <div class="panel-header">
        <h3>Recipe ingredients</h3>
      </div>
      <EmptyState v-if="error" tone="error" :body="error" @retry="loadRecipe" />
      <div v-else-if="loading" class="skeleton-table" />
      <template v-else>
        <EmptyState
          v-if="!rows.length"
          tone="empty"
          title="No ingredients yet"
          body="Add ingredient rows to build this dish's recipe. Costs calculate once you save."
          :retryable="false"
        />
        <div v-else class="recipe-rows">
          <div v-for="(row, index) in rows" :key="index" class="recipe-row">
            <select v-model="row.ingredientId" aria-label="Ingredient" @change="onIngredientChange(index)">
              <option value="" disabled>Select ingredient</option>
              <option
                v-for="ingredient in ingredients"
                :key="ingredient.ingredientId"
                :value="ingredient.ingredientId"
              >
                {{ ingredient.name }}
              </option>
            </select>
            <input v-model.number="row.quantity" type="number" step="0.01" min="0" aria-label="Quantity" />
            <input v-model="row.unit" aria-label="Unit" />
            <button
              class="icon-button"
              type="button"
              aria-label="Remove ingredient row"
              @click="removeRow(index)"
            >
              <X :size="14" />
            </button>
          </div>
        </div>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <button class="ghost-button" type="button" @click="addRow">Add ingredient</button>
      </template>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h3>Recipe cost</h3>
        <StatusBadge v-if="cost" :tone="cost.fullyCosted ? 'success' : 'warning'">
          {{ cost.fullyCosted ? 'Fully costed' : 'Partial' }}
        </StatusBadge>
      </div>
      <EmptyState v-if="costError" tone="error" :body="costError" @retry="loadRecipeCost" />
      <div v-else-if="costLoading" class="skeleton-table" />
      <template v-else-if="cost">
        <p>Total: {{ formatMoney(cost.totalCost) }}</p>
        <DataTable :columns="costColumns" :rows="cost.lines" row-key="recipeLineId" empty-text="No cost lines yet.">
          <template #cell-lineCost="{ value }">{{ formatMoney(value as number) }}</template>
        </DataTable>
      </template>
      <p v-else class="field-hint">Save the recipe to see its cost breakdown.</p>
    </section>

    <div class="modal-footer">
      <button class="ghost-button" type="button" @click="backToMenu">Back to menu</button>
      <button class="primary-button" type="button" :disabled="saving" @click="saveRecipe">
        {{ saving ? 'Saving…' : 'Save recipe' }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.recipe-rows {
  display: grid;
  gap: 12px;
}

.recipe-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface);
}

.recipe-row select,
.recipe-row input {
  flex: 1;
}
</style>
