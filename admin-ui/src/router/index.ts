import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { isAuthenticated } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import OverviewView from '../views/OverviewView.vue'
import MenuView from '../views/MenuView.vue'
import TablesView from '../views/TablesView.vue'
import InventoryView from '../views/InventoryView.vue'
import PaymentsView from '../views/PaymentsView.vue'
import KitchenView from '../views/KitchenView.vue'
import OrdersView from '../views/OrdersView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    {
      path: '/',
      component: AdminLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'overview', component: OverviewView },
        { path: 'menu', name: 'menu', component: MenuView },
        { path: 'tables', name: 'tables', component: TablesView },
        { path: 'inventory', name: 'inventory', component: InventoryView },
        { path: 'payments', name: 'payments', component: PaymentsView },
        { path: 'kitchen', name: 'kitchen', component: KitchenView },
        { path: 'orders', name: 'orders', component: OrdersView },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !isAuthenticated()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && isAuthenticated()) {
    return { name: 'overview' }
  }
  return true
})
