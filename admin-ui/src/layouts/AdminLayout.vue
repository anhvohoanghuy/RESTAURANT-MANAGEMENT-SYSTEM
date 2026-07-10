<script setup lang="ts">
import {
  ClipboardList,
  CreditCard,
  Home,
  LayoutGrid,
  LogOut,
  Menu as MenuIcon,
  Package,
  ReceiptText,
  Soup,
  Utensils,
} from '@lucide/vue'
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { logout } from '../api/auth'
import { authState, clearSession } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const drawerOpen = ref(false)

const navItems = [
  { to: '/', label: 'Overview', icon: Home },
  { to: '/menu', label: 'Menu', icon: Utensils },
  { to: '/tables', label: 'Tables', icon: LayoutGrid },
  { to: '/inventory', label: 'Inventory', icon: Package },
  { to: '/payments', label: 'Payments', icon: CreditCard },
  { to: '/kitchen', label: 'Kitchen', icon: Soup },
  { to: '/orders', label: 'Orders', icon: ClipboardList },
]

const pageTitle = computed(() => navItems.find((item) => item.to === route.path)?.label ?? 'Admin')

async function signOut() {
  const refreshToken = authState.refreshToken
  clearSession()
  if (refreshToken) {
    await logout(refreshToken).catch(() => undefined)
  }
  await router.push('/login')
}
</script>

<template>
  <div class="admin-shell">
    <aside class="sidebar" :class="{ open: drawerOpen }">
      <div class="brand">
        <ReceiptText :size="22" />
        <span>feat1 Admin</span>
      </div>
      <nav class="nav-list" aria-label="Admin modules">
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to" class="nav-item" @click="drawerOpen = false">
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
    </aside>

    <div class="main-area">
      <header class="topbar">
        <button class="icon-button mobile-only" type="button" aria-label="Open navigation" @click="drawerOpen = true">
          <MenuIcon :size="20" />
        </button>
        <div>
          <p class="eyebrow">Restaurant operations</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <button class="ghost-button" type="button" @click="signOut">
          <LogOut :size="16" />
          <span>Sign out</span>
        </button>
      </header>

      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>
