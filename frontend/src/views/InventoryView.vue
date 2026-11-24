<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, errorMessage } from '../api/client.js'
import { canAccessRoute } from '../auth/roles.js'
import { readSession } from '../auth/session.js'

const router = useRouter()
const canOpenPurchasing = computed(() => canAccessRoute(readSession()?.role, 'purchasing'))
const balances = ref([])
const products = ref([])
const orders = ref([])
const selectedOrderId = ref('')
const requirements = ref([])
const loading = ref(true)
const requirementLoading = ref(false)
const error = ref('')
const action = ref('')
const receiptDialog = ref(false)
const receiptSaving = ref(false)
const receiptForm = reactive({ productId: '', quantity: 1, reason: '手工期初库存' })

const materials = computed(() => products.value.filter((product) => product.type === 'MATERIAL' && product.enabled))
const approvedOrders = computed(() => orders.value.filter((order) => order.status === 'APPROVED'))
const selectedOrder = computed(() => orders.value.find((order) => String(order.id) === String(selectedOrderId.value)))
const totalShortageLines = computed(() => requirements.value.filter((row) => Number(row.shortageQuantity) > 0).length)
const canReserve = computed(() => selectedOrder.value?.status === 'APPROVED' && selectedOrder.value.materialStatus === 'NOT_READY' && requirements.value.length > 0 && totalShortageLines.value === 0)
const canIssue = computed(() => selectedOrder.value?.status === 'APPROVED' && selectedOrder.value.materialStatus === 'RESERVED')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [balanceRows, productRows, orderRows] = await Promise.all([api.inventoryBalances(), api.products(), api.orders()])
    balances.value = balanceRows
    products.value = productRows
    orders.value = orderRows
    if (!selectedOrderId.value) {
      const candidate = orderRows.find((order) => order.status === 'APPROVED' && order.materialStatus !== 'ISSUED')
      if (candidate) selectedOrderId.value = String(candidate.id)
    }
    if (selectedOrderId.value) await loadRequirements(selectedOrderId.value)
  } catch (err) {
    error.value = errorMessage(err, '无法读取库存或生产订单')
  } finally {
    loading.value = false
  }
}

async function loadRequirements(orderId) {
  requirements.value = []
  if (!orderId) return
  requirementLoading.value = true
  try {
    requirements.value = await api.orderRequirements(Number(orderId))
  } catch (err) {
    error.value = errorMessage(err, '无法读取订单物料需求')
  } finally {
    requirementLoading.value = false
  }
}

async function selectOrder() {
  error.value = ''
  await loadRequirements(selectedOrderId.value)
}

async function performMaterialAction(kind) {
  if (!selectedOrder.value) return
  action.value = kind
  try {
    if (kind === 'reserve') await api.reserveOrderMaterials(selectedOrder.value.id)
    else await api.issueOrderMaterials(selectedOrder.value.id)
    ElMessage.success(kind === 'reserve' ? '已完成整单备料，库存已预留' : '领料完成，MES 可开始生产')
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, kind === 'reserve' ? '备料失败' : '领料失败'))
  } finally {
    action.value = ''
  }
}

function openReceipt() {
  Object.assign(receiptForm, { productId: materials.value[0]?.id || '', quantity: 1, reason: '手工期初库存' })
  receiptDialog.value = true
}

async function receiveOpeningStock() {
  if (!receiptForm.productId || Number(receiptForm.quantity) <= 0 || !receiptForm.reason.trim()) return
  receiptSaving.value = true
  try {
    await api.receiveOpeningStock({ productId: Number(receiptForm.productId), quantity: Number(receiptForm.quantity), reason: receiptForm.reason.trim() })
    receiptDialog.value = false
    ElMessage.success('原材料收货已登记并生成库存流水')
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '库存收货失败'))
  } finally {
    receiptSaving.value = false
  }
}

