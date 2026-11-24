<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <main class="forbidden-screen">
    <div class="forbidden-card">
      <span class="forbidden-code">403</span>
      <h1>当前角色没有访问权限</h1>
      <p>你仍可使用已授权的模块。若需要执行此操作，请切换到具备对应权限的演示账号。</p>
      <button class="button button-primary" type="button" @click="router.replace({ name: 'dashboard' })">返回生产总览</button>
    </div>
  </main>
</template>
