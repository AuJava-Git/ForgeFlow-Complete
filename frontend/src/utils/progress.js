export function orderProgress(order) {
  const planned = Number(order?.plannedQuantity || 0)
  const reported = Number(order?.goodQuantity || 0) + Number(order?.defectQuantity || 0)
  if (planned <= 0) return 0
  return Math.min(100, Math.max(0, Math.round((reported / planned) * 100)))
}
