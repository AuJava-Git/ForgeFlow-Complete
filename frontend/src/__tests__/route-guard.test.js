import { describe, expect, it } from 'vitest'
import { routeAccessDecision } from '../auth/route-guard.js'

describe('route access decision', () => {
  it('redirects an unauthenticated request to login', () => {
    expect(routeAccessDecision(null, 'mes')).toBe('login')
  })

  it('redirects an authenticated operator away from approvals', () => {
    expect(routeAccessDecision({ accessToken: 'token', role: 'OPERATOR' }, 'approvals')).toBe('forbidden')
  })

  it('allows an operator into MES and an approver into approvals', () => {
    expect(routeAccessDecision({ accessToken: 'token', role: 'OPERATOR' }, 'mes')).toBe(true)
    expect(routeAccessDecision({ accessToken: 'token', role: 'APPROVER' }, 'approvals')).toBe(true)
  })

  it('sends an already authenticated user away from the login screen', () => {
    expect(routeAccessDecision({ accessToken: 'token', role: 'PLANNER' }, 'login')).toBe('dashboard')
  })
})
