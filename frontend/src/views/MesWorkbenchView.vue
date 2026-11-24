<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ReportForm from '../components/ReportForm.vue'
import { api, errorMessage } from '../api/client.js'
import { orderProgress, } from '../utils/progress.js'
import { orderStatusLabel, orderStatusTone } from '../utils/labels.js'

const workOrders = ref([])
const products = ref([])
const loading = ref(true)
const error = ref('')
const startingId = ref(null)
const reportDialog = ref(false)
const selectedOrder = ref(null)
const reportSaving = ref(false)
const remaining = computed(() => selectedOrder.value ? Math.max(0, selectedOrder.value.plannedQuantity - selectedOrder.value.goodQuantity - selectedOrder.value.defectQuantity) : 0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [rows, productRows] = await Promise.all([api.workOrders(), api.products()])
    workOrders.value = rows
    products.value = productRows
  } catch (err) {
    error.value = errorMessage(err, '无法读取 MES 工作订单')
  } finally {
    loading.value = false
  }
}

function productName(order) {
  return products.value.find((product) => product.id === order.productId)?.name || `产品 #${order.productId}`
}

async function start(order) {
  startingId.value = order.id
  try {
    await api.startWorkOrder(order.id)
    ElMessage.success(`${order.orderNo} 已开工`)
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '开工失败'))
  } finally {
    startingId.value = null
  }
}

function openReport(order) {
  selectedOrder.value = order
  reportDialog.value = true
}

async function submitReport(payload) {
  reportSaving.value = true
  try {
    const result = await api.reportWorkOrder(selectedOrder.value.id, payload)
    ElMessage.success(result.status === 'COMPLETED' ? '计划数量已达成，订单已完工' : '本批报工已登记')
    reportDialog.value = false
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '报工失败'))
  } finally {
    reportSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">MES · SHOP FLOOR</span><h1>报工工作台</h1><p>只有获批订单可以开工。生产中订单通过分批报工更新累计产量，报工总量不能超过计划数量。</p></div><span class="status-pill tone-green">执行中 {{ workOrders.filter((order) => order.status === 'IN_PROGRESS').length }}</span></div>
    <p v-if="error" class="panel-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="panel panel-loading">正在加载 MES 工作订单…</div>
    <div v-else-if="!workOrders.length" class="panel panel-empty">当前没有可执行订单。请先由审批员批准生产订单。</div>
    <div v-else class="work-order-list">
      <article v-for="order in workOrders" :key="order.id" class="work-order-card">
        <div class="card-topline"><div><h3 class="order-id">{{ order.orderNo }}</h3><p>{{ productName(order) }} · 产品 ID {{ order.productId }}</p></div><span class="status-pill" :class="`tone-${orderStatusTone(order.status)}`">{{ orderStatusLabel(order.status) }}</span></div>
        <div class="card-metrics"><span class="card-metric"><small>计划数量</small><strong>{{ order.plannedQuantity.toLocaleString('zh-CN') }} 件</strong></span><span class="card-metric"><small>合格</small><strong>{{ order.goodQuantity }}</strong></span><span class="card-metric"><small>不合格</small><strong>{{ order.defectQuantity }}</strong></span><span class="card-metric"><small>剩余计划</small><strong>{{ Math.max(0, order.plannedQuantity - order.goodQuantity - order.defectQuantity) }}</strong></span></div>
        <div class="progress-line"><div class="progress-track"><span :style="{ width: `${orderProgress(order)}%` }"></span></div><span class="progress-text">{{ orderProgress(order) }}% 已报工</span></div>
        <div class="work-order-footer"><span class="cell-sub">报工累计（合格 + 不合格）不可超过计划数量</span><button v-if="order.status === 'APPROVED'" class="button button-primary" type="button" :disabled="startingId === order.id" @click="start(order)">{{ startingId === order.id ? '开工中…' : '开工' }}</button><button v-else class="button button-primary" type="button" :disabled="orderProgress(order) >= 100" @click="openReport(order)">分批报工 →</button></div>
      </article>
    </div>

    <el-dialog v-model="reportDialog" title="MES 分批报工" width="520px" destroy-on-close>
      <p class="cell-sub">{{ selectedOrder?.orderNo }} · {{ productName(selectedOrder || {}) }} · 剩余计划 {{ remaining }} 件</p>
      <div class="panel" style="margin-top: 17px; padding: 17px"><ReportForm :remaining="remaining" :submitting="reportSaving" @submit="submitReport" /></div>
    </el-dialog>
  </section>
</template>
