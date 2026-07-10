<script setup lang="ts">
import { LogIn } from '@lucide/vue'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api/auth'
import { ApiError } from '../api/client'
import { authState, setSession } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  loading.value = true
  error.value = ''
  try {
    setSession(await login({ username: username.value, password: password.value }))
    await router.push((route.query.redirect as string) || '/')
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : 'We could not sign you in. Check your credentials.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <form class="login-panel" @submit.prevent="submit">
      <div class="brand compact">
        <LogIn :size="22" />
        <span>feat1 Admin</span>
      </div>
      <div>
        <p class="eyebrow">Admin access</p>
        <h1>Sign in</h1>
      </div>
      <p v-if="authState.message" class="notice">{{ authState.message }}</p>
      <label>
        Username
        <input v-model="username" autocomplete="username" required />
      </label>
      <label>
        Password
        <input v-model="password" type="password" autocomplete="current-password" required />
      </label>
      <p v-if="error" class="form-error">{{ error }}</p>
      <button class="primary-button" type="submit" :disabled="loading">
        <LogIn :size="16" />
        <span>{{ loading ? 'Signing in' : 'Sign in' }}</span>
      </button>
    </form>
  </main>
</template>
