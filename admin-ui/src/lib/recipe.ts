import type { IngredientResponse, RecipeRequest } from '../api/modules'

export type RecipeRowForm = { ingredientId: string; quantity: number; unit: string }

/**
 * Pure transform from a recipe builder's row-array form state into the
 * RecipeRequest payload the backend expects.
 *
 * Every RecipeRequest.Line carries BOTH the ingredientId and the resolved
 * ingredient display name (RESEARCH.md Pitfall 2 — the backend RecipeRequest.Line
 * record requires both fields, not just the id). sortOrder is auto-assigned
 * from the row's zero-based index — it is never exposed as a form field.
 */
export function toRecipeRequest(
  dishId: string,
  dishName: string,
  rows: RecipeRowForm[],
  ingredients: IngredientResponse[],
): RecipeRequest {
  return {
    targetType: 'DISH',
    targetId: dishId,
    name: dishName,
    lines: rows.map((row, index) => ({
      ingredientId: row.ingredientId,
      ingredient: ingredients.find((ingredient) => ingredient.ingredientId === row.ingredientId)?.name ?? '',
      quantity: row.quantity,
      unit: row.unit,
      sortOrder: index,
    })),
  }
}
