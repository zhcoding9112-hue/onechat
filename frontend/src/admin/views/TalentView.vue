<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'
import {
  talentAdminApi,
  AUDIO_TYPES,
  COVER_TYPES,
  MAX_AUDIO_BYTES,
  MAX_COVER_BYTES,
  type Gender,
  type TalentAdminView,
  type ImportResult,
  type TalentProfilePayload,
} from '@/admin/api/talent'
import { readAudioSeconds } from '@/utils/audioDuration'

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

// ---------- 素材上传 ----------

const media = reactive({
  visible: false,
  row: null as TalentAdminView | null,
  coverUploading: false,
  audioUploading: false,
  /** 已选但还没上传的音频，等运营确认时长 */
  pendingAudio: null as File | null,
  pendingAudioName: '',
  pendingSeconds: null as number | null,
  /** 时长读不出来时为 true，必须手填 */
  secondsUnknown: false,
  detecting: false,
})

const coverInput = ref<HTMLInputElement | null>(null)

const IMPORT_HEADER = '昵称,性别,年龄,地区,简介,标签,宣传语,排序,服务档位,价格元'

/** 模板由后端给，带 BOM 与一行示例，Excel 打开不乱码。 */
function downloadTemplate() {
  window.location.assign('/api/admin/talents/import/template')
}
const audioInput = ref<HTMLInputElement | null>(null)

function openMedia(row: TalentAdminView) {
  Object.assign(media, {
    visible: true,
    row,
    coverUploading: false,
    audioUploading: false,
    pendingAudio: null,
    pendingAudioName: '',
    pendingSeconds: null,
    secondsUnknown: false,
    detecting: false,
  })
}

/** 选中文件后先在本地挡一道，省得传完几 MB 才被后端拒绝。 */
function precheck(file: File, types: string[], maxBytes: number, label: string): boolean {
  if (!types.includes(file.type.toLowerCase())) {
    ElMessage.error(`不支持的${label}格式：${file.type || '未知'}`)
    return false
  }
  if (file.size > maxBytes) {
    ElMessage.error(
      `${label}大小 ${(file.size / 1024 / 1024).toFixed(1)} MB，超过 ${maxBytes / 1024 / 1024} MB 上限`,
    )
    return false
  }
  return true
}

async function onCoverPicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 先清空，否则连续选同一个文件不会再触发 change
  input.value = ''
  if (!file || !media.row) return
  if (!precheck(file, COVER_TYPES, MAX_COVER_BYTES, '图片')) return

  media.coverUploading = true
  try {
    await talentAdminApi.uploadCover(media.row.id, file)
    ElMessage.success('封面已更新')
    await refreshMediaRow()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    media.coverUploading = false
  }
}

async function onAudioPicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!precheck(file, AUDIO_TYPES, MAX_AUDIO_BYTES, '音频')) return

  media.pendingAudio = file
  media.pendingAudioName = file.name
  media.pendingSeconds = null
  media.secondsUnknown = false
  media.detecting = true
  try {
    const seconds = await readAudioSeconds(file)
    media.pendingSeconds = seconds
    media.secondsUnknown = seconds === null
  } finally {
    media.detecting = false
  }
}

async function uploadAudio() {
  if (!media.row || !media.pendingAudio) return
  const seconds = media.pendingSeconds
  if (seconds === null || seconds <= 0) {
    ElMessage.warning('请填写试听时长（秒）')
    return
  }

  media.audioUploading = true
  try {
    await talentAdminApi.uploadAudio(media.row.id, media.pendingAudio, seconds)
    ElMessage.success('试听片段已更新')
    media.pendingAudio = null
    media.pendingAudioName = ''
    media.pendingSeconds = null
    media.secondsUnknown = false
    await refreshMediaRow()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    media.audioUploading = false
  }
}

function cancelPendingAudio() {
  media.pendingAudio = null
  media.pendingAudioName = ''
  media.pendingSeconds = null
  media.secondsUnknown = false
}

/** 上传后重新拉一遍：列表的「素材」列和弹窗里的预览都要跟着变。 */
async function refreshMediaRow() {
  if (!media.row) return
  const id = media.row.id
  await reload()
  media.row = rows.value.find((r) => r.id === id) ?? media.row
}

