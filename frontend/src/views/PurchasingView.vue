<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errorMessage } from '../api/client.js'

const orders = ref([])
const purchaseOrders = ref([])
const selectedOrderId = ref('')
const selectedPurchaseOrderId = ref('')
const loading = ref(true)
const savingOrder = ref(false)
const receivingId = ref(null)
const error = ref('')
const receiptQuantities = reactive({})

const approvedOrders = computed(() => orders.value.filter((order) => order.status === 'APPROVED'))
const selectedOrder = computed(() => orders.value.find((order) => String(order.id) === String(selectedOrderId.value)))
const selectedPurchaseOrder = computed(() => purchaseOrders.value.find((order) => String(order.id) === String(selectedPurchaseOrderId.value)))
const existingOrderForProduction = computed(() => purchaseOrders.value.find((order) => String(order.productionOrderId) === String(selectedOrderId.value)))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [orderRows, purchaseRows] = await Promise.all([api.orders(), api.purchaseOrders()])
    orders.value = orderRows
    purchaseOrders.value = purchaseRows
    if (!selectedOrderId.value) {
      const candidate = orderRows.find((order) => order.status === 'APPROVED')
      if (candidate) selectedOrderId.value = String(candidate.id)
    }
    if (!selectedPurchaseOrderId.value && purchaseRows.length) selectedPurchaseOrderId.value = String(purchaseRows[0].id)
    if (selectedPurchaseOrder.value) resetReceiptQuantities(selectedPurchaseOrder.value)
  } catch (err) {
    error.value = errorMessage(err, '无法读取生产订单或采购单')
  } finally {
    loading.value = false
  }
}

function resetReceiptQuantities(order) {
  Object.keys(receiptQuantities).forEach((key) => delete receiptQuantities[key])
  order?.lines?.forEach((line) => { receiptQuantities[line.materialId] = '' })
}

function selectPurchaseOrder() {
  resetReceiptQuantities(selectedPurchaseOrder.value)
}

async function createShortageOrder() {
  if (!selectedOrder.value) return
  savingOrder.value = true
  try {
    const created = await api.createShortageOrder(selectedOrder.value.id)
    ElMessage.success(`已生成补料采购单 ${created.orderNo}`)
    await load()
    selectedPurchaseOrderId.value = String(created.id)
    resetReceiptQuantities(created)
  } catch (err) {
    ElMessage.error(errorMessage(err, '生成短缺采购单失败'))
  } finally {
    savingOrder.value = false
  }
}

async function receive(purchaseOrder) {
  const items = purchaseOrder.lines
    .map((line) => ({ materialId: Number(line.materialId), quantity: Number(receiptQuantities[line.materialId] || 0) }))
    .filter((line) => line.quantity > 0)
  if (!items.length) {
    ElMessage.warning('请至少填写一项大于零的到货数量')
    return
  }
  receivingId.value = purchaseOrder.id
  try {
    const updated = await api.receivePurchaseOrder(purchaseOrder.id, { items })
    ElMessage.success(updated.status === 'RECEIVED' ? '采购单已全部到货，原料库存已更新' : '本次到货已登记，采购单为部分到货')
    await load()
    selectedPurchaseOrderId.value = String(updated.id)
    resetReceiptQuantities(updated)
  } catch (err) {
    ElMessage.error(errorMessage(err, '登记到货失败'))
  } finally {
    receivingId.value = null
  }
}

