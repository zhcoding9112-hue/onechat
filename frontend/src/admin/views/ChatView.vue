<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  chatAdminApi,
  type AdminChatMessage,
  type AdminConversation,
  type AdminTranscript,
} from '@/admin/api/chat'
import { ApiError } from '@/api/http'

const form = reactive({ orderRef: '', userId: '' })
const results = ref<AdminConversation[]>([])
const transcript = ref<AdminTranscript | null>(null)
const searching = ref(false)
const searched = ref(false)

function asRow(row: unknown): AdminConversation {
  return row as AdminConversation
}

async function search() {
  const orderRef = form.orderRef.trim()
  const userId = form.userId.trim()
  if (!orderRef && !userId) {
    ElMessage.warning('请填订单号或用户 id')
    return
  }
  searching.value = true
  transcript.value = null
  try {
    results.value = await chatAdminApi.search({
      orderRef: orderRef || undefined,
      userId: userId ? Number(userId) : undefined,
    })
    searched.value = true
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
    results.value = []
  } finally {
    searching.value = false
  }
}

async function openTranscript(row: AdminConversation) {
  transcript.value = await chatAdminApi.transcript(row.id)
}

async function remove(m: AdminChatMessage) {
  const { value } = await ElMessageBox.prompt(
    '下架原因（会记入留痕；消息原文仍保留在库中供追溯）',
    '下架消息',
    { inputValidator: (v) => (v && v.trim() ? true : '原因为必填') },
  ).catch(() => ({ value: null }))
  if (value === null) return

  await chatAdminApi.removeMessage(m.id, value)
  ElMessage.success('已下架')
  if (transcript.value) {
    transcript.value = await chatAdminApi.transcript(transcript.value.conversation.id)
  }
}

function time(iso: string) {
  return new Date(iso).toLocaleString()
}
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <strong>会话查询</strong>
        <span class="hint">纠纷追溯用。看到的是完整记录，含已下架的消息。</span>
      </template>

      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="订单号">
          <el-input v-model="form.orderRef" placeholder="如 PO20261116…" clearable />
        </el-form-item>
        <el-form-item label="用户 id">
          <el-input v-model="form.userId" placeholder="粉丝或主播的 app_user.id" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="searching" @click="search">查询</el-button>
        </el-form-item>
      </el-form>

      <el-empty
        v-if="searched && results.length === 0"
        description="没有找到会话。该订单的双方可能从未在站内聊过。"
      />

      <el-table v-else-if="results.length" :data="results" size="small">
        <el-table-column prop="id" label="会话" width="80" />
        <el-table-column label="粉丝" min-width="160">
          <template #default="{ row }">
            {{ asRow(row).fanNickname }}（{{ asRow(row).fanUserId }}）
          </template>
        </el-table-column>
        <el-table-column label="主播" min-width="160">
          <template #default="{ row }">
            {{ asRow(row).talentNickname }}（{{ asRow(row).talentId }}）
          </template>
        </el-table-column>
        <el-table-column label="最后消息" min-width="170">
          <template #default="{ row }">{{ time(asRow(row).lastMessageAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTranscript(asRow(row))">
              查看记录
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="transcript" shadow="never" class="transcript">
      <template #header>
        <strong>会话 {{ transcript.conversation.id }} 的完整记录</strong>
        <span class="hint">
          {{ transcript.conversation.fanNickname }}
          ×
          {{ transcript.conversation.talentNickname }}
          · 共 {{ transcript.messages.length }} 条
        </span>
      </template>

      <el-empty v-if="transcript.messages.length === 0" description="这个会话还没有消息" />

      <ul v-else class="thread">
        <li
          v-for="m in transcript.messages"
          :key="m.id"
          class="line"
          :class="{ removed: m.status === 'REMOVED' }"
        >
          <div class="meta">
            <el-tag size="small" :type="m.senderRole === 'FAN' ? 'info' : 'success'">
              {{ m.senderRole === 'FAN' ? '粉丝' : '主播' }}
            </el-tag>
            <span class="who">{{ m.senderNickname }}</span>
            <span class="at">{{ time(m.createdAt) }}</span>
            <el-button
              v-if="m.status === 'VISIBLE'"
              link
              type="danger"
              size="small"
              @click="remove(m)"
            >
              下架
            </el-button>
          </div>
          <div class="body">
            <el-image
              v-if="m.contentType === 'IMAGE'"
              :src="m.content"
              :preview-src-list="[m.content]"
              fit="cover"
              class="img"
            />
            <span v-else>{{ m.content }}</span>
          </div>
          <p v-if="m.status === 'REMOVED'" class="trail">
            已下架 · 操作者 {{ m.removedBy }} · {{ m.removedAt ? time(m.removedAt) : '' }} ·
            {{ m.removeReason }}（原文保留于上方）
          </p>
        </li>
      </ul>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

.thread {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.line {
  border-left: 3px solid #e4e7ed;
  padding-left: 10px;
}

.line.removed {
  border-left-color: #f56c6c;
  background: #fef0f0;
}

.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}

.body {
  margin-top: 4px;
  font-size: 14px;
  white-space: pre-wrap;
  word-break: break-word;
}

.img {
  width: 120px;
  border-radius: 4px;
}

.trail {
  margin: 4px 0 0;
  font-size: 12px;
  color: #f56c6c;
}
</style>
