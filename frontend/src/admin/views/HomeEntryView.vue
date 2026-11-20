<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  homeEntryAdminApi,
  IMAGE_TYPES,
  MAX_IMAGE_BYTES,
  type HomeEntry,
  type HomeEntryType,
} from '@/admin/api/homeEntry'
import { ApiError } from '@/api/http'

const rows = ref<HomeEntry[]>([])
const loading = ref(false)

const grids = computed(() =>
  rows.value.filter((e) => e.entryType === 'GRID').sort(bySort),
)
const banners = computed(() =>
  rows.value.filter((e) => e.entryType === 'BANNER').sort(bySort),
)

function bySort(a: HomeEntry, b: HomeEntry) {
  return a.sortOrder - b.sortOrder || a.id - b.id
}

/**
 * 粉丝现在到底看不看得到。
 *
 * 后端的筛选是 enabled 为真 **并且** 当前时间落在生效窗口内，两者缺一不可。
 * 只看开关会误判：一条开着但已经过期的记录，开关是「启用」，粉丝却看不到。
 */
type Visibility = { state: 'LIVE' | 'OFF' | 'EARLY' | 'EXPIRED'; label: string }

function visibility(e: HomeEntry): Visibility {
  if (!e.enabled) return { state: 'OFF', label: '已停用' }
  const now = Date.now()
  if (e.startAt && new Date(e.startAt).getTime() > now) {
    return { state: 'EARLY', label: '未到生效时间' }
  }
  if (e.endAt && new Date(e.endAt).getTime() < now) {
    return { state: 'EXPIRED', label: '已过期' }
  }
  return { state: 'LIVE', label: '展示中' }
}

function visibilityType(state: Visibility['state']) {
  return { LIVE: 'success', OFF: 'info', EARLY: 'warning', EXPIRED: 'danger' }[
    state
  ] as 'success' | 'info' | 'warning' | 'danger'
}

/** 启用前该补的东西。不补也能开，但粉丝端会是残的。 */
function problems(e: HomeEntry): string[] {
  const list: string[] = []
  if (!e.targetUrl) list.push('没有跳转地址，点了没反应')
  if (!e.imageUrl) list.push(e.entryType === 'GRID' ? '没有图标' : '没有配图')
  if (e.entryType === 'BANNER' && !e.subtitle) list.push('没有副标题')
  return list
}

const liveGrids = computed(() => grids.value.filter((e) => visibility(e).state === 'LIVE'))
const liveBanners = computed(() => banners.value.filter((e) => visibility(e).state === 'LIVE'))