function quantity(value) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading">
      <div><span class="eyebrow">ERP · INVENTORY CONTROL</span><h1>库存与备料</h1><p>按生产订单计算物料需求并核对可用库存。备料是整单原子预留，领料后订单才可进入 MES 开工。</p></div>
      <button class="button button-primary" type="button" :disabled="!materials.length" @click="openReceipt">＋ 登记原料收货</button>
    </div>
    <p v-if="error" class="panel-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="panel panel-loading">正在读取库存与订单…</div>
    <template v-else>
      <section class="panel" style="margin-bottom: 18px">
        <div class="panel-header"><div><h2>生产订单物料需求</h2><p>需求量基于创建订单时冻结的 BOM 快照计算</p></div><span class="eyebrow">MATERIAL READINESS</span></div>
        <div class="panel-body">
          <div class="form-grid" style="align-items: end">
            <label class="field"><span>已批准生产订单</span><select v-model="selectedOrderId" @change="selectOrder"><option value="">选择订单</option><option v-for="order in approvedOrders" :key="order.id" :value="String(order.id)">{{ order.orderNo }} · {{ order.productName }} · {{ order.plannedQuantity }} 件</option></select></label>
            <div class="field"><span>物料状态</span><span class="status-pill" :class="selectedOrder?.materialStatus === 'ISSUED' ? 'tone-green' : selectedOrder?.materialStatus === 'RESERVED' ? 'tone-blue' : 'tone-amber'">{{ selectedOrder ? (selectedOrder.materialStatus === 'ISSUED' ? '已领料' : selectedOrder.materialStatus === 'RESERVED' ? '已备料' : '待备料') : '未选择订单' }}</span></div>
          </div>
          <div v-if="requirementLoading" class="panel-loading">正在计算物料可用量…</div>
          <div v-else-if="selectedOrderId && !requirements.length" class="panel-empty">没有物料快照。请确认该订单创建时成品已配置 BOM。</div>
          <div v-else-if="!selectedOrderId" class="panel-empty">当前没有可处理的已批准生产订单。</div>
          <div v-else class="table-wrap" style="margin-top: 17px">
            <table class="data-table">
              <thead><tr><th>物料</th><th>订单需求</th><th>已预留</th><th>已领料</th><th>可用库存</th><th>短缺</th></tr></thead>
              <tbody><tr v-for="row in requirements" :key="row.materialId"><td><span class="cell-primary">{{ row.name }}</span><span class="cell-sub">{{ row.code }}</span></td><td>{{ quantity(row.requiredQuantity) }} {{ row.unit }}</td><td>{{ quantity(row.reservedQuantity) }}</td><td>{{ quantity(row.issuedQuantity) }}</td><td>{{ quantity(row.availableQuantity) }}</td><td><span class="status-pill" :class="Number(row.shortageQuantity) > 0 ? 'tone-red' : 'tone-green'">{{ quantity(row.shortageQuantity) }}</span></td></tr></tbody>
            </table>
          </div>
          <div v-if="selectedOrder && requirements.length" class="work-order-footer">
            <span class="cell-sub">{{ totalShortageLines ? `${totalShortageLines} 项物料短缺；${canOpenPurchasing ? '可前往短缺采购补料' : '采购岗位会按缺口补料'}` : '所有 BOM 物料均满足本订单需求' }}</span>
            <div class="card-action-group">
              <button v-if="totalShortageLines && canOpenPurchasing" class="button button-secondary" type="button" @click="router.push('/purchasing')">前往短缺采购</button>
              <button v-if="canReserve" class="button button-primary" type="button" :disabled="!!action" @click="performMaterialAction('reserve')">{{ action === 'reserve' ? '备料中…' : '整单备料' }}</button>
              <button v-if="canIssue" class="button button-primary" type="button" :disabled="!!action" @click="performMaterialAction('issue')">{{ action === 'issue' ? '领料中…' : '确认领料' }}</button>
              <span v-if="selectedOrder.materialStatus === 'ISSUED'" class="status-pill tone-green">物料已发至产线，可进入 MES</span>
            </div>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header"><div><h2>库存余额</h2><p>{{ balances.length }} 项原料 / 成品库存 · 预留数量不计入可用库存</p></div><span class="eyebrow">ON-HAND / AVAILABLE</span></div>
        <div v-if="!balances.length" class="panel-empty">暂无库存余额。可登记原料期初收货，成品库存由质检通过后自动入库。</div>
        <div v-else class="table-wrap"><table class="data-table"><thead><tr><th>产品</th><th>类型</th><th>单位</th><th>现有库存</th><th>预留</th><th>可用</th></tr></thead><tbody><tr v-for="row in balances" :key="row.productId"><td><span class="cell-primary">{{ row.name }}</span><span class="cell-sub">{{ row.code }}</span></td><td>{{ row.productType === 'MATERIAL' ? '原材料' : '成品' }}</td><td>{{ row.unit }}</td><td>{{ quantity(row.onHandQuantity) }}</td><td>{{ quantity(row.reservedQuantity) }}</td><td><strong>{{ quantity(row.availableQuantity) }}</strong></td></tr></tbody></table></div>
      </section>
    </template>

    <el-dialog v-model="receiptDialog" title="登记原料收货" width="520px" destroy-on-close>
      <p class="cell-sub">用于模拟原料期初库存。正式采购到货请在“短缺采购”中登记，以关联采购单和库存流水。</p>
      <div class="form-grid" style="margin-top: 18px">
        <label class="field" style="grid-column: 1 / -1"><span>原材料</span><select v-model="receiptForm.productId"><option value="" disabled>选择原材料</option><option v-for="product in materials" :key="product.id" :value="product.id">{{ product.code }} · {{ product.name }}（{{ product.unit }}）</option></select></label>
        <label class="field"><span>收货数量</span><input v-model.number="receiptForm.quantity" type="number" min="0.0001" step="0.0001" /></label>
        <label class="field"><span>原因</span><input v-model="receiptForm.reason" maxlength="200" /></label>
      </div>
      <template #footer><button class="button button-secondary" type="button" @click="receiptDialog = false">取消</button><button class="button button-primary" type="button" :disabled="receiptSaving || !receiptForm.productId || Number(receiptForm.quantity) <= 0" @click="receiveOpeningStock">{{ receiptSaving ? '登记中…' : '确认收货' }}</button></template>
    </el-dialog>
  </section>
</template>
