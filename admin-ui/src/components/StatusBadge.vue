<script setup lang="ts">
defineProps<{
  tone?: 'info' | 'success' | 'warning' | 'danger' | 'muted'
}>()
</script>

<template>
  <span class="status-badge" :class="tone ?? 'muted'">
    <slot />
  </span>
</template>
