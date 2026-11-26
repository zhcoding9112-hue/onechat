<script setup lang="ts">
import { ref, watch } from 'vue';
import { Popup, Skeleton, Icon, showToast, showImagePreview } from 'vant';
import { homeApi, type SupportInfo } from '@/api/home';

const props = defineProps<{ show: boolean }>();
const emit = defineEmits<{ 'update:show': [value: boolean] }>();
const info = ref<SupportInfo | null>(null);
const loading = ref(false);
const failed = ref(false);
const qrFailed = ref(false);
const copying = ref(false);
let requestId = 0;

watch(
  () => props.show,
  (show) => {
    if (show) void load();
    else ++requestId;
  },
  { immediate: true },
);

async function load() {
  const id = ++requestId;
  loading.value = true;
  failed.value = false;
  qrFailed.value = false;
  info.value = null;
  try {
    const result = await homeApi.support();
    if (id === requestId) info.value = result;
  } catch {
    if (id === requestId) failed.value = true;
  } finally {
    if (id === requestId) loading.value = false;
  }
}

async function copyWechat() {
  if (!info.value?.wechatId || copying.value) return;
  copying.value = true;
  try {
    if (!navigator.clipboard?.writeText)
      throw new Error('Clipboard unavailable');
    await navigator.clipboard.writeText(info.value.wechatId);
    showToast('微信号已复制，打开微信添加客服');
  } catch {
    showToast('请长按微信号，选择复制');
  } finally {
    copying.value = false;
  }
}
</script>

<template>
  <Popup
    :show="show"
    round
    close-on-popstate
    class="support-dialog"
    :aria-label="info?.title || '在线客服'"
    @update:show="emit('update:show', $event)"
    @keydown.esc="emit('update:show', false)"
  >
    <button
      class="support-close"
      aria-label="关闭客服弹层"
      @click="emit('update:show', false)"
    >
      <Icon name="cross" />
    </button>
    <div class="support-header">
      <span class="support-emblem"><Icon name="service-o" /></span>
      <h2>{{ info?.title || '在线客服' }}</h2>
      <p>{{ info?.description || '订单咨询 · 服务协助' }}</p>
    </div>
    <div class="support-body" aria-live="polite">
      <Skeleton v-if="loading" :row="4" class="support-loading" />
      <template v-else-if="failed">
        <div class="support-symbol"><Icon name="replay" /></div>
        <h3>客服信息加载失败</h3>
        <p class="support-note">请稍后重试，也可以返回公众号会话联系客服。</p>
        <button class="support-primary" @click="load">重新加载</button>
      </template>
      <template v-else-if="info">
        <template v-if="info.qrCodeUrl">
          <div v-if="qrFailed" class="qr-failed">
            <Icon name="photo-fail" /><span>二维码加载失败</span>
            <button @click="qrFailed = false">重试</button>
          </div>
          <div v-else class="support-qr">
            <img
              :src="info.qrCodeUrl"
              alt="客服微信二维码"
              @error="qrFailed = true"
            />
            <i v-for="n in 4" :key="n" :class="'corner-' + n" />
          </div>
          <p v-if="!qrFailed" class="qr-guide">长按识别二维码，添加客服微信</p>
          <button
            v-if="!qrFailed"
            class="support-text"
            @click="
              showImagePreview({ images: [info.qrCodeUrl], closeable: true })
            "
          >
            查看二维码大图
          </button>
        </template>
        <div v-else class="support-symbol"><Icon name="chat-o" /></div>
        <template v-if="info.wechatId">
          <div class="support-account">
            <small>客服微信</small><strong>{{ info.wechatId }}</strong>
          </div>
          <button
            class="support-primary"
            :disabled="copying"
            @click="copyWechat"
          >
            {{ copying ? '正在复制…' : '复制微信号' }}
          </button>
        </template>
        <a
          v-if="info.serviceUrl"
          class="support-primary"
          :class="{ secondary: !!info.wechatId }"
          :href="info.serviceUrl"
          >联系在线客服 <Icon name="arrow"
        /></a>
        <template v-if="!info.configured">
          <h3>在公众号里找到我们</h3>
          <p class="support-note">
            返回公众号会话，发送你的问题。<br />订单咨询请一并提供订单编号。
          </p>
          <button class="support-primary" @click="emit('update:show', false)">
            我知道了
          </button>
        </template>
        <p v-if="info.hours" class="support-hours">
          <Icon name="clock-o" /> 服务时间 {{ info.hours }}
        </p>
      </template>
      <p class="support-footnote">订单问题请附订单编号，便于客服核对处理</p>
    </div>
  </Popup>
