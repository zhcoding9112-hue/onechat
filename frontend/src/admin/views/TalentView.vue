<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'
import {
  talentAdminApi,
  type Gender,
  type TalentAdminView,
  type TalentProfilePayload,
} from '@/admin/api/talent'

/** 与后端 TalentAdminService.MAX_PROMO_TAGS 对应。前端只做输入框数量约束，真正的拒绝在后端。 */
const MAX_PROMO_TAGS = 2

const rows = ref<TalentAdminView[]>([])
const loading = ref(false)

const form = reactive({
  visible: false,
  id: null as number | null,
  nickname: '',
  intro: '',
  gender: '' as Gender | '',
  age: null as number | null,
  region: '',
  promoTags: ['', ''] as string[],
  sortOrder: 0,
  saving: false,
})

onMounted(reload)

async function reload() {
  loading.value = true
  try {
    rows.value = await talentAdminApi.list()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    visible: true,
    id: null,
    nickname: '',
    intro: '',
    gender: '',
    age: null,
    region: '',
    promoTags: ['', ''],
    sortOrder: 0,
    saving: false,
  })
}

function openEdit(row: TalentAdminView) {
  Object.assign(form, {
    visible: true,
    id: row.id,
    nickname: row.nickname,
    intro: row.intro ?? '',
    gender: row.gender ?? '',
    age: row.age,
    region: row.region ?? '',
    // 回显补齐到固定两格，少于两条时后面是空输入框
    promoTags: [row.promoTags[0] ?? '', row.promoTags[1] ?? ''],
    sortOrder: row.sortOrder,
    saving: false,
  })
}

async function save() {
  if (!form.nickname.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  // 空输入框不算一条标签，否则运营留空一格就会被当成填了内容
  const promoTags = form.promoTags.map((t) => t.trim()).filter((t) => t.length > 0)

  const payload: TalentProfilePayload = {
    nickname: form.nickname.trim(),
    intro: form.intro,
    gender: form.gender === '' ? null : form.gender,
    age: form.age,
    region: form.region,
    promoTags,
    sortOrder: form.sortOrder,
  }

  form.saving = true
  try {
    if (form.id === null) {
      await talentAdminApi.create(payload)
    } else {
      await talentAdminApi.update(form.id, payload)
    }
    ElMessage.success('已保存')
    form.visible = false
    await reload()
  } catch (e) {
    // 数量与长度的业务校验在后端，这里把后端原话透出来，不自己编文案
    if (e instanceof ApiError) {
      ElMessage.error(e.message)
    } else {
      throw e
    }
  } finally {
    form.saving = false
  }
}

async function toggleListed(row: TalentAdminView) {
  await talentAdminApi.setListed(row.id, !row.listed)
  ElMessage.success(row.listed ? '已下架' : '已上架')
  await reload()
}

function genderLabel(g: Gender | null) {
  if (g === 'FEMALE') return '女'
  if (g === 'MALE') return '男'
  return '—'
}

function asRow(row: unknown): TalentAdminView {
  return row as TalentAdminView
}
</script>

<template>
  <div class="page">
    <div class="head">
      <h2>主播管理</h2>
      <el-button type="primary" @click="openCreate">新建主播</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="封面图与试听片段决定粉丝端的第一观感，缺失时卡片会留白。扩展资料未填写的项在粉丝端整项不渲染。"
      style="margin-bottom: 16px"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="nickname" label="昵称" min-width="110" />
      <el-table-column label="性别" width="70">
        <template #default="{ row }">{{ genderLabel(asRow(row).gender) }}</template>
      </el-table-column>
      <el-table-column label="年龄" width="70">
        <template #default="{ row }">{{ asRow(row).age ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="地区" width="100">
        <template #default="{ row }">{{ asRow(row).region || '—' }}</template>
      </el-table-column>
      <el-table-column label="宣传语标签" min-width="180">
        <template #default="{ row }">
          <template v-if="asRow(row).promoTags.length">
            <el-tag v-for="t in asRow(row).promoTags" :key="t" size="small" style="margin-right: 6px">
              {{ t }}
            </el-tag>
          </template>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="素材" width="110">
        <template #default="{ row }">
          <el-tag v-if="!asRow(row).coverUrl" type="warning" size="small">缺封面</el-tag>
          <el-tag v-if="!asRow(row).audioUrl" type="warning" size="small" style="margin-left: 4px">
            缺试听
          </el-tag>
          <span v-if="asRow(row).coverUrl && asRow(row).audioUrl">齐全</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="asRow(row).listed ? 'success' : 'info'" size="small">
            {{ asRow(row).listed ? '已上架' : '未上架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(asRow(row))">编辑</el-button>
          <el-button link type="primary" @click="toggleListed(asRow(row))">
            {{ asRow(row).listed ? '下架' : '上架' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="form.visible" :title="form.id === null ? '新建主播' : '编辑主播'" width="520px">
      <el-form label-width="96px">
        <el-form-item label="昵称" required>
          <el-input v-model="form.nickname" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio value="">未填写</el-radio>
            <el-radio value="FEMALE">女</el-radio>
            <el-radio value="MALE">男</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年龄">
          <el-input-number v-model="form.age" :min="1" :max="120" controls-position="right" />
          <span class="hint">留空表示不展示</span>
        </el-form-item>
        <el-form-item label="地区">
          <el-input v-model="form.region" maxlength="32" placeholder="如：北京 / 广东省" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.intro" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="宣传语标签">
          <div class="promo">
            <el-input
              v-for="i in MAX_PROMO_TAGS"
              :key="i"
              v-model="form.promoTags[i - 1]"
              maxlength="16"
              show-word-limit
              :placeholder="`标签 ${i}`"
            />
          </div>
          <div class="hint">最多 {{ MAX_PROMO_TAGS }} 个，展示在粉丝端卡片上。内容自定，留空则不展示。</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" controls-position="right" />
          <span class="hint">小的在前</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.head h2 {
  margin: 0;
  font-size: 18px;
}
.promo {
  display: flex;
  gap: 8px;
  width: 100%;
}
.hint {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
</style>
