<script setup lang="ts">
import { AlertCircle, Inbox } from '@lucide/vue'

const props = defineProps<{
  tone?: 'empty' | 'error'
  title?: string
  body?: string
  retryable?: boolean
}>()

defineEmits<{ retry: [] }>()

const isError = props.tone === 'error'
</script>

<template>
  <section class="empty-state" :class="tone ?? 'empty'">
    <component :is="isError ? AlertCircle : Inbox" :size="20" />
    <h2>{{ title ?? (isError ? 'We could not load this data.' : 'Nothing to review yet') }}</h2>
    <p>
      {{
        body ??
        (isError
          ? 'Retry, or check your session and API connection.'
          : 'When records are available, they will appear here. Adjust filters or create a new record.')
      }}
    </p>
    <button v-if="isError && retryable !== false" class="ghost-button" type="button" @click="$emit('retry')">
      Retry
    </button>
  </section>
</template>
