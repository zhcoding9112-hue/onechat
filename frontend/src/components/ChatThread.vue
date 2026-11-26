<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue';
import LevelBadge from '@/components/LevelBadge.vue';
import { useRouter } from 'vue-router';
import { NavBar, Skeleton, showToast, showImagePreview } from 'vant';
import type {
  ChatConversationView,
  ChatMessageView,
  ChatView,
} from '@/api/chat';
import { ApiError } from '@/api/http';

/**
 * 会话详情。粉丝端与主播端共用。
 *
 * 两端的差别只有两处：调哪组接口，以及「对方」是谁。其余（气泡左右、图片发送、
 * 只读态）完全一致，所以做成一个组件，而不是把同样的两百行抄两遍——
 * 抄两遍的代价是以后只改一边。
 */
const props = defineProps<{
  conversationId: number;
  api: {
    open: (id: number, limit?: number) => Promise<ChatView>;
    sendText: (id: number, content: string) => Promise<ChatMessageView>;
    sendImage: (id: number, url: string) => Promise<ChatMessageView>;
    uploadImage: (file: File) => Promise<string>;
  };
  /** 右上角跳转对方主页的链接；主播端看自己的会话时不需要。 */
  peerLink?: (c: ChatConversationView) => string | null;
}>();

const emit = defineEmits<{ (e: 'read'): void }>();

const router = useRouter();

const conversation = ref<ChatConversationView | null>(null);
const messages = ref<ChatMessageView[]>([]);
const draft = ref('');
const loading = ref(true);
const failed = ref(false);
const sending = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);
const scroller = ref<HTMLElement | null>(null);

onMounted(async () => {
  try {
    const view = await props.api.open(props.conversationId);
    conversation.value = view.conversation;
    messages.value = view.messages;
    // 打开即已读，导航上的红点要跟着变
    emit('read');
    await scrollToEnd();
  } catch {
    failed.value = true;
  } finally {
    loading.value = false;
  }
});

async function scrollToEnd() {
  await nextTick();
  const el = scroller.value;
  if (el) el.scrollTop = el.scrollHeight;
}

async function send() {
  const text = draft.value.trim();
  if (!text || sending.value) return;
  sending.value = true;
  try {
    messages.value.push(await props.api.sendText(props.conversationId, text));
    draft.value = '';
    await scrollToEnd();
  } catch (e) {
    if (!(e instanceof ApiError)) throw e;
  } finally {
    sending.value = false;
  }
}

function pickImage() {
  fileInput.value?.click();
}

async function onPick(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  sending.value = true;
  try {
    const url = await props.api.uploadImage(file);
    messages.value.push(await props.api.sendImage(props.conversationId, url));
    await scrollToEnd();
  } catch (err) {
    showToast(err instanceof Error ? err.message : '发送失败');
  } finally {
    sending.value = false;
  }
}

function preview(url: string) {
  showImagePreview({ images: [url] });
}

function peerUrl() {
  const c = conversation.value;
  if (!c || !props.peerLink) return null;
  return props.peerLink(c);
}

function timeText(iso: string) {
  const at = new Date(iso);
  return `${String(at.getHours()).padStart(2, '0')}:${String(at.getMinutes()).padStart(2, '0')}`;
}
</script>

