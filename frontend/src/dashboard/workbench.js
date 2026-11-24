const ROLE_QUEUE_LOADERS = {
  PLANNER: (api) => api.orders().then((orders) => [plannerQueue(orders)]),
  APPROVER: (api) => api.pendingApprovals().then((orders) => [approvalQueue(orders)]),
  WAREHOUSE: (api) => api.orders().then((orders) => [warehouseQueue(orders)]),
  PURCHASER: (api) => api.purchaseOrders().then((orders) => [purchasingQueue(orders)]),
  OPERATOR: (api) => api.workOrders().then((orders) => [mesQueue(orders)]),
  QUALITY_INSPECTOR: (api) => api.qualityPending().then((reports) => [qualityQueue(reports)]),
}

const orderRow = (order, detail, status) => ({
  id: order.id,
  primary: order.orderNo,
  detail,
  status,
})

function plannerQueue(orders) {
  const rows = orders
    .filter((order) => order.status === 'DRAFT')
    .map((order) => orderRow(order, `${order.productName || `产品 #${order.productId}`} · 计划 ${order.plannedQuantity} 件`, '待提交'))
  return queue('计划待提交', rows, '/orders', '去提交')
}

function approvalQueue(orders) {
  const rows = orders.map((order) => orderRow(order, `计划 ${order.plannedQuantity} 件 · 产品 #${order.productId}`, '待审批'))
  return queue('待我审批', rows, '/approvals', '去审批')
}

function warehouseQueue(orders) {
  const rows = orders
    .filter((order) => order.status === 'APPROVED' && order.materialStatus !== 'ISSUED')
    .map((order) => orderRow(
      order,
      `${order.productName || `产品 #${order.productId}`} · ${order.materialStatus === 'RESERVED' ? '已备料，待领料' : '待核料 / 备料'}`,
      order.materialStatus === 'RESERVED' ? '待领料' : '待备料',
    ))
  return queue('待备料 / 待领料', rows, '/inventory', '去处理')
}

function purchasingQueue(orders) {
  const rows = orders.flatMap((order) => (order.lines || [])
    .filter((line) => Number(line.outstandingQuantity) > 0)
    .map((line) => ({
      id: `${order.id}:${line.materialId}`,
      primary: `${order.orderNo} · ${line.materialName}`,
      detail: `未到 ${quantity(line.outstandingQuantity)} ${line.unit}`,
      status: order.status === 'PARTIAL' ? '部分到货' : '待到货',
    })))
  return queue('补料待到货', rows, '/purchasing', '登记到货')
}

function mesQueue(orders) {
  const rows = orders
    .filter((order) => order.status === 'IN_PROGRESS'
      || (order.status === 'APPROVED' && order.materialStatus === 'ISSUED'))
    .map((order) => orderRow(
      order,
      `计划 ${order.plannedQuantity} 件 · 已报合格 ${order.goodQuantity} / 不合格 ${order.defectQuantity}`,
      order.status === 'APPROVED' ? '待开工' : '生产中',
    ))
  return queue('待开工 / 待报工', rows, '/mes', '去执行')
}

function qualityQueue(reports) {
  const rows = reports.map((report) => ({
    id: report.reportId,
    primary: report.orderNo,
    detail: `合格 ${report.goodQuantity} · 不合格 ${report.defectQuantity} · ${report.operator}`,
    status: '待检验',
  }))
  return queue('待质量检验', rows, '/quality', '去检验')
}

function queue(title, rows, path, actionLabel) {
  return { title, count: rows.length, rows, path, actionLabel }
}

function quantity(value) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

export async function loadWorkQueues(role, api) {
  if (role === 'ADMIN') {
    const [orders, approvals, purchases, workOrders, inspections] = await Promise.all([
      api.orders(),
      api.pendingApprovals(),
      api.purchaseOrders(),
      api.workOrders(),
      api.qualityPending(),
    ])
    return [
      plannerQueue(orders),
      approvalQueue(approvals),
      warehouseQueue(orders),
      purchasingQueue(purchases),
      mesQueue(workOrders),
      qualityQueue(inspections),
    ]
  }
  return ROLE_QUEUE_LOADERS[role]?.(api) ?? []
}
