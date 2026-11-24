<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  remaining: { type: Number, default: Number.MAX_SAFE_INTEGER },
  submitting: { type: Boolean, default: false },
})
const emit = defineEmits(['submit'])

const goodQuantity = ref('0')
const defectQuantity = ref('0')
const remark = ref('')

const quantities = computed(() => ({
  good: goodQuantity.value === '' ? 0 : Number(goodQuantity.value),
  defect: defectQuantity.value === '' ? 0 : Number(defectQuantity.value),
}))
const quantityError = computed(() => {
  const { good, defect } = quantities.value
  if (!Number.isInteger(good) || !Number.isInteger(defect) || good < 0 || defect < 0) {
    return '数量必须是大于或等于零的整数'
  }
  const total = good + defect
  if (total === 0) return '合格数和不合格数至少有一项必须大于零'
  if (total > props.remaining) return `本次报工数量超过剩余计划（还可报工 ${props.remaining} 件）`
  return ''
})

function submitReport() {
  if (quantityError.value || props.submitting) return
  emit('submit', {
    goodQuantity: quantities.value.good,
    defectQuantity: quantities.value.defect,
    remark: remark.value.trim(),
  })
}
</script>

<template>
  <form class="report-form" @submit.prevent="submitReport">
    <div class="report-quantities">
      <label class="report-field">
        <span>合格数量</span>
        <input
          v-model="goodQuantity"
          data-test="good-quantity"
          type="number"
          min="0"
          step="1"
          inputmode="numeric"
        />
      </label>
      <label class="report-field">
        <span>不合格数量</span>
        <input
          v-model="defectQuantity"
          data-test="defect-quantity"
          type="number"
          min="0"
          step="1"
          inputmode="numeric"
        />
      </label>
    </div>
    <label class="report-field report-remark">
      <span>报工备注 <small>选填</small></span>
      <input v-model="remark" data-test="remark" type="text" maxlength="500" placeholder="例如：首件检验通过" />
    </label>
    <p v-if="quantityError" class="report-error" data-test="quantity-error" role="alert">{{ quantityError }}</p>
    <button class="button button-primary report-submit" data-test="submit-report" type="submit" :disabled="Boolean(quantityError) || submitting" @click.prevent="submitReport">
      {{ submitting ? '正在提交…' : '提交报工' }}
    </button>
  </form>
</template>
