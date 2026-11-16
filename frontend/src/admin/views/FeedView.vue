<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { feedAdminApi, type AdminPick, type AdminPost, type AdminReport } from '@/admin/api/feed'
import { ApiError } from '@/api/http'

const tab = ref('reports')

const reports = ref<AdminReport[]>([])
const posts = ref<AdminPost[]>([])
const picks = ref<AdminPick[]>([])
const maxImages = ref(9)
const loading = ref(false)

const pickForm = reactive({ talentId: null as number | null, sortOrder: 0 })

onMounted(reload)

async function reload() {
  loading.value = true
  try {
    ;[reports.value, posts.value, picks.value] = await Promise.all([
      feedAdminApi.reports(),
      feedAdminApi.posts(),
      feedAdminApi.picks(),
    ])
    maxImages.value = (await feedAdminApi.settings()).maxImages
  } finally {
    loading.value = false
  }
}

function asReport(row: unknown): AdminReport {
  return row as AdminReport
}

function asPost(row: unknown): AdminPost {
  return row as AdminPost
}

function asPick(row: unknown): AdminPick {
  return row as AdminPick
}

async function handleReport(row: AdminReport) {
  const { value } = await ElMessageBox.prompt('下架原因（会记入留痕，粉丝看不到）', '下架内容', {
    inputValue: row.reason,
    inputValidator: (v) => (v && v.trim() ? true : '原因为必填'),
  }).catch(() => ({ value: null }))
  if (value === null) return

  const res = await feedAdminApi.handleReport(row.id, value)
  ElMessage.success(`已下架，同时关闭 ${res.closedReports} 条举报`)
  await reload()
}

async function dismissReport(row: AdminReport) {
  await ElMessageBox.confirm('驳回后内容保持可见，该对象上的待处理举报一并关闭。', '驳回举报')
  const res = await feedAdminApi.dismissReport(row.id, '内容无问题')
  ElMessage.success(`已驳回 ${res.closedReports} 条`)
  await reload()
}

async function removePost(row: AdminPost) {
  const { value } = await ElMessageBox.prompt('下架原因', '下架动态', {
    inputValidator: (v) => (v && v.trim() ? true : '原因为必填'),
  }).catch(() => ({ value: null }))
  if (value === null) return

  await feedAdminApi.removePost(row.id, value)
  ElMessage.success('已下架')
  await reload()
}

async function addPick() {
  if (!pickForm.talentId) {
    ElMessage.warning('请输入主播 id')
    return
  }
  try {
    await feedAdminApi.pick(pickForm.talentId, pickForm.sortOrder)
    ElMessage.success('已加入心动墙，粉丝刷新即可见')
    pickForm.talentId = null
    await reload()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  }
}

async function removePick(row: AdminPick) {
  await feedAdminApi.unpick(row.talentId)
  ElMessage.success('已移出')
  await reload()
}

async function saveMaxImages() {
  await feedAdminApi.updateSettings(maxImages.value)
  ElMessage.success('已保存')
}
</script>

<template>
  <div v-loading="loading">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>下架是软的</template>
      下架只是让内容对粉丝不可见，原文与图片原样保留，并记下操作者、时间与原因，
      申诉与纠纷时可以追溯。处理一条举报会把同一对象上的全部待处理举报一并关闭。
    </el-alert>

    <el-tabs v-model="tab">
      <el-tab-pane label="举报队列" name="reports">
        <el-table :data="reports" empty-text="没有待处理的举报">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="对象" width="140">
            <template #default="{ row }">
              {{ asReport(row).targetType === 'POST' ? '动态' : '评论' }} #{{ asReport(row).targetId }}
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="举报理由" min-width="220" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="举报时间" width="190" />
          <el-table-column label="操作" width="170">
            <template #default="{ row }">
              <el-button link type="danger" @click="handleReport(asReport(row))">下架</el-button>
              <el-button link @click="dismissReport(asReport(row))">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="全部动态" name="posts">
        <el-table :data="posts" empty-text="还没有动态">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="nickname" label="主播" width="130" />
          <el-table-column prop="content" label="原文" min-width="200" show-overflow-tooltip />
          <el-table-column label="图" width="70">
            <template #default="{ row }">{{ asPost(row).images.length }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="asPost(row).status === 'REMOVED'" type="danger">已下架</el-tag>
              <el-tag v-else type="success">可见</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="下架留痕" min-width="240">
            <template #default="{ row }">
              <span v-if="asPost(row).status === 'REMOVED'">
                {{ asPost(row).removedAt }} · 操作者 {{ asPost(row).removedBy }} ·
                {{ asPost(row).removeReason }}
              </span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button
                v-if="asPost(row).status === 'VISIBLE'"
                link
                type="danger"
                @click="removePost(asPost(row))"
              >
                下架
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="心动墙" name="wall">
        <el-card shadow="never" style="margin-bottom: 16px">
          <el-form inline>
            <el-form-item label="主播 id">
              <el-input-number v-model="pickForm.talentId" :min="1" :controls="false" />
            </el-form-item>
            <el-form-item label="顺序">
              <el-input-number v-model="pickForm.sortOrder" :min="0" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="addPick">加入心动墙</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-table :data="picks" empty-text="心动墙还是空的">
          <el-table-column prop="talentId" label="主播 id" width="100" />
          <el-table-column prop="nickname" label="昵称" min-width="140" />
          <el-table-column prop="sortOrder" label="顺序" width="90" />
          <el-table-column label="上架状态" width="120">
            <template #default="{ row }">
              <el-tag v-if="asPick(row).listed" type="success">已上架</el-tag>
              <el-tag v-else type="info">已下架，不展示</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="danger" @click="removePick(asPick(row))">移出</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="配置" name="settings">
        <el-card shadow="never">
          <el-form label-width="140px" style="max-width: 420px">
            <el-form-item label="单条动态图片上限">
              <el-input-number v-model="maxImages" :min="1" :max="20" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveMaxImages">保存</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
