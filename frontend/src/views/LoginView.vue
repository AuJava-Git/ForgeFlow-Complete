<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, errorMessage } from '../api/client.js'
import { saveSession } from '../auth/session.js'

const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function signIn() {
  if (!username.value.trim() || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const response = await api.login({ username: username.value.trim(), password: password.value })
    saveSession(response)
    ElMessage.success(`欢迎回来，${response.displayName}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } catch (err) {
    error.value = errorMessage(err, '登录失败，请检查演示账号')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-screen">
    <section class="login-panel">
      <div class="login-form-side">
        <RouterLink class="login-brand" to="/login">
          <span class="brand-mark">F</span>
          <span><strong>ForgeFlow</strong><small>智造协同平台</small></span>
        </RouterLink>
        <div class="login-heading">
          <span class="eyebrow">欢迎使用 ForgeFlow</span>
          <h1>让生产协同<br /><em>清晰、有序、可追溯。</em></h1>
          <p>使用本地演示账号登录，体验从 ERP 建单、OA 审批到 MES 报工的完整业务闭环。</p>
        </div>
        <form class="login-form" @submit.prevent="signIn">
          <label class="login-field">
            <span>用户名</span>
            <input v-model="username" type="text" autocomplete="username" placeholder="输入演示用户名" autofocus />
          </label>
          <label class="login-field">
            <span>密码</span>
            <input v-model="password" type="password" autocomplete="current-password" placeholder="输入演示密码" />
          </label>
          <p v-if="error" class="login-error" role="alert">{{ error }}</p>
          <button class="button button-primary login-submit" type="submit" :disabled="loading">
            {{ loading ? '正在验证…' : '登录工作台' }}
            <span aria-hidden="true">→</span>
          </button>
        </form>
        <div class="login-hint"><span class="hint-dot"></span>默认演示账号与密码见项目 README，仅用于本地体验。</div>
        <div class="login-legal">ForgeFlow MVP · 演示数据请勿用于生产环境</div>
      </div>

      <aside class="login-story">
        <div class="story-grid" aria-hidden="true"></div>
        <div class="story-topline"><span class="story-live-dot"></span>制造协同 · 实时业务链路</div>
        <div class="story-content">
          <span class="story-kicker">ONE WORKFLOW, ONE SOURCE OF TRUTH</span>
          <h2>从计划到产线，<br />每一步都可追踪。</h2>
          <p>让计划、审批与执行团队围绕同一张生产订单协作。</p>
          <ol class="workflow-list">
            <li><span class="flow-number">01</span><span><strong>ERP 计划</strong><small>维护产品、BOM，创建生产订单</small></span><span class="flow-tag">PLAN</span></li>
            <li><span class="flow-number">02</span><span><strong>OA 审批</strong><small>审批决策留痕，状态清晰流转</small></span><span class="flow-tag">REVIEW</span></li>
            <li><span class="flow-number">03</span><span><strong>MES 执行</strong><small>分批报工，进度与良率同步汇总</small></span><span class="flow-tag">EXECUTE</span></li>
          </ol>
        </div>
        <div class="story-footer"><span>Built for a focused manufacturing workflow</span><span>01 / 03</span></div>
      </aside>
    </section>
  </main>
</template>
