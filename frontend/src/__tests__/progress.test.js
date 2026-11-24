import { describe, expect, it } from 'vitest'
import { orderProgress } from '../utils/progress.js'

describe('production order progress', () => {
  it('returns the rounded percentage of reported quantity against plan', () => {
    expect(orderProgress({ plannedQuantity: 12, goodQuantity: 5, defectQuantity: 1 })).toBe(50)
  })

  it('keeps progress within zero and one hundred for malformed or over-reported data', () => {
    expect(orderProgress({ plannedQuantity: 0, goodQuantity: 4, defectQuantity: 0 })).toBe(0)
    expect(orderProgress({ plannedQuantity: 5, goodQuantity: 5, defectQuantity: 2 })).toBe(100)
    expect(orderProgress({ plannedQuantity: 10, goodQuantity: -3, defectQuantity: 0 })).toBe(0)
  })
})
