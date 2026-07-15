<script setup lang="ts">
import { computed } from 'vue'

type Tone = 'info' | 'success' | 'warning' | 'danger' | 'muted'

const STATUS_TONES: Record<string, Tone> = {
  ACTIVE: 'success',
  AVAILABLE: 'success',
  OPEN: 'success',
  CONFIRMED: 'success',
  SEATED: 'success',
  COMPLETED: 'success',
  SERVED: 'info',
  READY: 'info',
  RESERVED: 'info',
  PREPARING: 'warning',
  PENDING: 'warning',
  PENDING_CONFIRMATION: 'warning',
  OCCUPIED: 'warning',
  QUEUED: 'muted',
  SUBMITTED: 'muted',
  CLEANING: 'muted',
  CLOSED: 'muted',
  INACTIVE: 'muted',
  ARCHIVED: 'muted',
  OUT_OF_SERVICE: 'danger',
  CANCELLED: 'danger',
  NO_SHOW: 'danger',
  REJECTED: 'danger',
}

const props = defineProps<{
  tone?: Tone
  status?: string
}>()

const resolvedTone = computed<Tone>(() => {
  if (props.tone) {
    return props.tone
  }
  if (props.status && STATUS_TONES[props.status]) {
    return STATUS_TONES[props.status]
  }
  return 'muted'
})
</script>

<template>
  <span class="status-badge" :class="resolvedTone">
    <slot>{{ status }}</slot>
  </span>
</template>
