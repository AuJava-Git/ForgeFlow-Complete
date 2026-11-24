<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errorMessage } from '../api/client.js'

const pending = ref([])
const inspections = ref([])
const loading = ref(true)
const error = ref('')
const dialog = ref(false)
const saving = ref(false)
const receivingId = ref(null)
const selectedReport = ref(null)
const form = reactive({ passedQuantity: 0, rejectedQuantity: 0, remark: '' })
const quantityMatches = computed(() => selectedReport.value && Number(form.passedQuantity) >= 0 && Number(form.rejectedQuantity) >= 0 && Number(form.passedQuantity) + Number(form.rejectedQuantity) === selectedReport.value.goodQuantity)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [pendingRows, inspectionRows] = await Promise.all([api.qualityPending(), api.qualityInspections()])
    pending.value = pendingRows
    inspections.value = inspectionRows
  } catch (err) {
    error.value = errorMessage(err, '无法读取质检队列')
  } finally {
    loading.value = false
  }
}

function openInspection(report) {
  selectedReport.value = report
  Object.assign(form, { passedQuantity: report.goodQuantity, rejectedQuantity: 0, remark: '' })
  dialog.value = true
}

async function submitInspection() {
  if (!selectedReport.value || !quantityMatches.value) return
  saving.value = true
  try {
    const inspection = await api.inspectProductionReport(selectedReport.value.reportId, {
      passedQuantity: Number(form.passedQuantity),
      rejectedQuantity: Number(form.rejectedQuantity),
      remark: form.remark.trim() || null,
    })
    ElMessage.success(`质检已登记：通过 ${inspection.passedQuantity} 件，拒收 ${inspection.rejectedQuantity} 件`)
    dialog.value = false
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '质检登记失败'))
  } finally {
    saving.value = false
  }
}

async function receiveFinishedGoods(inspection) {
  receivingId.value = inspection.id
  try {
    await api.receiveFinishedGoods(inspection.id)
    ElMessage.success(`质检记录 #${inspection.id} 的通过数量已入成品库存`)
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '成品入库失败'))
  } finally {
    receivingId.value = null
  }
}

function resultLabel(result) {
  return result === 'PASS' ? '全数通过' : result === 'REJECTED' ? '全数拒收' : '部分通过'
}
function resultTone(result) {
  return result === 'PASS' ? 'tone-green' : result === 'REJECTED' ? 'tone-red' : 'tone-amber'
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">QMS · QUALITY ASSURANCE</span><h1>质量检验</h1><p>对 MES 报工中的合格产出进行检验，明确通过与拒收数量；只有检验通过数量可以转入成品库存。</p></div><span class="status-pill" :class="pending.length ? 'tone-amber' : 'tone-green'">待检 {{ pending.length }} 批</span></div>
    <p v-if="error" class="panel-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="panel panel-loading">正在加载质检队列…</div>
    <template v-else>
      <section class="panel" style="margin-bottom: 18px">
        <div class="panel-header"><div><h2>待检 MES 报工</h2><p>检验通过数 + 拒收数必须等于本批 MES 合格报工数</p></div><span class="eyebrow">INSPECTION QUEUE</span></div>
        <div v-if="!pending.length" class="panel-empty">当前没有待检报工。生产操作员提交 MES 报工后，含合格产出的批次会进入此处。</div>
        <div v-else class="table-wrap"><table class="data-table"><thead><tr><th>生产订单</th><th>MES 合格报工</th><th>MES 不合格报工</th><th>报工人</th><th>备注</th><th>操作</th></tr></thead><tbody><tr v-for="report in pending" :key="report.reportId"><td><span class="cell-primary">{{ report.orderNo }}</span><span class="cell-sub">订单 #{{ report.orderId }} · 报工批次 #{{ report.reportId }}</span></td><td>{{ report.goodQuantity }} 件</td><td>{{ report.defectQuantity }} 件</td><td>{{ report.operator || '—' }}</td><td>{{ report.remark || '—' }}</td><td><button class="button button-primary" type="button" @click="openInspection(report)">开始质检</button></td></tr></tbody></table></div>
      </section>

      <section class="panel">
        <div class="panel-header"><div><h2>近期质检与成品入库</h2><p>{{ inspections.length }} 条记录 · 成品库存只按通过数量入账</p></div><span class="eyebrow">INSPECTION RESULTS</span></div>
        <div v-if="!inspections.length" class="panel-empty">尚无质检记录。</div>
        <div v-else class="table-wrap"><table class="data-table"><thead><tr><th>质检记录</th><th>报工 / 订单</th><th>通过</th><th>拒收</th><th>结论</th><th>检验员</th><th>成品入库</th></tr></thead><tbody><tr v-for="inspection in inspections" :key="inspection.id"><td><span class="cell-primary">#{{ inspection.id }}</span></td><td>报工 #{{ inspection.reportId }}<span class="cell-sub">订单 #{{ inspection.orderId }}</span></td><td>{{ inspection.passedQuantity }} 件</td><td>{{ inspection.rejectedQuantity }} 件</td><td><span class="status-pill" :class="resultTone(inspection.result)">{{ resultLabel(inspection.result) }}</span></td><td>{{ inspection.inspector || '—' }}</td><td><span v-if="inspection.receivedToFinishedGoods" class="status-pill tone-green">已入库</span><button v-else-if="inspection.passedQuantity > 0" class="button button-secondary" type="button" :disabled="receivingId === inspection.id" @click="receiveFinishedGoods(inspection)">{{ receivingId === inspection.id ? '入库中…' : '入库通过数量' }}</button><span v-else class="cell-sub">无合格数量</span></td></tr></tbody></table></div>
      </section>
    </template>

    <el-dialog v-model="dialog" title="MES 报工批次质量检验" width="540px" destroy-on-close>
      <p class="cell-sub">订单 {{ selectedReport?.orderNo }} · 报工批次 #{{ selectedReport?.reportId }} · 待检合格报工 {{ selectedReport?.goodQuantity }} 件</p>
      <div class="form-grid" style="margin-top: 18px">
        <label class="field"><span>质检通过数量</span><input v-model.number="form.passedQuantity" type="number" min="0" step="1" /></label>
        <label class="field"><span>质检拒收数量</span><input v-model.number="form.rejectedQuantity" type="number" min="0" step="1" /></label>
        <label class="field" style="grid-column: 1 / -1"><span>检验备注（可选）</span><textarea v-model="form.remark" rows="3" maxlength="500" placeholder="缺陷类型、抽检结论或处置说明"></textarea></label>
      </div>
      <p v-if="selectedReport && !quantityMatches" class="panel-error" style="margin-top: 14px">通过数与拒收数之和必须等于 {{ selectedReport.goodQuantity }} 件。</p>
      <p class="cell-sub" style="margin-top: 13px">MES 已报不合格数量不在此处重复检验；此检验批次仅针对 MES 合格报工。</p>
      <template #footer><button class="button button-secondary" type="button" @click="dialog = false">取消</button><button class="button button-primary" type="button" :disabled="saving || !quantityMatches" @click="submitInspection">{{ saving ? '保存中…' : '保存质检结果' }}</button></template>
    </el-dialog>
  </section>
</template>
