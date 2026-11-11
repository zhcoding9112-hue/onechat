<script setup lang="ts">
import { NavBar, Empty } from 'vant'
</script>

<template>
  <NavBar title="请在微信中打开" />
  <Empty image="error" description="">
    <template #description>
      <p class="tip-title">请在微信中打开本页面</p>
      <p class="tip-body">
        本服务需要通过微信完成身份验证，<br />
        请回到微信，从公众号菜单或聊天中的链接进入。
      </p>
    </template>
  </Empty>
</template>

<style scoped>
.tip-title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: #323233;
}

.tip-body {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #969799;
}
</style>
