<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { NavBar, Button } from 'vant'
import { AUTH_ENTRY } from '@/api/http'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()

/** 只接受站内相对路径，避免被构造成跳转到站外。 */
const target = computed(() => {
  const raw = route.query.redirect
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) {
    return '/'
  }
  return value
})

function retry() {
  window.location.assign(`${AUTH_ENTRY}?redirect=${encodeURIComponent(target.value)}`)
}
</script>

<template>
  <NavBar title="需要微信授权" />
  <EmptyState image="error">
    <template #description>
      <p class="tip-title">还没有完成微信授权</p>
      <p class="tip-body">
        需要你同意授权才能确认身份、<br />
        查看订单与联系主播。
      </p>
    </template>
  </EmptyState>
  <div class="actions">
    <Button type="primary" block round @click="retry">重新授权</Button>
  </div>
</template>

<style scoped>
.tip-title {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--c-text);
}

.tip-body {
  margin: 0;
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--c-text-3);
}

.actions {
  padding: var(--sp-2) var(--sp-4) var(--sp-6);
}
</style>
