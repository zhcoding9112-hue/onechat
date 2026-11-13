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
  Rate,
  Progress,
  showToast,
} from 'vant'
import { workspaceApi, type MyProfile, type SurveyAggregate } from '@/talent/api/workspace'

const router = useRouter()
const profile = ref<MyProfile | null>(null)
const survey = ref<SurveyAggregate | null>(null)
const loading = ref(true)
const switching = ref(false)

onMounted(load)

async function load() {
  try {
    const [p, s] = await Promise.all([workspaceApi.me(), workspaceApi.survey()])
    profile.value = p
    survey.value = s
  } finally {
    loading.value = false
  }
}

/** 某个分值占总数的百分比，用于分布条。 */
function percent(score: number): number {
  if (!survey.value || survey.value.count === 0) return 0
  return Math.round(((survey.value.distribution[String(score)] ?? 0) / survey.value.count) * 100)
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

    <CellGroup inset title="满意度" style="margin-top: 12px">
      <template v-if="survey && survey.count > 0">
        <Cell title="平均分">
          <template #value>
            <span class="score">{{ survey.average }}</span>
            <Rate :model-value="survey.average ?? 0" readonly allow-half size="14" />
          </template>
        </Cell>
        <Cell :title="`共 ${survey.count} 份评价`" />
        <div class="dist">
          <div v-for="n in 5" :key="n" class="dist-row">
            <span class="dist-label">{{ 6 - n }} 分</span>
            <Progress
              :percentage="percent(6 - n)"
              :show-pivot="false"
              stroke-width="6"
              style="flex: 1"
            />
            <span class="dist-count">{{ survey.distribution[String(6 - n)] ?? 0 }}</span>
          </div>
        </div>
        <p class="footnote" style="margin: 0; padding: 4px 16px 12px">
          只展示汇总结果。单条评价出自哪一笔订单、哪位粉丝，系统不会告诉任何人。
        </p>
      </template>
      <Cell v-else title="还没有收到评价" :label="'服务完成后粉丝可以评分'" />
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

.score {
  margin-right: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #ff976a;
}

.dist {
  padding: 4px 16px 8px;
}

.dist-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
}

.dist-label {
  width: 32px;
  font-size: 12px;
  color: #969799;
}

.dist-count {
  width: 24px;
  text-align: right;
  font-size: 12px;
  color: #969799;
}

.footnote {
  margin: 12px 16px 0;
  font-size: 12px;
  line-height: 1.7;
  color: #969799;
}
</style>
