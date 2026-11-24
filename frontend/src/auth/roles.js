const ROLE_ACCESS = Object.freeze({
  dashboard: ['ADMIN', 'PLANNER', 'APPROVER', 'WAREHOUSE', 'PURCHASER', 'OPERATOR', 'QUALITY_INSPECTOR'],
  products: ['ADMIN', 'PLANNER'],
  orders: ['ADMIN', 'PLANNER'],
  approvals: ['ADMIN', 'APPROVER'],
  inventory: ['ADMIN', 'WAREHOUSE'],
  purchasing: ['ADMIN', 'PURCHASER'],
  mes: ['ADMIN', 'OPERATOR'],
  quality: ['ADMIN', 'QUALITY_INSPECTOR'],
})

const ROLE_LABELS = Object.freeze({
  ADMIN: '系统管理员',
  PLANNER: '计划员',
  APPROVER: '审批员',
  WAREHOUSE: '仓库员',
  PURCHASER: '采购员',
  OPERATOR: '生产操作员',
  QUALITY_INSPECTOR: '质检员',
})

const NAVIGATION = Object.freeze([
  { key: 'dashboard', label: '生产总览', path: '/dashboard', group: '工作台' },
  { key: 'products', label: '产品与 BOM', path: '/products', group: 'ERP' },
  { key: 'orders', label: '生产订单', path: '/orders', group: 'ERP' },
  { key: 'approvals', label: '审批中心', path: '/approvals', group: 'OA' },
  { key: 'inventory', label: '库存与备料', path: '/inventory', group: 'ERP' },
  { key: 'purchasing', label: '短缺采购', path: '/purchasing', group: 'ERP' },
  { key: 'mes', label: '报工工作台', path: '/mes', group: 'MES' },
  { key: 'quality', label: '质量检验', path: '/quality', group: 'QMS' },
])

export function canAccessRoute(role, routeKey) {
  return ROLE_ACCESS[routeKey]?.includes(role) ?? false
}

export function navigationForRole(role) {
  return NAVIGATION.filter((item) => canAccessRoute(role, item.key)).map((item) => ({ ...item }))
}

export function roleLabelFor(role) {
  return ROLE_LABELS[role] || '用户'
}
