<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errorMessage } from '../api/client.js'
import { readSession } from '../auth/session.js'
import { loadWorkQueues } from '../dashboard/workbench.js'
import { roleLabelFor } from '../auth/roles.js'

const summary = ref(null)
const queues = ref([])
const loading = ref(true)
const queueLoading = ref(true)
const error = ref('')
const queueError = ref('')
const currentRole = computed(() => readSession()?.role)
const currentRoleLabel = computed(() => roleLabelFor(currentRole.value))
const metrics = computed(() => {
  const data = summary.value || {}
  return [
    { label: '生产订单总数', value: data.totalOrders ?? 0, caption: '全部状态 · 累计建单', symbol: '01' },
    { label: '待审批订单', value: data.pendingApproval ?? 0, caption: '等待 OA 审批处理', symbol: '02' },
    { label: '生产中订单', value: data.inProgress ?? 0, caption: 'MES 正在执行', symbol: '03' },
    { label: '已完工订单', value: data.completed ?? 0, caption: '已达到计划数量', symbol: '04' },
  ]
})
const reportedTotal = computed(() => Number(summary.value?.goodQuantity || 0) + Number(summary.value?.defectQuantity || 0))
const yieldRate = computed(() => reportedTotal.value ? Math.round((Number(summary.value?.goodQuantity || 0) / reportedTotal.value) * 100) : null)
const totalOpenTasks = computed(() => queues.value.reduce((total, queue) => total + queue.count, 0))

