import { describe, expect, it, vi } from 'vitest'
import { loadWorkQueues } from '../dashboard/workbench.js'

function mockApi(overrides = {}) {
  return {
    orders: vi.fn(async () => []),
    pendingApprovals: vi.fn(async () => []),
    purchaseOrders: vi.fn(async () => []),
    workOrders: vi.fn(async () => []),
    qualityPending: vi.fn(async () => []),
    ...overrides,
  }
}

const order = (id, status, materialStatus = 'NOT_READY') => ({
  id,
  orderNo: `MO-${id}`,
  productName: '装配件',
  plannedQuantity: 20,
  status,
  materialStatus,
})

describe('role work queues', () => {
  it('calls only the API sources allowed for each operational role', async () => {
    const api = mockApi()
    for (const role of ['PLANNER', 'APPROVER', 'WAREHOUSE', 'PURCHASER', 'OPERATOR', 'QUALITY_INSPECTOR']) {
      await loadWorkQueues(role, api)
    }
    expect(api.orders).toHaveBeenCalledTimes(2)
    expect(api.pendingApprovals).toHaveBeenCalledTimes(1)
    expect(api.purchaseOrders).toHaveBeenCalledTimes(1)
    expect(api.workOrders).toHaveBeenCalledTimes(1)
    expect(api.qualityPending).toHaveBeenCalledTimes(1)
  })

  it('shows draft production orders as planner tasks', async () => {
    const api = mockApi({ orders: vi.fn(async () => [order(1, 'DRAFT'), order(2, 'PENDING_APPROVAL')]) })
    const [queue] = await loadWorkQueues('PLANNER', api)
    expect(queue).toMatchObject({ title: '计划待提交', count: 1, path: '/orders', actionLabel: '去提交' })
    expect(queue.rows.map((row) => row.id)).toEqual([1])
  })

  it('shows warehouse orders awaiting either reservation or issue', async () => {
    const api = mockApi({ orders: vi.fn(async () => [
      order(1, 'APPROVED', 'NOT_READY'),
      order(2, 'APPROVED', 'RESERVED'),
      order(3, 'APPROVED', 'ISSUED'),
      order(4, 'IN_PROGRESS', 'ISSUED'),
    ]) })
    const [queue] = await loadWorkQueues('WAREHOUSE', api)
    expect(queue.rows.map((row) => row.id)).toEqual([1, 2])
  })

  it('flattens each outstanding purchase line and skips fully received lines', async () => {
    const api = mockApi({ purchaseOrders: vi.fn(async () => [{
      id: 7,
      orderNo: 'PO-007',
      productionOrderId: 70,
      status: 'PARTIAL',
      lines: [
        { materialId: 2, materialName: '控制板', outstandingQuantity: 3, unit: '件' },
        { materialId: 3, materialName: '螺丝', outstandingQuantity: 0, unit: '袋' },
        { materialId: 4, materialName: '铝材', outstandingQuantity: 2, unit: '米' },
      ],
    }]) })
    const [queue] = await loadWorkQueues('PURCHASER', api)
    expect(queue.rows).toHaveLength(2)
    expect(queue.rows.map((row) => row.detail)).toEqual(['未到 3 件', '未到 2 米'])
    expect(queue.rows.map((row) => row.id)).toEqual(['7:2', '7:4'])
  })

  it('shows only approved or in-progress orders to operators and pending inspections to quality', async () => {
    const api = mockApi({
      workOrders: vi.fn(async () => [
        order(1, 'APPROVED', 'ISSUED'),
        order(2, 'IN_PROGRESS', 'ISSUED'),
        order(3, 'APPROVED', 'NOT_READY'),
        order(4, 'COMPLETED', 'ISSUED'),
      ]),
      qualityPending: vi.fn(async () => [{ reportId: 50, orderId: 3, orderNo: 'MO-3', goodQuantity: 12, defectQuantity: 1, operator: 'demo-operator' }]),
    })
    const [mesQueue] = await loadWorkQueues('OPERATOR', api)
    const [qualityQueue] = await loadWorkQueues('QUALITY_INSPECTOR', api)
    expect(mesQueue.rows.map((row) => row.id)).toEqual([1, 2])
    expect(qualityQueue.rows[0]).toMatchObject({ id: 50, primary: 'MO-3', detail: '合格 12 · 不合格 1 · demo-operator' })
  })

  it('keeps an empty role queue visible and aggregates all six queues for admin', async () => {
    const api = mockApi()
    const [emptyQueue] = await loadWorkQueues('PLANNER', api)
    expect(emptyQueue).toMatchObject({ count: 0, rows: [], path: '/orders' })
    const adminQueues = await loadWorkQueues('ADMIN', api)
    expect(adminQueues).toHaveLength(6)
    expect(api.orders).toHaveBeenCalledTimes(2)
    expect(api.pendingApprovals).toHaveBeenCalledTimes(1)
    expect(api.purchaseOrders).toHaveBeenCalledTimes(1)
    expect(api.workOrders).toHaveBeenCalledTimes(1)
    expect(api.qualityPending).toHaveBeenCalledTimes(1)
  })

  it('returns no queues and calls no business API for an unknown role', async () => {
    const api = mockApi()
    expect(await loadWorkQueues('GUEST', api)).toEqual([])
    expect(Object.values(api).every((method) => method.mock.calls.length === 0)).toBe(true)
  })
})
