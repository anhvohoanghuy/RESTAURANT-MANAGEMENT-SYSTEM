<script setup lang="ts">
defineProps<{
  title?: string
  body?: string
}>()
</script>

<template>
  <section class="empty-state">
    <h2>{{ title ?? 'Nothing to review yet' }}</h2>
    <p>{{ body ?? 'When records are available, they will appear here. Adjust filters or create a new record.' }}</p>
  </section>
</template>
