import { describe, expect, it } from 'vitest'
import { canAccessRoute, navigationForRole, roleLabelFor } from '../auth/roles.js'

const keysFor = (role) => navigationForRole(role).map((item) => item.key)

const expectedNavigation = {
  ADMIN: ['dashboard', 'products', 'orders', 'approvals', 'inventory', 'purchasing', 'mes', 'quality'],
  PLANNER: ['dashboard', 'products', 'orders'],
  APPROVER: ['dashboard', 'approvals'],
  WAREHOUSE: ['dashboard', 'inventory'],
  PURCHASER: ['dashboard', 'purchasing'],
  OPERATOR: ['dashboard', 'mes'],
  QUALITY_INSPECTOR: ['dashboard', 'quality'],
}

describe('role navigation and access', () => {
  it.each(Object.entries(expectedNavigation))('shows only assigned modules to %s', (role, expectedKeys) => {
    expect(keysFor(role)).toEqual(expectedKeys)
  })

  it('uses distinct role labels for all operational jobs', () => {
    expect(roleLabelFor('PLANNER')).toBe('计划员')
    expect(roleLabelFor('APPROVER')).toBe('审批员')
    expect(roleLabelFor('WAREHOUSE')).toBe('仓库员')
    expect(roleLabelFor('PURCHASER')).toBe('采购员')
    expect(roleLabelFor('OPERATOR')).toBe('生产操作员')
    expect(roleLabelFor('QUALITY_INSPECTOR')).toBe('质检员')
    expect(roleLabelFor('ADMIN')).toBe('系统管理员')
    expect(roleLabelFor('GUEST')).toBe('用户')
  })

  it('does not grant sibling job permissions or unknown routes', () => {
    expect(canAccessRoute('PLANNER', 'purchasing')).toBe(false)
    expect(canAccessRoute('PURCHASER', 'inventory')).toBe(false)
    expect(canAccessRoute('APPROVER', 'quality')).toBe(false)
    expect(canAccessRoute('QUALITY_INSPECTOR', 'approvals')).toBe(false)
    expect(canAccessRoute('GUEST', 'dashboard')).toBe(false)
    expect(canAccessRoute('ADMIN', 'not-a-module')).toBe(false)
  })

  it('denies access for unknown roles', () => {
    expect(navigationForRole('GUEST')).toEqual([])
  })
})
