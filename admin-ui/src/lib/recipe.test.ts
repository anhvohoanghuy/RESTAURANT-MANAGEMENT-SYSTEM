import { describe, expect, it } from 'vitest'
import { toRecipeRequest } from './recipe'
import type { IngredientResponse } from '../api/modules'

const BEEF: IngredientResponse = {
  ingredientId: 'i-1',
  name: 'Beef',
  baseUnit: 'kg',
  description: null,
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const ONION: IngredientResponse = {
  ingredientId: 'i-2',
  name: 'Onion',
  baseUnit: 'g',
  description: null,
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('toRecipeRequest', () => {
  it('resolves the ingredient display name from the ingredient id and sets sortOrder to the row index', () => {
    const result = toRecipeRequest('dish-1', 'Pho', [{ ingredientId: 'i-1', quantity: 2, unit: 'kg' }], [BEEF])

    expect(result).toEqual({
      targetType: 'DISH',
      targetId: 'dish-1',
      name: 'Pho',
      lines: [{ ingredientId: 'i-1', ingredient: 'Beef', quantity: 2, unit: 'kg', sortOrder: 0 }],
    })
  })

  it('includes both ingredientId and the resolved ingredient name on every line (RESEARCH Pitfall 2)', () => {
    const result = toRecipeRequest(
      'dish-1',
      'Pho',
      [
        { ingredientId: 'i-1', quantity: 2, unit: 'kg' },
        { ingredientId: 'i-2', quantity: 0.5, unit: 'g' },
      ],
      [BEEF, ONION],
    )

    for (const line of result.lines) {
      expect(line.ingredientId).toBeTruthy()
      expect(typeof line.ingredient).toBe('string')
      expect(line.ingredient.length).toBeGreaterThan(0)
    }
  })

  it('resolves ingredient to an empty string (not undefined) when the ingredientId is not found', () => {
    const result = toRecipeRequest('dish-1', 'Pho', [{ ingredientId: 'missing', quantity: 1, unit: 'kg' }], [BEEF])

    expect(result.lines[0].ingredient).toBe('')
    expect(result.lines[0].ingredient).not.toBeUndefined()
  })

  it('assigns ascending sortOrder (0,1,2…) across multiple rows', () => {
    const result = toRecipeRequest(
      'dish-1',
      'Pho',
      [
        { ingredientId: 'i-1', quantity: 2, unit: 'kg' },
        { ingredientId: 'i-2', quantity: 0.5, unit: 'g' },
        { ingredientId: 'missing', quantity: 1, unit: 'unit' },
      ],
      [BEEF, ONION],
    )

    expect(result.lines.map((line) => line.sortOrder)).toEqual([0, 1, 2])
  })

  it('passes through targetType DISH, targetId, and dish name unchanged', () => {
    const result = toRecipeRequest('dish-42', 'Bun Cha', [], [BEEF])

    expect(result.targetType).toBe('DISH')
    expect(result.targetId).toBe('dish-42')
    expect(result.name).toBe('Bun Cha')
    expect(result.lines).toEqual([])
  })
})