<template>
  <div class="page">
    <NavBar left-arrow @click-left="router.back()">
      <!-- 用 title 插槽而不是 title 属性：要在昵称旁边放徽章，字符串塞不下组件 -->
      <template #title>
        <span class="peer">
          <span class="peer-name">{{ conversation?.nickname ?? '聊天' }}</span>
          <LevelBadge
            v-if="conversation?.peerLevel"
            :level="conversation.peerLevel"
            :name="conversation.peerLevelName ?? ''"
          />
        </span>
      </template>
      <template #right>
        <span v-if="peerUrl()" class="nav-link" @click="router.push(peerUrl()!)"
          >主页</span
        >
      </template>
    </NavBar>

    <Skeleton v-if="loading" :row="5" style="padding: 16px" />
    <p v-else-if="failed" class="failed">会话打不开了，请稍后再试</p>

    <template v-else>
      <div ref="scroller" class="thread" data-testid="thread">
        <p v-if="messages.length === 0" class="hint">还没有消息，打个招呼吧</p>
        <div
          v-for="m in messages"
          :key="m.id"
          class="line"
          :class="m.mine ? 'mine' : 'theirs'"
          data-testid="message"
          :data-role="m.senderRole"
        >
          <div class="bubble" :class="{ img: m.contentType === 'IMAGE' }">
            <img
              v-if="m.contentType === 'IMAGE'"
              :src="m.content"
              alt=""
              @click="preview(m.content)"
            />
            <span v-else>{{ m.content }}</span>
          </div>
          <span class="at">{{ timeText(m.createdAt) }}</span>
        </div>
      </div>

      <!--
        已下架主播的历史会话只读：输入区整体换成一条说明，而不是留一个灰按钮。
        服务端同样会拒绝发送，前端这里只是别让人白打一段字。
      -->
      <footer
        v-if="conversation && !conversation.canSend"
        class="composer closed"
      >
        <span data-testid="send-disabled">{{
          conversation.disabledReason ?? '暂不可约'
        }}</span>
      </footer>

      <footer v-else class="composer">
        <button
          class="plus"
          :disabled="sending"
          aria-label="发图片"
          @click="pickImage"
        >
          ＋
        </button>
        <input
          ref="fileInput"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          hidden
          @change="onPick"
        />
        <input
          v-model="draft"
          class="input"
          data-testid="chat-input"
          placeholder="说点什么…"
          maxlength="1000"
          @keyup.enter="send"
        />
        <button class="send" :disabled="sending || !draft.trim()" @click="send">
          发送
        </button>
      </footer>
    </template>
  </div>
</template>

<style scoped>
/* 标题栏里昵称与徽章同排。昵称可能很长，徽章不能被挤掉 */
.peer {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  max-width: 62vw;
}

.peer-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--g-page);
}

.nav-link {
  font-size: var(--fs-sm);
  color: var(--c-brand-deep);
}

.failed,
.hint {
  margin: var(--sp-8) auto;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.thread {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-4);
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.line {
  display: flex;
  flex-direction: column;
  max-width: 76%;
}

.line.mine {
  align-self: flex-end;
  align-items: flex-end;
}

.line.theirs {
  align-self: flex-start;
  align-items: flex-start;
}

.bubble {
  padding: 9px 12px;
  border-radius: var(--r-md);
  font-size: var(--fs-md);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--c-surface);
  box-shadow: var(--sd-card);
}

.mine .bubble {
  background: var(--c-brand-soft);
}

.bubble.img {
  padding: 4px;
}

.bubble.img img {
  display: block;
  max-width: 180px;
  border-radius: var(--r-sm);
  cursor: pointer;
}

.at {
  margin-top: 3px;
  font-size: 11px;
  color: var(--c-text-3);
}

.composer {
  flex: none;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-2) var(--sp-3);
  padding-bottom: calc(var(--sp-2) + env(safe-area-inset-bottom));
  background: var(--c-surface);
  border-top: 1px solid var(--c-border);
}

.composer.closed {
  justify-content: center;
  padding: var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.plus {
  flex: none;
  width: 34px;
  height: 34px;
  border: 1px solid var(--c-border);
  border-radius: 50%;
  background: none;
  font-size: 18px;
  line-height: 1;
  color: var(--c-text-2);
  cursor: pointer;
}

.input {
  flex: 1;
  min-width: 0;
  height: 36px;
  padding: 0 12px;
  border: none;
  border-radius: 18px;
  background: var(--c-bg);
  font-size: var(--fs-md);
  outline: none;
}

.send {
  flex: none;
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 17px;
  background: var(--c-brand);
  color: #fff;
  font-size: var(--fs-sm);
  cursor: pointer;
}

.send:disabled,
.plus:disabled {
  opacity: 0.5;
}
.page {
  background: #f5f5f5;
}
.composer {
  max-width: var(--app-max-width);
  width: 100%;
}
.composer input {
  background: #ededed;
  border: 0;
  border-radius: 20px;
}
.composer .send {
  background: #a875db;
  border-radius: 20px;
}
</style>