async function load() {
  loading.value = true
  try {
    rows.value = await homeEntryAdminApi.list()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ---------- 编辑 ----------

const form = reactive({
  visible: false,
  id: null as number | null,
  entryType: 'GRID' as HomeEntryType,
  title: '',
  subtitle: '',
  imageUrl: '',
  targetUrl: '',
  sortOrder: 0,
  enabled: false,
  range: null as [string, string] | null,
  saving: false,
  uploading: false,
})

const fileInput = ref<HTMLInputElement | null>(null)

/*
 * 可以作为跳转目标的站内页面。
 *
 * 与 router/index.ts 里的路由对应，只列不带参数、粉丝点进去有意义的那些。
 * 填错路径后端不会报错，粉丝点进去是一片空白——所以这里直接给出可点的清单，
 * 而不是让运营自己回忆路径。加了新页面记得同步这里。
 */
const SITE_ROUTES = [
  { path: '/', name: '首页' },
  { path: '/match', name: '随机匹配' },
  { path: '/audition', name: '试音选人' },
  { path: '/feed', name: '动态' },
  { path: '/leaderboard', name: '榜单' },
  { path: '/orders', name: '我的订单' },
  { path: '/me/coupons', name: '我的券' },
  { path: '/me/credits', name: '我的元子' },
  { path: '/me/favorites', name: '我的收藏' },
  { path: '/me', name: '我的' },
] as const

function openCreate(type: HomeEntryType) {
  Object.assign(form, {
    visible: true,
    id: null,
    entryType: type,
    title: '',
    subtitle: '',
    imageUrl: '',
    targetUrl: '',
    // 新建的排到当前同类最后
    sortOrder: (type === 'GRID' ? grids.value : banners.value).length + 1,
    // 新建默认不启用：内容填全、确认过跳转地址再开
    enabled: false,
    range: null,
    saving: false,
    uploading: false,
  })
}

function openEdit(e: HomeEntry) {
  Object.assign(form, {
    visible: true,
    id: e.id,
    entryType: e.entryType,
    title: e.title,
    subtitle: e.subtitle ?? '',
    imageUrl: e.imageUrl ?? '',
    targetUrl: e.targetUrl ?? '',
    sortOrder: e.sortOrder,
    enabled: e.enabled,
    range: e.startAt && e.endAt ? [e.startAt, e.endAt] : null,
    saving: false,
    uploading: false,
  })
}

async function onImagePicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  if (!IMAGE_TYPES.includes(file.type.toLowerCase())) {
    ElMessage.error(`不支持的图片格式：${file.type || '未知'}`)
    return
  }
  if (file.size > MAX_IMAGE_BYTES) {
    ElMessage.error(
      `图片 ${(file.size / 1024 / 1024).toFixed(1)} MB，超过 ${MAX_IMAGE_BYTES / 1024 / 1024} MB 上限`,
    )
    return
  }

  form.uploading = true
  try {
    const result = await homeEntryAdminApi.uploadImage(file)
    form.imageUrl = result.url
    ElMessage.success('图片已上传')
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    form.uploading = false
  }
}

async function save() {
  if (!form.title.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }

  const payload = {
    entryType: form.entryType,
    title: form.title.trim(),
    subtitle: form.subtitle.trim(),
    imageUrl: form.imageUrl.trim(),
    targetUrl: form.targetUrl.trim(),
    sortOrder: form.sortOrder,
    enabled: form.enabled,
    startAt: form.range?.[0] ?? null,
    endAt: form.range?.[1] ?? null,
  }

  form.saving = true
  try {
    if (form.id === null) {
      await homeEntryAdminApi.create(payload)
    } else {
      await homeEntryAdminApi.update(form.id, payload)
    }
    ElMessage.success('已保存')
    form.visible = false
    await load()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    form.saving = false
  }
}

async function toggle(e: HomeEntry) {
  const next = !e.enabled
  if (next) {
    const issues = problems(e)
    if (issues.length) {
      try {
        await ElMessageBox.confirm(
          `这一条还缺：${issues.join('；')}。现在启用，粉丝端会是残的。确定要开吗？`,
          '内容还没补齐',
          { type: 'warning', confirmButtonText: '仍然启用', cancelButtonText: '先去补' },
        )
      } catch {
        return
      }
    }
  }

  try {
    await homeEntryAdminApi.update(e.id, {
      entryType: e.entryType,
      title: e.title,
      subtitle: e.subtitle,
      imageUrl: e.imageUrl,
      targetUrl: e.targetUrl,
      sortOrder: e.sortOrder,
      enabled: next,
      startAt: e.startAt,
      endAt: e.endAt,
    })
    ElMessage.success(next ? '已启用' : '已停用')
    await load()
  } catch (err) {
    if (!(err instanceof ApiError)) throw err
  }
}

async function remove(e: HomeEntry) {
  try {
    await ElMessageBox.confirm(
      `删除「${e.title}」？删掉就没了，没有回收站。只是暂时不想展示的话，停用就够了。`,
      '删除运营位',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    await homeEntryAdminApi.remove(e.id)
    ElMessage.success('已删除')
    await load()
  } catch (err) {
    if (!(err instanceof ApiError)) throw err
  }
}

// ---------- 展示 ----------

function asEntry(row: unknown): HomeEntry {
  return row as HomeEntry
}

function windowText(e: HomeEntry) {
  if (!e.startAt && !e.endAt) return '长期有效'
  const f = (v: string | null) => (v ? v.replace('T', ' ').slice(0, 16) : '不限')
  return `${f(e.startAt)} ~ ${f(e.endAt)}`
}
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="head">
      <h2>首页运营位</h2>
      <div>
        <el-button @click="openCreate('GRID')">新建九宫格入口</el-button>
        <el-button @click="openCreate('BANNER')">新建横幅</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>粉丝看不看得到，由开关和生效时间共同决定</template>
      只看「启用」不够——一条开着但已经过期的运营位，开关显示启用，粉丝端照样不展示。
      「当前状态」这一列已经把两者合起来算好了。
      <strong>启用前请确认跳转地址指向真实页面</strong>：地址为空时粉丝点了没有任何反应。
    </el-alert>

    <!-- ---------- 九宫格 ---------- -->
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>
        <div class="card-head">
          <span>九宫格入口</span>
          <span class="sub">
            粉丝端展示 {{ liveGrids.length }} / {{ grids.length }} 条 · 图标按 28×28 显示
          </span>
        </div>
      </template>

      <div v-if="liveGrids.length" class="preview">
        <span class="preview-label">粉丝端现在长这样：</span>
        <div class="preview-grid">
          <div v-for="g in liveGrids" :key="g.id" class="preview-grid-item">
            <img v-if="g.imageUrl" :src="g.imageUrl" alt="" />
            <div v-else class="preview-icon-empty">?</div>
            <span>{{ g.title }}</span>
          </div>
        </div>
      </div>

      <el-empty v-if="!grids.length" description="还没有九宫格入口" />
      <el-table v-else :data="grids" row-key="id">
        <el-table-column label="排序" width="70">
          <template #default="{ row }">
            <span class="sub">{{ asEntry(row).sortOrder }}</span>
          </template>
        </el-table-column>
        <el-table-column label="图标" width="80">
          <template #default="{ row }">
            <el-image
              v-if="asEntry(row).imageUrl"
              :src="asEntry(row).imageUrl!"
              :preview-src-list="[asEntry(row).imageUrl!]"
              fit="contain"
              class="icon"
              preview-teleported
            />
            <span v-else class="sub">无</span>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="120">
          <template #default="{ row }">{{ asEntry(row).title }}</template>
        </el-table-column>
        <el-table-column label="跳转地址" min-width="170">
          <template #default="{ row }">
            <span v-if="asEntry(row).targetUrl" class="mono">{{ asEntry(row).targetUrl }}</span>
            <el-tag v-else type="danger" size="small">未设置</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效时间" min-width="180">
          <template #default="{ row }">
            <span class="sub">{{ windowText(asEntry(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="当前状态" min-width="170">
          <template #default="{ row }">
            <el-tag :type="visibilityType(visibility(asEntry(row)).state)" size="small">
              {{ visibility(asEntry(row)).label }}
            </el-tag>
            <div v-for="p in problems(asEntry(row))" :key="p" class="warn">{{ p }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(asEntry(row))">编辑</el-button>
            <el-button link type="primary" @click="toggle(asEntry(row))">
              {{ asEntry(row).enabled ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="remove(asEntry(row))">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ---------- 横幅 ---------- -->
    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>横幅轮播</span>
          <span class="sub">
            粉丝端展示 {{ liveBanners.length }} / {{ banners.length }} 条
            <template v-if="liveBanners.length > 1">· 多条会自动轮播</template>
          </span>
        </div>
      </template>

      <div v-if="liveBanners.length" class="preview">
        <span class="preview-label">粉丝端现在长这样：</span>
        <div class="preview-banners">
          <div v-for="b in liveBanners" :key="b.id" class="preview-banner">
            <img v-if="b.imageUrl" :src="b.imageUrl" alt="" />
            <div class="preview-banner-text">
              <strong>{{ b.title }}</strong>
              <span v-if="b.subtitle">{{ b.subtitle }}</span>
            </div>
          </div>
        </div>
      </div>

      <el-empty v-if="!banners.length" description="还没有横幅" />
      <el-table v-else :data="banners" row-key="id">
        <el-table-column label="排序" width="70">
          <template #default="{ row }">
            <span class="sub">{{ asEntry(row).sortOrder }}</span>
          </template>
        </el-table-column>
        <el-table-column label="配图" width="110">
          <template #default="{ row }">
            <el-image
              v-if="asEntry(row).imageUrl"
              :src="asEntry(row).imageUrl!"
              :preview-src-list="[asEntry(row).imageUrl!]"
              fit="cover"
              class="thumb"
              preview-teleported
            />
            <span v-else class="sub">无</span>
          </template>
        </el-table-column>
        <el-table-column label="文案" min-width="190">
          <template #default="{ row }">
            <div>{{ asEntry(row).title }}</div>
            <div class="sub">{{ asEntry(row).subtitle || '（无副标题）' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="跳转地址" min-width="150">
          <template #default="{ row }">
            <span v-if="asEntry(row).targetUrl" class="mono">{{ asEntry(row).targetUrl }}</span>
            <el-tag v-else type="danger" size="small">未设置</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效时间" min-width="180">
          <template #default="{ row }">
            <span class="sub">{{ windowText(asEntry(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="当前状态" min-width="170">
          <template #default="{ row }">
            <el-tag :type="visibilityType(visibility(asEntry(row)).state)" size="small">
              {{ visibility(asEntry(row)).label }}
            </el-tag>
            <div v-for="p in problems(asEntry(row))" :key="p" class="warn">{{ p }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(asEntry(row))">编辑</el-button>
            <el-button link type="primary" @click="toggle(asEntry(row))">
              {{ asEntry(row).enabled ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="remove(asEntry(row))">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ---------- 表单 ---------- -->
    <el-dialog
      v-model="form.visible"
      :title="
        (form.id === null ? '新建' : '编辑') +
        (form.entryType === 'GRID' ? '九宫格入口' : '横幅')
      "
      width="560px"
    >
      <el-form label-width="100px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="32" show-word-limit />
        </el-form-item>
        <el-form-item v-if="form.entryType === 'BANNER'" label="副标题">
          <el-input v-model="form.subtitle" maxlength="64" show-word-limit />
        </el-form-item>

        <el-form-item :label="form.entryType === 'GRID' ? '图标' : '配图'">
          <div class="image-field">
            <el-image
              v-if="form.imageUrl"
              :src="form.imageUrl"
              fit="contain"
              :class="form.entryType === 'GRID' ? 'icon' : 'thumb'"
            />
            <div class="image-actions">
              <el-button :loading="form.uploading" @click="fileInput?.click()">
                {{ form.imageUrl ? '换一张' : '上传图片' }}
              </el-button>
              <el-button v-if="form.imageUrl" link @click="form.imageUrl = ''">清除</el-button>
            </div>
          </div>
          <el-input
            v-model="form.imageUrl"
            placeholder="也可以直接填图片地址"
            maxlength="512"
            style="margin-top: 8px"
          />
          <div class="hint-block">
            JPG / PNG / WebP，不超过 2 MB。
            {{ form.entryType === 'GRID' ? '九宫格图标按 28×28 显示，建议用方形透明底的图。' : '横幅按卡片宽度铺满。' }}
          </div>
          <input
            ref="fileInput"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            class="file-input"
            @change="onImagePicked"
          />
        </el-form-item>

        <el-form-item label="跳转地址">
          <el-input v-model="form.targetUrl" placeholder="站内填路径，站外填完整 http 地址" />
          <div class="hint-block">
            留空的话粉丝点了没有任何反应。站内地址会自动带上来源参数，用于转化看板归因。
            <br />
            <strong>路径必须是真实存在的页面</strong>，写错不会报错，粉丝点进去是一片空白。
            现有的站内页面：
            <el-button
              v-for="r in SITE_ROUTES"
              :key="r.path"
              link
              type="primary"
              size="small"
              @click="form.targetUrl = r.path"
            >
              {{ r.path }}<span class="route-name">（{{ r.name }}）</span>
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" controls-position="right" />
          <span class="hint">小的在前</span>
        </el-form-item>

        <el-form-item label="生效时间">
          <el-date-picker
            v-model="form.range"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
          />
          <div class="hint-block">留空表示长期有效。过了结束时间粉丝端自动不再展示，不用回来关。</div>
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
          <span class="hint">内容填全、跳转地址确认过之后再开</span>
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
.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.sub {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  font-weight: 400;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
.warn {
  font-size: 12px;
  color: #e6a23c;
  line-height: 1.5;
}
.icon {
  width: 28px;
  height: 28px;
}
.thumb {
  width: 80px;
  height: 40px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}
.preview {
  background: #fafafa;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 16px;
}
.preview-label {
  font-size: 12px;
  color: #909399;
}
.preview-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 10px;
}
.preview-grid-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  width: 64px;
  text-align: center;
}
.preview-grid-item img {
  width: 28px;
  height: 28px;
  object-fit: contain;
}
.preview-icon-empty {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: #ebeef5;
  color: #c0c4cc;
  display: flex;
  align-items: center;
  justify-content: center;
}
.preview-banners {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}
.preview-banner {
  position: relative;
  width: 260px;
  height: 72px;
  border-radius: 8px;
  overflow: hidden;
  background: #dfe6ef;
  display: flex;
  align-items: center;
}
.preview-banner img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.preview-banner-text {
  position: relative;
  padding: 0 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: #303133;
}
.preview-banner-text strong {
  font-size: 14px;
}
.preview-banner-text span {
  font-size: 12px;
  color: #606266;
}
.image-field {
  display: flex;
  align-items: center;
  gap: 12px;
}
.image-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.file-input {
  display: none;
}
.hint {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.hint-block {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.8;
  width: 100%;
}
.hint-block .el-button {
  margin: 0 6px 0 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.route-name {
  font-family: inherit;
  color: #909399;
}
</style>