// ---------- 批量导入 ----------

const imp = reactive({
  visible: false,
  csv: '',
  busy: false,
  result: null as ImportResult | null,
})

function openImport() {
  Object.assign(imp, { visible: true, csv: '', busy: false, result: null })
}

async function runImport(dryRun: boolean) {
  if (!imp.csv.trim()) {
    ElMessage.warning('先把表格内容贴进来')
    return
  }
  imp.busy = true
  imp.result = null
  try {
    imp.result = await talentAdminApi.importCsv(imp.csv, dryRun)
    if (dryRun) {
      ElMessage.success(`试算通过，${imp.result.created} 行没有问题，可以正式导入了`)
    } else {
      ElMessage.success(`已导入 ${imp.result.created} 位`)
      imp.visible = false
      await reload()
    }
  } catch (e) {
    // 哪一行出的问题在后端的报错里，统一通路已经弹出来了
    if (!(e instanceof ApiError)) throw e
  } finally {
    imp.busy = false
  }
}

function durationText(seconds: number | null) {
  if (!seconds) return ''
  const m = Math.floor(seconds / 60)
  const sec = seconds % 60
  return m > 0 ? `${m} 分 ${sec} 秒` : `${sec} 秒`
}
</script>

<template>
  <div class="page">
    <div class="head">
      <h2>主播管理</h2>
      <el-button @click="openImport">批量导入</el-button>
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
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(asRow(row))">编辑</el-button>
          <el-button link type="primary" @click="openMedia(asRow(row))">素材</el-button>
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

    <el-dialog v-model="imp.visible" title="批量导入花名册" width="720px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        <template #title>先试算，再导入</template>
        表格里有一个错字就整批不导——导到一半最难收拾：不知道哪些进去了，
        重导又会撞昵称。所以先点「试算」，把错改干净再点「正式导入」。
        <strong>封面与试听不在这里导</strong>，它们是文件，导完回列表逐个点「素材」传。
        导进来的主播<strong>一律不上架</strong>，素材齐了再上。
      </el-alert>

      <p class="hint-block" style="margin-bottom: 8px">
        第一行必须是表头：<code>{{ IMPORT_HEADER }}</code><br />
        标签与宣传语用竖线分隔（如 <code>温柔|耐心</code>）；
        服务档位填「项目编码:档位编码」（如 <code>listen:m30</code>），
        与价格要么都填、要么都留空。
        <el-button link type="primary" @click="downloadTemplate">下载模板</el-button>
      </p>

      <el-input
        v-model="imp.csv"
        type="textarea"
        :rows="10"
        placeholder="把 CSV 内容整个贴进来，含表头"
        style="font-family: ui-monospace, Menlo, monospace"
      />

      <el-table
        v-if="imp.result && imp.result.rows.length"
        :data="imp.result.rows"
        size="small"
        max-height="220"
        style="margin-top: 12px"
      >
        <el-table-column label="行" width="60" prop="line" />
        <el-table-column label="昵称" min-width="140" prop="nickname" />
        <el-table-column label="结果" min-width="200">
          <template #default="{ row }">
            <span :class="row.ok ? 'ok' : 'bad'">{{ row.message }}</span>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="imp.visible = false">取消</el-button>
        <el-button :loading="imp.busy" @click="runImport(true)">试算</el-button>
        <el-button type="primary" :loading="imp.busy" @click="runImport(false)">
          正式导入
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="media.visible"
      :title="media.row ? `素材 — ${media.row.nickname}` : '素材'"
      width="560px"
    >
      <template v-if="media.row">
        <el-alert
          v-if="media.row.listed && (!media.row.coverUrl || !media.row.audioUrl)"
          type="warning"
          :closable="false"
          show-icon
          title="这位主播已经上架，但素材不齐，粉丝端卡片现在是留白的。"
          style="margin-bottom: 16px"
        />

        <section class="media-block">
          <h4>封面图</h4>
          <div class="media-row">
            <el-image
              v-if="media.row.coverUrl"
              :src="media.row.coverUrl"
              :preview-src-list="[media.row.coverUrl]"
              fit="cover"
              class="cover-preview"
              preview-teleported
            />
            <div v-else class="cover-preview empty">未上传</div>

            <div class="media-actions">
              <el-button
                type="primary"
                :loading="media.coverUploading"
                @click="coverInput?.click()"
              >
                {{ media.row.coverUrl ? '更换封面' : '上传封面' }}
              </el-button>
              <p class="hint-block">
                JPG / PNG / WebP，不超过 2 MB。建议竖图，粉丝端卡片按竖向裁切。
                <br />更换后旧文件会被删除。
              </p>
            </div>
          </div>
          <input
            ref="coverInput"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            class="file-input"
            @change="onCoverPicked"
          />
        </section>

        <el-divider />

        <section class="media-block">
          <h4>试听片段</h4>

          <div v-if="media.row.audioUrl" class="current-audio">
            <audio :src="media.row.audioUrl" controls preload="none" class="player" />
            <span class="hint">当前时长 {{ durationText(media.row.audioSeconds) || '未知' }}</span>
          </div>
          <p v-else class="hint-block">未上传</p>

          <div v-if="!media.pendingAudio" class="media-actions">
            <el-button :loading="media.audioUploading" @click="audioInput?.click()">
              {{ media.row.audioUrl ? '更换试听' : '上传试听' }}
            </el-button>
            <p class="hint-block">MP3 / M4A / AAC / WAV，不超过 5 MB。</p>
          </div>

          <div v-else class="pending">
            <p class="file-name">{{ media.pendingAudioName }}</p>

            <p v-if="media.detecting" class="hint-block">正在读取时长…</p>
            <template v-else>
              <el-alert
                v-if="media.secondsUnknown"
                type="warning"
                :closable="false"
                show-icon
                title="读不出这个文件的时长，请自己听一遍后手动填写"
                style="margin-bottom: 8px"
              />
              <div class="seconds-row">
                <span>试听时长</span>
                <el-input-number
                  v-model="media.pendingSeconds"
                  :min="1"
                  :max="3600"
                  controls-position="right"
                />
                <span class="hint">秒{{ media.pendingSeconds ? `（${durationText(media.pendingSeconds)}）` : '' }}</span>
              </div>
              <p class="hint-block">
                时长是前端读出来的，后端不解析音频文件，所以这个数字直接决定粉丝端显示的秒数。
                不对就在这里改。
              </p>
            </template>

            <div class="media-actions">
              <el-button
                type="primary"
                :loading="media.audioUploading"
                :disabled="media.detecting"
                @click="uploadAudio"
              >
                上传
              </el-button>
              <el-button @click="cancelPendingAudio">重选</el-button>
            </div>
          </div>

          <input
            ref="audioInput"
            type="file"
            accept="audio/mpeg,audio/mp4,audio/aac,audio/x-m4a,audio/wav,audio/x-wav,.mp3,.m4a,.aac,.wav"
            class="file-input"
            @change="onAudioPicked"
          />
        </section>
      </template>

      <template #footer>
        <el-button type="primary" @click="media.visible = false">完成</el-button>
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
.ok {
  color: #67c23a;
}
.bad {
  color: #f56c6c;
}
.hint {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.media-block h4 {
  margin: 0 0 12px;
  font-size: 14px;
}
.media-row {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.cover-preview {
  width: 96px;
  height: 128px;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
  flex: none;
}
.cover-preview.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 12px;
  background: #fafafa;
}
.media-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}
.hint-block {
  margin: 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.current-audio {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.player {
  height: 32px;
  max-width: 300px;
}
/* 原生 file 输入一律藏起来，由按钮触发，样式才统一 */
.file-input {
  display: none;
}
.pending {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
}
.file-name {
  margin: 0 0 8px;
  font-size: 13px;
  word-break: break-all;
}
.seconds-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  margin-bottom: 6px;
}
/* 待上传块里的两个按钮并排，外面那组（按钮 + 多行说明）才需要竖排 */
.pending .media-actions {
  flex-direction: row;
  margin-top: 10px;
}
</style>
