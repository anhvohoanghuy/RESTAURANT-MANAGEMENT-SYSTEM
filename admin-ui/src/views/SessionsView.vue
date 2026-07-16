<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import DataTable, { type DataTableColumn } from '../components/DataTable.vue'
import EmptyState from '../components/EmptyState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import Toolbar from '../components/Toolbar.vue'
import { sessionsApi, type AuthSessionResponse } from '../api/modules'
import { formatDateTime, messageOf } from '../lib/format'

const loading = ref(true)
const error = ref('')
const authSessions = ref<AuthSessionResponse[]>([])

async function loadSessions() {
  loading.value = true
  error.value = ''
  try {
    authSessions.value = await sessionsApi.list()
  } catch (caught) {
    error.value = messageOf(caught, 'We could not load this data.')
  } finally {
    loading.value = false
  }
}

onMounted(() => loadSessions())

const sessionColumns: DataTableColumn[] = [
  { key: 'userAgent', label: 'Device' },
  { key: 'ipAddress', label: 'IP address' },
  { key: 'createdAt', label: 'Created' },
  { key: 'lastUsedAt', label: 'Last used' },
  { key: 'actions', label: '' },
]

// -- Revoke one session ----------------------------------------------------

const revokeTarget = ref<AuthSessionResponse | null>(null)
const revokePending = ref(false)

function requestRevoke(session: AuthSessionResponse) {
  revokeTarget.value = session
}

async function confirmRevoke() {
  if (!revokeTarget.value) {
    return
  }
  revokePending.value = true
  try {
    await sessionsApi.revoke(revokeTarget.value.sessionId)
    revokeTarget.value = null
    await loadSessions()
  } catch (caught) {
    error.value = messageOf(caught, 'We could not revoke this session.')
  } finally {
    revokePending.value = false
  }
}

// -- Revoke other sessions --------------------------------------------------

const revokeOthersOpen = ref(false)
const revokeOthersPending = ref(false)

async function confirmRevokeOthers() {
  revokeOthersPending.value = true
  try {
    await sessionsApi.revokeOthers()
    revokeOthersOpen.value = false
    await loadSessions()
  } catch (caught) {
    error.value = messageOf(caught, 'We could not sign out other sessions.')
  } finally {
    revokeOthersPending.value = false
  }
}
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Account</p>
        <h2>Sessions</h2>
        <p>Active sign-ins for your account across devices.</p>
      </div>
    </div>

    <Toolbar>
      <template #actions>
        <button
          v-if="authSessions.length > 1"
          class="ghost-button"
          type="button"
          @click="revokeOthersOpen = true"
        >
          Sign out other sessions
        </button>
      </template>
    </Toolbar>

    <section class="panel">
      <div class="panel-header">
        <h3>Sessions</h3>
        <StatusBadge :tone="loading ? 'muted' : error ? 'danger' : 'success'">
          {{ loading ? 'Loading' : error ? 'Needs backend' : 'Ready' }}
        </StatusBadge>
      </div>
      <EmptyState v-if="error" tone="error" :body="error" @retry="loadSessions" />
      <div v-else-if="loading" class="skeleton-table" />
      <DataTable
        v-else
        :columns="sessionColumns"
        :rows="authSessions"
        row-key="sessionId"
        empty-text="No active sessions."
      >
        <template #cell-userAgent="{ value }">
          <span style="overflow-wrap: anywhere">{{ value }}</span>
        </template>
        <template #cell-createdAt="{ value }">{{ formatDateTime(value as string) }}</template>
        <template #cell-lastUsedAt="{ value }">{{ formatDateTime(value as string) }}</template>
        <template #cell-actions="{ row }">
          <button
            class="ghost-button small"
            type="button"
            @click="requestRevoke(row as unknown as AuthSessionResponse)"
          >
            Revoke
          </button>
        </template>
      </DataTable>
    </section>

    <ConfirmDialog
      :open="Boolean(revokeTarget)"
      title="Revoke session"
      message="Sign this device out immediately? They'll need to log in again."
      confirm-label="Revoke"
      danger
      :pending="revokePending"
      @close="revokeTarget = null"
      @confirm="confirmRevoke"
    />

    <ConfirmDialog
      :open="revokeOthersOpen"
      title="Sign out other sessions"
      message="This immediately signs out every other active session for your account. This device stays signed in."
      confirm-label="Sign out others"
      danger
      :pending="revokeOthersPending"
      @close="revokeOthersOpen = false"
      @confirm="confirmRevokeOthers"
    />
  </section>
</template>
