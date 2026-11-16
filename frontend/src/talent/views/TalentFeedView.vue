<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Empty, Skeleton, showToast } from 'vant'
import { talentFeedApi, type FeedPostView } from '@/api/feed'
import { ApiError } from '@/api/http'

const posts = ref<FeedPostView[]>([])
const loading = ref(true)
const maxImages = ref(9)
const content = ref('')
const images = ref<string[]>([])
const uploading = ref(false)
const publishing = ref(false)

onMounted(async () => {
  // 上限从服务端取，前端不写死——运营改了配置，这里跟着变
  void talentFeedApi.limits().then((r) => (maxImages.value = r.maxImages)).catch(() => {})
  await reload()
})

async function reload() {
  loading.value = true
  try {
    posts.value = await talentFeedApi.mine()
  } finally {
    loading.value = false
  }
}

async function pick(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length) return

  if (images.value.length + files.length > maxImages.value) {
    showToast(`最多 ${maxImages.value} 张`)
    return
  }
  uploading.value = true
  try {
    for (const file of files) {
      images.value = images.value.concat(await talentFeedApi.uploadImage(file))
    }
  } catch (e) {
    showToast(e instanceof Error ? e.message : '图片上传失败')
  } finally {
    uploading.value = false
  }
}

function removeImage(url: string) {
  images.value = images.value.filter((u) => u !== url)
}

async function publish() {
  if (!content.value.trim() && images.value.length === 0) {
    showToast('写点什么，或者加张图')
    return
  }
  publishing.value = true
  try {
    await talentFeedApi.publish(content.value.trim(), images.value)
    content.value = ''
    images.value = []
    showToast('已发布')
    await reload()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <div class="wrap">
    <section class="composer">
      <textarea
        v-model="content"
        rows="3"
        maxlength="1000"
        placeholder="说点什么，粉丝会在动态广场看到"
      ></textarea>

      <div class="thumbs">
        <div v-for="url in images" :key="url" class="thumb">
          <img :src="url" alt="" />
          <button class="del" @click="removeImage(url)">×</button>
        </div>
        <label v-if="images.length < maxImages" class="add">
          <input type="file" accept="image/jpeg,image/png,image/webp" multiple @change="pick" />
          <span>{{ uploading ? '…' : '+' }}</span>
        </label>
      </div>

      <div class="bar">
        <span class="hint">最多 {{ maxImages }} 张</span>
        <button class="send" :disabled="publishing || uploading" @click="publish">
          {{ publishing ? '发布中…' : '发布' }}
        </button>
      </div>
    </section>

    <h3 class="sec-title">我发过的</h3>
    <Skeleton v-if="loading" :row="3" />
    <Empty v-else-if="posts.length === 0" description="还没有发过动态" />
    <div v-else class="list">
      <article v-for="p in posts" :key="p.id" class="post">
        <p v-if="p.content" class="content">{{ p.content }}</p>
        <div v-if="p.images.length" class="images">
          <img v-for="img in p.images" :key="img" :src="img" alt="" />
        </div>
        <div class="stat">♥ {{ p.likeCount }} · 评论 {{ p.commentCount }}</div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  padding: var(--sp-4);
}

.composer {
  background: var(--c-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--sd-card);
  padding: var(--sp-4);
}

textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  font-family: inherit;
  font-size: var(--fs-md);
  line-height: 1.7;
  color: var(--c-text);
}

.thumbs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  margin-top: var(--sp-3);
}

.thumb {
  position: relative;
  aspect-ratio: 1;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: var(--r-md);
}

.del {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 14px;
  line-height: 20px;
  cursor: pointer;
}

.add {
  aspect-ratio: 1;
  border: 1px dashed var(--c-border);
  border-radius: var(--r-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: var(--c-text-3);
  cursor: pointer;
}

.add input {
  display: none;
}

.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--c-border);
}

.hint {
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}

.send {
  border: none;
  background: var(--c-brand);
  color: #fff;
  border-radius: var(--r-pill);
  padding: 7px 20px;
  font-size: var(--fs-md);
  font-weight: 600;
  cursor: pointer;
}

.sec-title {
  margin: var(--sp-5) 0 var(--sp-3);
  font-size: var(--fs-lg);
  font-weight: 700;
}

.list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.post {
  background: var(--c-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--sd-card);
  padding: var(--sp-4);
}

.content {
  margin: 0;
  font-size: var(--fs-md);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.images {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;
  margin-top: var(--sp-3);
}

.images img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: var(--r-md);
}

.stat {
  margin-top: var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}
</style>
