<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NavBar, Cell, CellGroup, Button } from 'vant'
import { useAppStore } from '@/stores/app'
import { fetchCurrentUser } from '@/api/session'
import { ApiError, http } from '@/api/http'

const app = useAppStore()
const probeResult = ref('未调用')
const toastResult = ref('未调用')

onMounted(() => {
  app.markBooted()
})

async function probeProtected() {
  probeResult.value = '请求中…'
  try {
    const me = await fetchCurrentUser()
    probeResult.value = `已登录 userId=${me.userId}`
  } catch (e) {
    // 未认证时请求层已自动发起授权跳转，这里只记录结果供自检。
    probeResult.value = e instanceof ApiError ? `${e.code} (HTTP ${e.httpStatus})` : '请求失败'
  }
}

// 自检用：确认非未认证类错误会走统一错误提示，而不是各页各写一遍。
async function probeToast() {
  try {
    await http.get('/api/__nonexistent__')
    toastResult.value = '意外成功'
  } catch (e) {
    toastResult.value = e instanceof ApiError ? `已提示 ${e.code}` : '请求失败'
  }
}
</script>

<template>
  <NavBar title="陪聊服务" />
  <CellGroup inset style="margin-top: 12px">
    <Cell title="前端工程" value="Vue 3 + Vite + TS" />
    <Cell title="组件库" value="Vant 4" />
    <Cell title="状态管理" value="Pinia" />
    <Cell title="启动状态" :value="app.booted ? '已就绪' : '初始化中'" />
    <Cell title="受保护接口" :value="probeResult" />
    <Cell title="统一错误提示" :value="toastResult" />
  </CellGroup>
  <div style="padding: 16px">
    <Button type="primary" block round @click="probeProtected">调用受保护接口</Button>
    <Button block round style="margin-top: 10px" @click="probeToast">触发统一错误提示</Button>
  </div>
</template>