function statusLabel(status) {
  return status === 'RECEIVED' ? '已到齐' : status === 'PARTIAL' ? '部分到货' : '待到货'
}
function statusTone(status) {
  return status === 'RECEIVED' ? 'tone-green' : status === 'PARTIAL' ? 'tone-blue' : 'tone-amber'
}
function quantity(value) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">ERP · SHORTAGE PROCUREMENT</span><h1>短缺采购</h1><p>仅针对已批准生产订单中未被可用库存覆盖的物料生成补料单。登记到货后会同步更新原料库存与采购单数量。</p></div><span class="status-pill tone-blue">订单驱动 · 按短缺补料</span></div>
    <p v-if="error" class="panel-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="panel panel-loading">正在加载采购台账…</div>
    <template v-else>
      <section class="panel" style="margin-bottom: 18px">
        <div class="panel-header"><div><h2>为生产订单补料</h2><p>系统按订单冻结的 BOM 需求减去当前可用库存计算采购数量</p></div><span class="eyebrow">CREATE FROM SHORTAGE</span></div>
        <div class="panel-body">
          <div class="form-grid" style="align-items: end">
            <label class="field"><span>已批准生产订单</span><select v-model="selectedOrderId"><option value="">选择订单</option><option v-for="order in approvedOrders" :key="order.id" :value="String(order.id)">{{ order.orderNo }} · {{ order.productName }} · {{ order.plannedQuantity }} 件</option></select></label>
            <div class="field"><span>该订单已有补料单</span><span v-if="existingOrderForProduction" class="status-pill tone-blue">{{ existingOrderForProduction.orderNo }} · {{ statusLabel(existingOrderForProduction.status) }}</span><span v-else class="cell-sub">尚未创建</span></div>
          </div>
          <div class="work-order-footer"><span class="cell-sub">不会采购 BOM 需求以外或库存已充足的数量；每个生产订单仅可生成一张短缺补料单。</span><button class="button button-primary" type="button" :disabled="savingOrder || !selectedOrder || !!existingOrderForProduction" @click="createShortageOrder">{{ savingOrder ? '计算并创建中…' : '计算短缺并生成采购单' }}</button></div>
        </div>
      </section>

      <section class="panel" style="margin-bottom: 18px">
        <div class="panel-header"><div><h2>采购单台账</h2><p>{{ purchaseOrders.length }} 张 · 到货状态和每行未到数量实时更新</p></div><span class="eyebrow">PURCHASE ORDERS</span></div>
        <div v-if="!purchaseOrders.length" class="panel-empty">还没有补料采购单。选择已批准且物料存在短缺的生产订单生成。</div>
        <div v-else class="table-wrap"><table class="data-table"><thead><tr><th>采购单号</th><th>关联生产订单</th><th>采购物料</th><th>采购量</th><th>已到货</th><th>未到</th><th>状态</th></tr></thead><tbody><template v-for="order in purchaseOrders" :key="order.id"><tr v-for="(line, index) in order.lines" :key="`${order.id}-${line.materialId}`"><td v-if="index === 0" :rowspan="order.lines.length"><button class="link-button" type="button" @click="selectedPurchaseOrderId = String(order.id); selectPurchaseOrder()">{{ order.orderNo }} →</button></td><td v-if="index === 0" :rowspan="order.lines.length">生产订单 #{{ order.productionOrderId }}</td><td><span class="cell-primary">{{ line.materialName }}</span><span class="cell-sub">{{ line.materialCode }}</span></td><td>{{ quantity(line.orderedQuantity) }} {{ line.unit }}</td><td>{{ quantity(line.receivedQuantity) }}</td><td>{{ quantity(line.outstandingQuantity) }}</td><td v-if="index === 0" :rowspan="order.lines.length"><span class="status-pill" :class="statusTone(order.status)">{{ statusLabel(order.status) }}</span></td></tr></template></tbody></table></div>
      </section>

      <section v-if="selectedPurchaseOrder" class="panel">
        <div class="panel-header"><div><h2>登记采购到货 · {{ selectedPurchaseOrder.orderNo }}</h2><p>可分批到货；数量不能超过采购单未到余额</p></div><span class="status-pill" :class="statusTone(selectedPurchaseOrder.status)">{{ statusLabel(selectedPurchaseOrder.status) }}</span></div>
        <div class="panel-body">
          <div v-if="selectedPurchaseOrder.status === 'RECEIVED'" class="panel-empty">采购单已全部到货，重复收货已关闭。</div>
          <template v-else>
            <div class="table-wrap"><table class="data-table"><thead><tr><th>原料</th><th>采购量</th><th>已到货</th><th>剩余可收</th><th>本次到货数量</th></tr></thead><tbody><tr v-for="line in selectedPurchaseOrder.lines" :key="line.materialId"><td><span class="cell-primary">{{ line.materialName }}</span><span class="cell-sub">{{ line.materialCode }}</span></td><td>{{ quantity(line.orderedQuantity) }} {{ line.unit }}</td><td>{{ quantity(line.receivedQuantity) }}</td><td>{{ quantity(line.outstandingQuantity) }}</td><td><input v-model.number="receiptQuantities[line.materialId]" class="inline-number" type="number" min="0" :max="line.outstandingQuantity" step="0.0001" :placeholder="`最多 ${quantity(line.outstandingQuantity)}`" /></td></tr></tbody></table></div>
            <div class="field-actions"><button class="button button-primary" type="button" :disabled="receivingId === selectedPurchaseOrder.id" @click="receive(selectedPurchaseOrder)">{{ receivingId === selectedPurchaseOrder.id ? '登记中…' : '确认本次到货' }}</button></div>
          </template>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.inline-number { width: 150px; min-height: 35px; padding: 0 9px; border: 1px solid #dfe5ea; border-radius: 7px; color: #23354a; background: #fff; font: inherit; font-size: 12px; }
.inline-number:focus { outline: 2px solid rgba(169,223,114,.35); border-color: #a6cf7b; }
</style>
