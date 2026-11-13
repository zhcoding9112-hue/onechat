<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NavBar,
  Cell,
  CellGroup,
  Switch,
  Tag,
  Image as VanImage,
  Skeleton,
  showToast,
} from 'vant'
import { workspaceApi, type MyProfile } from '@/talent/api/workspace'

const router = useRouter()
const profile = ref<MyProfile | null>(null)
const loading = ref(true)
const switching = ref(false)

onMounted(load)

async function load() {
  try {
    profile.value = await workspaceApi.me()
  } finally {
    loading.value = false
  }
}

async function toggleOnline(value: boolean) {
  switching.value = true
  try {
    profile.value = await workspaceApi.setOnline(value)
    showToast(value ? '已切换为在线' : '已切换为离线')
  } finally {
    switching.value = false
  }
}
</script>

<template>
  <NavBar title="主播资料" />

  <Skeleton v-if="loading" avatar :row="3" style="padding: 16px" />

  <template v-else-if="profile">
    <section class="header">
      <VanImage
        :src="profile.coverUrl ?? '/images/avatar-placeholder.svg'"
        width="56"
        height="56"
        round
        fit="cover"
      />
      <div class="info">
        <p class="name">{{ profile.nickname }}</p>
        <div class="tags">
          <Tag v-for="t in profile.tags" :key="t" plain type="primary" size="medium">{{ t }}</Tag>
        </div>
      </div>
    </section>

    <CellGroup inset style="margin-top: 12px">
      <Cell title="在线状态" :label="profile.online ? '粉丝看到你在线' : '粉丝看到你离线，但仍可下单'">
        <template #value>
          <Switch
            :model-value="profile.online"
            :loading="switching"
            size="22"
            @update:model-value="toggleOnline"
          />
        </template>
      </Cell>
      <Cell
        title="上架状态"
        :value="profile.listed ? '已上架' : '未上架'"
        :label="profile.listed ? undefined : '未上架时粉丝端看不到你，请联系运营'"
      />
    </CellGroup>

    <CellGroup inset title="资料" style="margin-top: 12px">
      <Cell title="简介" :label="profile.intro ?? '未填写'" />
      <Cell
        title="试听片段"
        :value="profile.audioUrl ? `${profile.audioSeconds ?? 0} 秒` : '未配置'"
      />
    </CellGroup>

    <p class="footnote">资料与价格由运营统一维护，需要修改请联系运营。</p>

    <CellGroup inset style="margin-top: 12px">
      <Cell title="返回粉丝端" is-link @click="router.push('/')" />
    </CellGroup>
  </template>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 16px;
  background: #fff;
}

.name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.tags {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.footnote {
  margin: 12px 16px 0;
  font-size: 12px;
  line-height: 1.7;
  color: #969799;
}
</style>
