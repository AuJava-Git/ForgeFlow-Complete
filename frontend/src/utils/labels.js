const ORDER_STATUS = {
  DRAFT: { label: '草稿', tone: 'neutral' },
  PENDING_APPROVAL: { label: '待审批', tone: 'amber' },
  APPROVED: { label: '已批准', tone: 'blue' },
  REJECTED: { label: '已驳回', tone: 'red' },
  IN_PROGRESS: { label: '生产中', tone: 'green' },
  COMPLETED: { label: '已完工', tone: 'ink' },
}

export function orderStatusLabel(status) {
  return ORDER_STATUS[status]?.label || status || '未知'
}

export function orderStatusTone(status) {
  return ORDER_STATUS[status]?.tone || 'neutral'
}

export function actionLabel(action) {
  return ({ SUBMIT: '提交审批', APPROVE: '批准订单', REJECT: '驳回订单' })[action] || action || '流程动作'
}

export function productTypeLabel(type) {
  return type === 'FINISHED' ? '成品' : type === 'MATERIAL' ? '原料' : type || '—'
}
