import axios from 'axios'
import { clearSession, readSession } from '../auth/session.js'

export const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = readSession()?.accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use((response) => response, (error) => {
  if (error.response?.status === 401 && !String(error.config?.url || '').includes('/auth/login')) {
    clearSession()
    if (typeof window !== 'undefined') window.dispatchEvent(new Event('forgeflow:session-expired'))
  }
  return Promise.reject(error)
})

const data = (request) => request.then((response) => response.data)

export const api = {
  login: (credentials) => data(http.post('/auth/login', credentials)),
  dashboard: () => data(http.get('/dashboard/summary')),
  products: () => data(http.get('/erp/products')),
  createProduct: (payload) => data(http.post('/erp/products', payload)),
  bom: (productId) => data(http.get(`/erp/products/${productId}/bom`)),
  replaceBom: (productId, payload) => data(http.put(`/erp/products/${productId}/bom`, payload)),
  orders: () => data(http.get('/erp/production-orders')),
  createOrder: (payload) => data(http.post('/erp/production-orders', payload)),
  submitOrder: (orderId) => data(http.post(`/oa/production-orders/${orderId}/submit`)),
  pendingApprovals: () => data(http.get('/oa/production-orders/pending')),
  decideApproval: (orderId, payload) => data(http.post(`/oa/production-orders/${orderId}/decision`, payload)),
  approvalHistory: (orderId) => data(http.get(`/oa/production-orders/${orderId}/history`)),
  workOrders: () => data(http.get('/mes/work-orders')),
  startWorkOrder: (orderId) => data(http.post(`/mes/work-orders/${orderId}/start`)),
  reportWorkOrder: (orderId, payload) => data(http.post(`/mes/work-orders/${orderId}/reports`, payload)),
  inventoryBalances: () => data(http.get('/inventory/balances')),
  receiveOpeningStock: (payload) => data(http.post('/inventory/receipts', payload)),
  orderRequirements: (orderId) => data(http.get(`/inventory/production-orders/${orderId}/requirements`)),
  reserveOrderMaterials: (orderId) => data(http.post(`/inventory/production-orders/${orderId}/reserve`)),
  issueOrderMaterials: (orderId) => data(http.post(`/inventory/production-orders/${orderId}/issue`)),
  purchaseOrders: () => data(http.get('/purchasing/orders')),
  createShortageOrder: (orderId) => data(http.post(`/purchasing/production-orders/${orderId}/shortage-orders`)),
  receivePurchaseOrder: (purchaseOrderId, payload) => data(http.post(`/purchasing/orders/${purchaseOrderId}/receipts`, payload)),
  qualityPending: () => data(http.get('/quality/pending')),
  qualityInspections: () => data(http.get('/quality/inspections')),
  inspectProductionReport: (reportId, payload) => data(http.post(`/quality/production-reports/${reportId}/inspect`, payload)),
  receiveFinishedGoods: (inspectionId) => data(http.post(`/quality/inspections/${inspectionId}/finished-goods-receipt`)),
}

export function errorMessage(error, fallback = '请求失败，请稍后重试') {
  return error?.response?.data?.detail || error?.response?.data?.message || error?.message || fallback
}