</template>

<style scoped>
.support-dialog {
  width: min(320px, calc(100vw - 40px));
  max-height: calc(100dvh - 48px);
  background: var(--c-surface);
}
.support-close {
  position: absolute;
  top: 12px;
  right: 12px;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--c-text-3);
  font-size: 20px;
  z-index: 1;
}
.support-header {
  padding: 28px 24px 18px;
  text-align: center;
  background: radial-gradient(circle at 10% 0, #d6fff0, transparent 72%),
    linear-gradient(140deg, #e2f8ff, #f6faff);
}
.support-emblem {
  width: 48px;
  height: 48px;
  margin: 0 auto 10px;
  border: 3px solid #fff;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: white;
  background: linear-gradient(135deg, #72d8ff, #0ebac9);
  box-shadow: 0 4px 10px #2dbbc52b;
  font-size: 27px;
}
h2 {
  margin: 0;
  font-size: 21px;
  font-weight: 650;
  color: var(--c-text);
}
.support-header p {
  font-size: 12px;
  color: var(--c-text-3);
  margin: 7px 0 0;
}
.support-body {
  padding: 20px 25px 18px;
  text-align: center;
}
.support-loading {
  padding: 20px 0;
}
.support-qr {
  position: relative;
  width: min(190px, 100%);
  aspect-ratio: 1;
  margin: auto;
  padding: 10px;
  background: var(--c-surface);
}
.support-qr img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  -webkit-touch-callout: default;
}
.support-qr i {
  position: absolute;
  width: 17px;
  height: 17px;
  border: 2px solid var(--c-brand);
  pointer-events: none;
}
.corner-1 {
  top: 0;
  left: 0;
  border-right: 0 !important;
  border-bottom: 0 !important;
}
.corner-2 {
  top: 0;
  right: 0;
  border-left: 0 !important;
  border-bottom: 0 !important;
}
.corner-3 {
  bottom: 0;
  left: 0;
  border-right: 0 !important;
  border-top: 0 !important;
}
.corner-4 {
  bottom: 0;
  right: 0;
  border-left: 0 !important;
  border-top: 0 !important;
}
.qr-guide {
  font-size: 12px;
  color: var(--c-text-3);
  margin: 12px 0 4px;
}
.support-text {
  color: var(--c-brand-deep);
  background: none;
  border: 0;
  padding: 4px;
  font-size: 12px;
}
.support-account {
  margin: 16px 0 14px;
  padding: 11px;
  background: var(--c-bg);
  border-radius: 8px;
}
.support-account small {
  display: block;
  color: var(--c-text-3);
  font-size: 11px;
  margin-bottom: 5px;
}
.support-account strong {
  font-size: 16px;
  color: var(--c-text);
  overflow-wrap: anywhere;
  user-select: text;
  -webkit-user-select: text;
}
.support-primary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  min-height: 42px;
  margin-top: 10px;
  padding: 10px 12px;
  background: linear-gradient(100deg, #20d3bd, #0ab6bc);
  border: 0;
  border-radius: 22px;
  color: #fff;
  font-size: 14px;
  text-decoration: none;
}
.support-primary:disabled {
  opacity: 0.6;
}
.support-primary.secondary {
  color: var(--c-brand-deep);
  background: var(--c-brand-soft);
}
.support-symbol {
  display: grid;
  place-items: center;
  margin: 0 auto 14px;
  width: 76px;
  height: 76px;
  color: var(--c-brand);
  background: var(--c-brand-soft);
  border-radius: 50%;
  font-size: 39px;
}
h3 {
  margin: 10px 0;
  color: var(--c-text-2);
  font-size: 16px;
  font-weight: 500;
}
.support-note {
  color: var(--c-text-2);
  font-size: 13px;
  line-height: 1.8;
  margin: 12px 0 20px;
}
.support-hours {
  color: var(--c-text-3);
  font-size: 11px;
  margin: 14px 0 0;
}
.support-footnote {
  margin: 18px 0 0;
  color: var(--c-text-3);
  font-size: 10px;
  line-height: 1.6;
}
.qr-failed {
  display: grid;
  justify-items: center;
  gap: 8px;
  color: var(--c-text-3);
  font-size: 12px;
  padding: 20px;
  background: var(--c-bg);
  border-radius: 10px;
}
.qr-failed > .van-icon {
  font-size: 32px;
}
.qr-failed button {
  border: 0;
  padding: 5px 14px;
  border-radius: 15px;
  background: var(--c-brand-soft);
  color: var(--c-brand-deep);
}
</style>
