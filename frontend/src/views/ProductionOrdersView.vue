<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errorMessage } from '../api/client.js'
import { orderProgress } from '../utils/progress.js'
import { orderStatusLabel, orderStatusTone } from '../utils/labels.js'

const orders = ref([])
const products = ref([])
const loading = ref(true)
const error = ref('')
const dialog = ref(false)
const saving = ref(false)
const submittingId = ref(null)
const form = reactive({ productId: '', plannedQuantity: 1 })
const finishedProducts = computed(() => products.value.filter((product) => product.type === 'FINISHED' && product.enabled))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [orderRows, productRows] = await Promise.all([api.orders(), api.products()])
    orders.value = orderRows
    products.value = productRows
  } catch (err) {
    error.value = errorMessage(err, '无法读取生产订单')
  } finally {
    loading.value = false
  }
}

function openDialog() {
  Object.assign(form, { productId: finishedProducts.value[0]?.id || '', plannedQuantity: 1 })
  dialog.value = true
}

async function createOrder() {
  if (!form.productId || !Number.isInteger(Number(form.plannedQuantity)) || Number(form.plannedQuantity) <= 0) return
  saving.value = true
  try {
    await api.createOrder({ productId: Number(form.productId), plannedQuantity: Number(form.plannedQuantity) })
    dialog.value = false
    ElMessage.success('生产订单已创建为草稿')
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '创建生产订单失败'))
  } finally {
    saving.value = false
  }
}

async function submitForApproval(order) {
  submittingId.value = order.id
  try {
    await api.submitOrder(order.id)
    ElMessage.success('生产订单已提交 OA 审批')
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '提交审批失败'))
  } finally {
    submittingId.value = null
  }
}

function productName(order) {
  return order.productName || products.value.find((item) => item.id === order.productId)?.name || `产品 #${order.productId}`
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading">
      <div><span class="eyebrow">ERP · PRODUCTION PLANNING</span><h1>生产订单</h1><p>为启用的成品创建计划订单。提交后进入 OA 审批，只有获批订单才能进入 MES 开工。</p></div>
      <button class="button button-primary" type="button" :disabled="!finishedProducts.length" @click="openDialog"><span>＋</span>新建生产订单</button>
    </div>

    <section class="panel">
      <div class="panel-header"><div><h2>生产订单台账</h2><p>按最近创建时间排序 · 展示最新 100 条</p></div><span class="eyebrow">{{ orders.length }} ORDERS</span></div>
      <p v-if="error" class="panel-error" role="alert">{{ error }}</p>
      <div v-if="loading" class="panel-loading">正在加载生产订单…</div>
      <div v-else-if="!orders.length" class="panel-empty">暂无生产订单。先维护成品与 BOM，再创建计划订单。</div>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead><tr><th>订单号 / 产品</th><th>计划数量</th><th>报工进度</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="order in orders" :key="order.id">
              <td><span class="cell-primary">{{ order.orderNo }}</span><span class="cell-sub">{{ productName(order) }} · {{ order.productCode }}</span></td>
              <td>{{ order.plannedQuantity.toLocaleString('zh-CN') }}</td>
              <td><div class="progress-line"><div class="progress-track"><span :style="{ width: `${orderProgress(order)}%` }"></span></div><span class="progress-text">{{ orderProgress(order) }}%</span></div><span class="cell-sub">合格 {{ order.goodQuantity }} · 不合格 {{ order.defectQuantity }}</span></td>
              <td><span class="status-pill" :class="`tone-${orderStatusTone(order.status)}`">{{ orderStatusLabel(order.status) }}</span></td>
              <td><button v-if="order.status === 'DRAFT'" class="link-button" type="button" :disabled="submittingId === order.id" @click="submitForApproval(order)">{{ submittingId === order.id ? '提交中…' : '提交审批 →' }}</button><span v-else class="cell-sub">—</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <el-dialog v-model="dialog" title="新建生产订单" width="500px" destroy-on-close>
      <p class="cell-sub">订单会先保存为草稿，计划员可随后提交审批。</p>
      <div class="form-grid" style="margin-top: 18px">
        <label class="field" style="grid-column: 1 / -1"><span>成品</span><select v-model="form.productId"><option value="" disabled>选择已启用的成品</option><option v-for="product in finishedProducts" :key="product.id" :value="product.id">{{ product.code }} · {{ product.name }}</option></select></label>
        <label class="field"><span>计划数量</span><input v-model.number="form.plannedQuantity" type="number" min="1" step="1" /></label>
      </div>
      <p v-if="!finishedProducts.length" class="panel-error" style="margin-top: 14px">当前没有已启用的成品，请先到“产品与 BOM”创建。</p>
      <template #footer><button class="button button-secondary" type="button" @click="dialog = false">取消</button><button class="button button-primary" type="button" :disabled="saving || !form.productId || Number(form.plannedQuantity) <= 0" @click="createOrder">{{ saving ? '创建中…' : '创建草稿订单' }}</button></template>
    </el-dialog>
  </section>
</template>
