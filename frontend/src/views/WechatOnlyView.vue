<script setup lang="ts">
import { NavBar } from 'vant'
import EmptyState from '@/components/EmptyState.vue'
</script>

<template>
  <NavBar title="请在微信中打开" />
  <EmptyState image="error">
    <template #description>
      <p class="tip-title">请在微信中打开本页面</p>
      <p class="tip-body">
        本服务需要通过微信完成身份验证，<br />
        请回到微信，从公众号菜单或聊天中的链接进入。
      </p>
    </template>
  </EmptyState>
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
</style>