async function loadSummary() {
  loading.value = true
  error.value = ''
  try {
    summary.value = await api.dashboard()
  } catch (err) {
    error.value = errorMessage(err, '仪表盘数据暂时不可用')
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

async function loadQueues() {
  queueLoading.value = true
  queueError.value = ''
  try {
    queues.value = await loadWorkQueues(currentRole.value, api)
  } catch (err) {
    queueError.value = errorMessage(err, '岗位待办暂时不可用')
  } finally {
    queueLoading.value = false
  }
}

onMounted(() => {
  void loadSummary()
  void loadQueues()
})
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <span class="eyebrow">MANUFACTURING OVERVIEW</span>
        <h1>生产总览</h1>
        <p>从订单计划到产线执行，关键状态一屏掌握。指标由业务数据库实时汇总，不使用静态演示数字。</p>
      </div>
      <span class="status-pill tone-green">{{ loading ? '正在同步' : '数据已同步' }}</span>
    </div>

    <p v-if="error" class="panel-error" role="alert">{{ error }}</p>
    <div class="metric-grid" :aria-busy="loading">
      <article v-for="metric in metrics" :key="metric.symbol" class="metric-card">
        <div class="metric-top"><span class="metric-label">{{ metric.label }}</span><span class="metric-mark" aria-hidden="true"></span></div>
        <div class="metric-value">{{ loading ? '—' : Number(metric.value).toLocaleString('zh-CN') }}</div>
        <div class="metric-caption">{{ metric.caption }}</div>
      </article>
    </div>

    <section class="panel dashboard-workbench">
      <div class="panel-header">
        <div><h2>我的待办</h2><p>{{ currentRoleLabel }} · {{ queueLoading ? '正在读取岗位任务' : `当前 ${totalOpenTasks} 项待处理` }}</p></div>
        <span class="eyebrow">ROLE WORKBENCH</span>
      </div>
      <p v-if="queueError" class="panel-error" role="alert">{{ queueError }}</p>
      <div v-if="queueLoading" class="panel-loading">正在读取你的待办…</div>
      <div v-else-if="!queues.length" class="panel-empty">当前账号没有可展示的岗位待办。</div>
      <div v-else class="workbench-queues">
        <section v-for="queue in queues" :key="queue.title" class="work-queue">
          <div class="work-queue-heading">
            <h3>{{ queue.title }}</h3>
            <span class="queue-count">{{ queue.count }}</span>
          </div>
          <div v-if="!queue.rows.length" class="queue-empty">暂时没有需要处理的记录</div>
          <div v-else class="table-wrap">
            <table class="data-table work-queue-table">
              <thead><tr><th>业务记录</th><th>当前状态</th><th>入口</th></tr></thead>
              <tbody>
                <tr v-for="row in queue.rows" :key="row.id">
                  <td><span class="cell-primary">{{ row.primary }}</span><span class="cell-sub">{{ row.detail }}</span></td>
                  <td><span class="status-pill tone-amber">{{ row.status }}</span></td>
                  <td><RouterLink class="link-button" :to="queue.path">{{ queue.actionLabel }} →</RouterLink></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </section>

    <div class="dashboard-lower">
      <section class="panel">
        <div class="panel-header"><div><h2>端到端业务闭环</h2><p>一张生产订单贯穿多个岗位与业务模块</p></div><span class="eyebrow">ERP → OA → MES → QMS</span></div>
        <div class="panel-body workflow-steps">
          <div class="workflow-step"><span class="step-number">01</span><span class="step-copy"><strong>计划与主数据</strong><small>维护产品、原料与 BOM，创建生产订单</small></span><span class="step-module">ERP</span></div>
          <div class="workflow-step"><span class="step-number">02</span><span class="step-copy"><strong>订单审批与备料</strong><small>审批留痕，仓库按订单核料、备料和领料</small></span><span class="step-module">OA · WMS</span></div>
          <div class="workflow-step"><span class="step-number">03</span><span class="step-copy"><strong>短缺补料与生产</strong><small>采购仅补 BOM 缺料，领料完成后由产线分批报工</small></span><span class="step-module">ERP · MES</span></div>
          <div class="workflow-step"><span class="step-number">04</span><span class="step-copy"><strong>质量检验与入库</strong><small>质检确认报工结果，合格数量转入成品库存</small></span><span class="step-module">QMS</span></div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-header"><div><h2>产出质量</h2><p>累计 MES 报工明细</p></div><span class="eyebrow">REPORTING</span></div>
        <div class="panel-body">
          <div v-if="loading" class="panel-loading">正在加载报工数据…</div>
          <template v-else>
            <div class="quality-row"><span class="quality-name"><i></i>合格数量</span><strong class="quality-value">{{ Number(summary?.goodQuantity || 0).toLocaleString('zh-CN') }}</strong></div>
            <div class="quality-row defect"><span class="quality-name"><i></i>不合格数量</span><strong class="quality-value">{{ Number(summary?.defectQuantity || 0).toLocaleString('zh-CN') }}</strong></div>
            <div class="mini-progress" role="progressbar" :aria-valuenow="yieldRate ?? 0" aria-valuemin="0" aria-valuemax="100"><span :style="{ width: `${yieldRate ?? 0}%` }"></span></div>
            <div class="metric-caption">{{ yieldRate === null ? '尚无报工记录' : `累计报工良率 ${yieldRate}%` }}</div>
          </template>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.dashboard-workbench { margin-top: 20px; }
.workbench-queues { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; padding: 0 18px 18px; }
.work-queue { min-width: 0; border: 1px solid #e8edf1; border-radius: 10px; overflow: hidden; }
.work-queue-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 13px 15px; background: #fbfcfa; border-bottom: 1px solid #e8edf1; }
.work-queue-heading h3 { margin: 0; color: #23354a; font-size: 14px; }
.queue-count { display: inline-flex; align-items: center; justify-content: center; min-width: 27px; height: 27px; padding: 0 7px; border-radius: 999px; color: #305527; background: #e9f5dc; font-weight: 700; font-size: 12px; }
.queue-empty { padding: 18px 15px; color: #7b8792; font-size: 12px; }
.work-queue-table th:last-child, .work-queue-table td:last-child { white-space: nowrap; }
@media (max-width: 900px) { .workbench-queues { grid-template-columns: 1fr; } }
</style>
