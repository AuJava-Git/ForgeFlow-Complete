import { createRouter, createWebHistory } from 'vue-router'
import AppShell from './components/AppShell.vue'
import { readSession } from './auth/session.js'
import { routeAccessDecision } from './auth/route-guard.js'

export function createAppRouter(history = createWebHistory()) {
  const router = createRouter({
    history,
    routes: [
      {
        path: '/',
        component: AppShell,
        children: [
          { path: '', redirect: { name: 'dashboard' } },
          { path: 'dashboard', name: 'dashboard', component: () => import('./views/DashboardView.vue') },
          { path: 'products', name: 'products', component: () => import('./views/ProductsView.vue') },
          { path: 'orders', name: 'orders', component: () => import('./views/ProductionOrdersView.vue') },
          { path: 'approvals', name: 'approvals', component: () => import('./views/ApprovalView.vue') },
          { path: 'inventory', name: 'inventory', component: () => import('./views/InventoryView.vue') },
          { path: 'purchasing', name: 'purchasing', component: () => import('./views/PurchasingView.vue') },
          { path: 'mes', name: 'mes', component: () => import('./views/MesWorkbenchView.vue') },
          { path: 'quality', name: 'quality', component: () => import('./views/QualityView.vue') },
        ],
      },
      { path: '/login', name: 'login', component: () => import('./views/LoginView.vue') },
      { path: '/403', name: 'forbidden', component: () => import('./views/ForbiddenView.vue') },
      { path: '/:pathMatch(.*)*', redirect: { name: 'dashboard' } },
    ],
    scrollBehavior: () => ({ top: 0, behavior: 'smooth' }),
  })

  router.beforeEach((to) => {
    const decision = routeAccessDecision(readSession(), to.name)
    if (decision === true) return true
    if (decision === 'login') {
      return { name: 'login', query: to.name === 'login' ? {} : { redirect: to.fullPath } }
    }
    return { name: decision }
  })

  return router
}
