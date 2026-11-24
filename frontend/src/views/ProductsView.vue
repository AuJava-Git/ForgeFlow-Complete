<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errorMessage } from '../api/client.js'
import { productTypeLabel } from '../utils/labels.js'

const products = ref([])
const loading = ref(true)
const error = ref('')
const savingProduct = ref(false)
const productDialog = ref(false)
const productForm = reactive({ code: '', name: '', type: 'FINISHED', unit: '件', enabled: true })
const bomDialog = ref(false)
const bomLoading = ref(false)
const bomSaving = ref(false)
const bomError = ref('')
const selectedProduct = ref(null)
const bomLines = ref([])
const materials = computed(() => products.value.filter((product) => product.type === 'MATERIAL' && product.enabled))
const finishedProducts = computed(() => products.value.filter((product) => product.type === 'FINISHED'))

async function load() {
  loading.value = true
  error.value = ''
  try {
    products.value = await api.products()
  } catch (err) {
    error.value = errorMessage(err, '无法读取产品主数据')
  } finally {
    loading.value = false
  }
}

function openProductDialog() {
  Object.assign(productForm, { code: '', name: '', type: 'FINISHED', unit: '件', enabled: true })
  productDialog.value = true
}

async function createProduct() {
  if (!productForm.code.trim() || !productForm.name.trim() || !productForm.unit.trim()) return
  savingProduct.value = true
  try {
    await api.createProduct({ ...productForm, code: productForm.code.trim(), name: productForm.name.trim(), unit: productForm.unit.trim() })
    productDialog.value = false
    ElMessage.success('产品主数据已创建')
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '创建产品失败'))
  } finally {
    savingProduct.value = false
  }
}

async function openBom(product) {
  selectedProduct.value = product
  bomLines.value = []
  bomError.value = ''
  bomDialog.value = true
  bomLoading.value = true
  try {
    const lines = await api.bom(product.id)
    bomLines.value = Array.isArray(lines) ? lines.map((line) => ({ ...line, quantity: String(line.quantity) })) : []
  } catch (err) {
    bomError.value = errorMessage(err, '无法读取 BOM 明细')
  } finally {
    bomLoading.value = false
  }
}

function addBomLine() {
  bomLines.value.push({ materialId: '', quantity: '' })
}

function removeBomLine(index) {
  bomLines.value.splice(index, 1)
}

async function saveBom() {
  if (!bomLines.value.length) {
    bomError.value = 'BOM 至少需要一条原料明细'
    return
  }
  const items = bomLines.value.map((line) => ({ materialId: Number(line.materialId), quantity: Number(line.quantity) }))
  if (items.some((item) => !item.materialId || !Number.isFinite(item.quantity) || item.quantity <= 0)) {
    bomError.value = '请选择原料，并填写大于零的单件用量'
    return
  }
  if (new Set(items.map((item) => item.materialId)).size !== items.length) {
    bomError.value = '同一个 BOM 中不能重复添加相同原料'
    return
  }
  bomSaving.value = true
  bomError.value = ''
  try {
    await api.replaceBom(selectedProduct.value.id, { items })
    ElMessage.success('BOM 已保存')
    bomDialog.value = false
  } catch (err) {
    bomError.value = errorMessage(err, '保存 BOM 失败')
  } finally {
    bomSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading">
      <div><span class="eyebrow">ERP · MASTER DATA</span><h1>产品与 BOM</h1><p>维护成品、原料主数据，并配置每种成品的物料清单。BOM 用量只用于业务演示，不进行库存扣减。</p></div>
      <button class="button button-primary" type="button" @click="openProductDialog"><span>＋</span>新建产品</button>
    </div>

    <section class="panel">
      <div class="panel-header"><div><h2>产品主数据</h2><p>{{ products.length }} 项 · 成品 {{ finishedProducts.length }} 项 · 原料 {{ materials.length }} 项</p></div><span class="eyebrow">PRODUCTS</span></div>
      <p v-if="error" class="panel-error" role="alert">{{ error }}</p>
      <div v-if="loading" class="panel-loading">正在加载产品…</div>
      <div v-else-if="!products.length" class="panel-empty">还没有产品主数据。先创建一个原料和一个成品，再为成品配置 BOM。</div>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead><tr><th>产品编码 / 名称</th><th>类型</th><th>计量单位</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="product in products" :key="product.id">
              <td><span class="cell-primary">{{ product.name }}</span><span class="cell-sub">{{ product.code }}</span></td>
              <td>{{ productTypeLabel(product.type) }}</td>
              <td>{{ product.unit }}</td>
              <td><span class="status-pill" :class="product.enabled ? 'tone-green' : 'tone-neutral'">{{ product.enabled ? '启用' : '停用' }}</span></td>
              <td><button v-if="product.type === 'FINISHED'" class="link-button" type="button" @click="openBom(product)">维护 BOM →</button><span v-else class="cell-sub">原料</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <el-dialog v-model="productDialog" title="新建产品主数据" width="520px" destroy-on-close>
      <div class="form-grid">
        <label class="field"><span>产品编码</span><input v-model="productForm.code" maxlength="64" placeholder="例如：FG-1001" /></label>
        <label class="field"><span>产品名称</span><input v-model="productForm.name" maxlength="120" placeholder="例如：智造控制器" /></label>
        <label class="field"><span>产品类型</span><select v-model="productForm.type"><option value="FINISHED">成品</option><option value="MATERIAL">原料</option></select></label>
        <label class="field"><span>计量单位</span><input v-model="productForm.unit" maxlength="16" placeholder="件" /></label>
      </div>
      <template #footer><button class="button button-secondary" type="button" @click="productDialog = false">取消</button><button class="button button-primary" type="button" :disabled="savingProduct" @click="createProduct">{{ savingProduct ? '保存中…' : '创建产品' }}</button></template>
    </el-dialog>

    <el-dialog v-model="bomDialog" :title="selectedProduct ? `维护 BOM · ${selectedProduct.code}` : '维护 BOM'" width="680px" destroy-on-close>
      <p class="cell-sub">配置生产 1 {{ selectedProduct?.unit || '件' }}成品需要的原料及用量。</p>
      <p v-if="bomError" class="panel-error" role="alert">{{ bomError }}</p>
      <div v-if="bomLoading" class="panel-loading">正在读取已保存的 BOM…</div>
      <template v-else>
        <div v-if="!materials.length" class="panel-empty">请先创建并启用至少一个原料产品。</div>
        <template v-else>
          <div v-for="(line, index) in bomLines" :key="`bom-${index}`" class="bom-line">
            <label class="field"><span>原料</span><select v-model="line.materialId"><option value="" disabled>选择原料</option><option v-for="material in materials" :key="material.id" :value="material.id">{{ material.code }} · {{ material.name }}</option></select></label>
            <label class="field"><span>单件用量</span><input v-model="line.quantity" type="number" min="0.0001" step="0.0001" placeholder="0.0000" /></label>
            <button class="icon-button" type="button" aria-label="删除原料行" @click="removeBomLine(index)">×</button>
          </div>
          <button class="button button-secondary" type="button" style="margin-top: 12px" @click="addBomLine">＋ 添加原料</button>
        </template>
      </template>
      <template #footer><button class="button button-secondary" type="button" @click="bomDialog = false">取消</button><button class="button button-primary" type="button" :disabled="bomLoading || bomSaving || !materials.length" @click="saveBom">{{ bomSaving ? '保存中…' : '保存 BOM' }}</button></template>
    </el-dialog>
  </section>
</template>
