<script setup lang="ts">
defineProps<{
  label: string
  detail: string
}>()
</script>

<template>
  <div class="notice-panel gap-notice">
    <span class="status-badge warning">Known gap</span>
    <div>
      <h3>{{ label }}</h3>
      <p>{{ detail }}</p>
    </div>
  </div>
</template>
