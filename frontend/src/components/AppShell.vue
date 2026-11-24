<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { clearSession, readSession } from '../auth/session.js'
import { navigationForRole, roleLabelFor } from '../auth/roles.js'

const route = useRoute()
const router = useRouter()
const session = ref(readSession())
const navigation = computed(() => navigationForRole(session.value?.role))
const activeItem = computed(() => navigation.value.find((item) => item.key === route.name))
const roleLabel = computed(() => roleLabelFor(session.value?.role))
const avatarText = computed(() => (session.value?.displayName || roleLabel.value).slice(0, 1))

function logout() {
  clearSession()
  router.replace({ name: 'login' })
}

function expireSession() {
  clearSession()
  ElMessage.warning('登录状态已失效，请重新登录')
  router.replace({ name: 'login' })
}

onMounted(() => window.addEventListener('forgeflow:session-expired', expireSession))
onUnmounted(() => window.removeEventListener('forgeflow:session-expired', expireSession))
</script>

<template>
  <div class="application-shell">
    <aside class="sidebar">
      <RouterLink class="brand" to="/dashboard" aria-label="ForgeFlow 首页">
        <span class="brand-mark">F</span>
        <span class="brand-copy"><strong>ForgeFlow</strong><small>智造协同平台</small></span>
      </RouterLink>

      <div class="workspace-label">运营工作台</div>
      <nav class="side-navigation" aria-label="主导航">
        <RouterLink
          v-for="item in navigation"
          :key="item.key"
          :to="item.path"
          class="nav-link"
          :class="{ 'nav-link-active': route.name === item.key }"
        >
          <span class="nav-indicator"></span>
          <span class="nav-link-label">{{ item.label }}</span>
          <span class="nav-link-group">{{ item.group }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-bottom">
        <span class="connection-dot"></span>
        <span>本地演示环境</span>
        <span class="version-label">MVP</span>
      </div>
    </aside>

    <div class="main-column">
      <header class="topbar">
        <div class="topbar-context">
          <span class="topbar-eyebrow">{{ activeItem?.group || '工作台' }}</span>
          <span class="topbar-divider">/</span>
          <strong>{{ activeItem?.label || 'ForgeFlow' }}</strong>
        </div>
        <div class="topbar-account">
          <span class="account-avatar">{{ avatarText }}</span>
          <span class="account-copy"><strong>{{ session?.displayName || 'ForgeFlow 用户' }}</strong><small>{{ roleLabel }}</small></span>
          <button class="logout-button" type="button" @click="logout">退出</button>
        </div>
      </header>
      <main class="page-content"><RouterView /></main>
      <footer class="app-footer"><span>ForgeFlow</span><span>ERP · OA · 库存 · 采购 · MES · 质量闭环</span><span>模块化单体演示版</span></footer>
    </div>
  </div>
</template>
