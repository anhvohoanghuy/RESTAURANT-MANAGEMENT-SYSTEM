<script setup lang="ts">
import Modal from './Modal.vue'

defineProps<{
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  danger?: boolean
  pending?: boolean
}>()

const emit = defineEmits<{ confirm: []; close: [] }>()
</script>

<template>
  <Modal :open="open" :title="title" @close="emit('close')">
    <p>{{ message }}</p>
    <div class="modal-footer">
      <button class="ghost-button" type="button" :disabled="pending" @click="emit('close')">Cancel</button>
      <button
        class="primary-button"
        :class="{ 'danger-button': danger }"
        type="button"
        :disabled="pending"
        @click="emit('confirm')"
      >
        {{ pending ? 'Working…' : (confirmLabel ?? 'Confirm') }}
      </button>
    </div>
  </Modal>
</template>
