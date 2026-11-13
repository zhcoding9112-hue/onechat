<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Tabbar, TabbarItem } from 'vant'

const route = useRoute()
const router = useRouter()

const active = computed({
  get: () => route.path,
  set: (path: string) => {
    void router.push(path)
  },
})
</script>

<template>
  <div class="wrap">
    <router-view />
  </div>
  <Tabbar v-model="active" route>
    <TabbarItem name="/talent" to="/talent" icon="orders-o">接单</TabbarItem>
    <TabbarItem name="/talent/income" to="/talent/income" icon="balance-o">收入</TabbarItem>
    <TabbarItem name="/talent/me" to="/talent/me" icon="user-o">我的</TabbarItem>
  </Tabbar>
</template>

<style scoped>
.wrap {
  padding-bottom: 60px;
}
</style>
