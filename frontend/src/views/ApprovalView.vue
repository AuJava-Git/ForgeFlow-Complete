<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errorMessage } from '../api/client.js'
import { actionLabel, orderStatusLabel } from '../utils/labels.js'

const approvals = ref([])
const products = ref([])
const loading = ref(true)
const error = ref('')
const decisionDialog = ref(false)
const decisionOrder = ref(null)
const approved = ref(true)
const comment = ref('')
const saving = ref(false)
const historyDialog = ref(false)
const historyItems = ref([])
const historyLoading = ref(false)
const historyError = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [pending, productRows] = await Promise.all([api.pendingApprovals(), api.products()])
    approvals.value = pending
    products.value = productRows
  } catch (err) {
    error.value = errorMessage(err, '无法读取 OA 待审批任务')
  } finally {
    loading.value = false
  }
}

function productName(order) {
  return products.value.find((product) => product.id === order.productId)?.name || `产品 #${order.productId}`
}

function openDecision(order, decision) {
  decisionOrder.value = order
  approved.value = decision
  comment.value = ''
  decisionDialog.value = true
}

async function submitDecision() {
  saving.value = true
  try {
    await api.decideApproval(decisionOrder.value.id, { approved: approved.value, comment: comment.value.trim() || null })
    ElMessage.success(approved.value ? '订单已批准并进入 MES' : '订单已驳回')
    decisionDialog.value = false
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '审批操作失败'))
  } finally {
    saving.value = false
  }
}

async function openHistory(order) {
  historyDialog.value = true
  historyLoading.value = true
  historyError.value = ''
  historyItems.value = []
  try {
    historyItems.value = await api.approvalHistory(order.id)
  } catch (err) {
    historyError.value = errorMessage(err, '无法读取审批历史')
  } finally {
    historyLoading.value = false
  }
}

function formatTime(value) {
  if (!value) return '时间待同步'
  const date = new Date(value)
  return Number.isNaN(date.valueOf()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">OA · APPROVAL CENTER</span><h1>审批中心</h1><p>处理计划员提交的生产订单。每次提交、批准与驳回都会写入审批历史，保留操作人和意见。</p></div><span class="status-pill tone-amber">待处理 {{ approvals.length }}</span></div>
    <p v-if="error" class="panel-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="panel panel-loading">正在加载待审批任务…</div>
    <div v-else-if="!approvals.length" class="panel panel-empty">当前没有待审批订单。新的生产计划提交后会出现在这里。</div>
    <div v-else class="approval-list">
      <article v-for="order in approvals" :key="order.id" class="approval-card">
        <div class="card-topline"><div><h3 class="order-id">{{ order.orderNo }}</h3><p>{{ productName(order) }} · 生产订单 #{{ order.id }}</p></div><span class="status-pill tone-amber">{{ orderStatusLabel(order.status) }}</span></div>
        <div class="card-metrics"><span class="card-metric"><small>计划数量</small><strong>{{ order.plannedQuantity.toLocaleString('zh-CN') }} 件</strong></span><span class="card-metric"><small>关联产品 ID</small><strong>#{{ order.productId }}</strong></span><span class="card-metric"><small>审批流程</small><strong>一级审批</strong></span></div>
        <div class="card-actions"><button class="link-button" type="button" @click="openHistory(order)">查看审批历史 →</button><div class="card-action-group"><button class="button button-secondary" type="button" @click="openDecision(order, false)">驳回</button><button class="button button-primary" type="button" @click="openDecision(order, true)">批准订单</button></div></div>
      </article>
    </div>

    <el-dialog v-model="decisionDialog" :title="approved ? '批准生产订单' : '驳回生产订单'" width="520px" destroy-on-close>
      <p class="cell-sub">{{ decisionOrder?.orderNo }} · {{ approved ? '批准后订单将进入 MES 工作台' : '驳回后本 MVP 不支持重新提交' }}</p>
      <label class="field" style="margin-top: 18px"><span>审批意见 <small>选填，最多 500 字</small></span><textarea v-model="comment" rows="4" maxlength="500" placeholder="填写审批意见或驳回原因"></textarea></label>
      <template #footer><button class="button button-secondary" type="button" @click="decisionDialog = false">取消</button><button class="button" :class="approved ? 'button-primary' : 'button-danger'" type="button" :disabled="saving" @click="submitDecision">{{ saving ? '处理中…' : approved ? '确认批准' : '确认驳回' }}</button></template>
    </el-dialog>

    <el-dialog v-model="historyDialog" title="审批历史" width="560px">
      <div v-if="historyLoading" class="panel-loading">正在读取审批记录…</div>
      <p v-else-if="historyError" class="panel-error" role="alert">{{ historyError }}</p>
      <div v-else-if="!historyItems.length" class="panel-empty">尚无审批记录</div>
      <div v-else class="history-timeline"><article v-for="item in historyItems" :key="item.id" class="history-item"><strong>{{ actionLabel(item.actionCode) }}</strong><p>{{ item.actor }}<span v-if="item.comment"> · {{ item.comment }}</span></p><time>{{ formatTime(item.createdAt) }}</time></article></div>
    </el-dialog>
  </section>
</template>
