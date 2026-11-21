<script setup lang="ts">
import { computed } from 'vue'

/**
 * 粉丝等级徽章。
 *
 * 五级逐级加码，不是五个同样的色块换颜色：
 *   1 新客  朴素圆角标，没有翅膀——还没消费过的人不该先得到一个华丽的东西
 *   2 常客  短翅膀
 *   3 银卡  长翅膀 + 银色渐变 + 一道斜光
 *   4 金卡  长翅膀 + 金色渐变 + 外发光
 *   5 黑卡  长翅膀 + 暗金渐变 + 流光动画 + 顶冠
 *
 * 翅膀与冠都是这里手画的 SVG 路径，没有用任何外部素材——
 * 竞品的图标一律不能拿（见 docs/assets.md）。
 */
const props = withDefaults(
  defineProps<{
    level: number
    name: string
    /** sm 用在行内（昵称旁），lg 用在个人页顶部。 */
    size?: 'sm' | 'lg'
  }>(),
  { size: 'sm' },
)

/** 夹到 1..5：后台能把等级配成几级是运营的事，这里不能因此画崩。 */
const tier = computed(() => Math.min(Math.max(Math.round(props.level), 1), 5))
const hasWing = computed(() => tier.value >= 2)
const hasCrown = computed(() => tier.value >= 5)
const big = computed(() => props.size === 'lg')
</script>

<template>
  <span class="badge" :class="[`t${tier}`, big ? 'lg' : 'sm']">
    <!-- 左翅 -->
    <svg v-if="hasWing" class="wing left" viewBox="0 0 24 16" aria-hidden="true">
      <path class="w1" d="M23 8 C16 2, 8 1, 1 3 C6 5, 10 6.5, 14 8 Z" />
      <path class="w2" d="M23 9 C16 6, 9 6, 3 8 C8 9.5, 12 10.5, 16 11 Z" />
      <path class="w3" d="M23 10.5 C17 10, 11 11, 6 13 C11 13.5, 16 13.5, 20 13 Z" />
    </svg>

    <span class="core">
      <!-- 顶冠只有最高级有 -->
      <svg v-if="hasCrown" class="crown" viewBox="0 0 20 12" aria-hidden="true">
        <path d="M1 11 L3 3 L7 7 L10 1 L13 7 L17 3 L19 11 Z" />
      </svg>
      <span class="text">{{ name }}</span>
      <i v-if="tier >= 3" class="shine" aria-hidden="true" />
    </span>

    <!-- 右翅：同一组路径镜像，不另画一套 -->
    <svg v-if="hasWing" class="wing right" viewBox="0 0 24 16" aria-hidden="true">
      <path class="w1" d="M23 8 C16 2, 8 1, 1 3 C6 5, 10 6.5, 14 8 Z" />
      <path class="w2" d="M23 9 C16 6, 9 6, 3 8 C8 9.5, 12 10.5, 16 11 Z" />
      <path class="w3" d="M23 10.5 C17 10, 11 11, 6 13 C11 13.5, 16 13.5, 20 13 Z" />
    </svg>
  </span>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
  gap: 1px;
  line-height: 1;
}

.core {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border-radius: var(--r-pill);
  font-weight: 700;
  letter-spacing: 0.3px;
  overflow: hidden;
  white-space: nowrap;
}

.sm .core {
  padding: 2px 8px;
  font-size: var(--fs-xs);
}

.lg .core {
  padding: 5px 14px;
  font-size: var(--fs-md);
}

.wing {
  width: 18px;
  height: 12px;
  flex: none;
}

.lg .wing {
  width: 30px;
  height: 20px;
}

/* 右翅是左翅的镜像 */
.wing.right {
  transform: scaleX(-1);
}

.wing path {
  fill: currentColor;
}
/* 三层羽片用透明度拉开层次，比画三种颜色更耐看 */
.wing .w1 { opacity: 0.95; }
.wing .w2 { opacity: 0.72; }
.wing .w3 { opacity: 0.48; }

.crown {
  width: 11px;
  height: 7px;
  flex: none;
}
.lg .crown {
  width: 15px;
  height: 9px;
}
.crown path {
  fill: currentColor;
}

/* ---------- 一级：朴素 ---------- */
.t1 .core {
  color: var(--c-text-2);
  background: var(--c-border);
}

/* ---------- 二级：有翅膀了 ---------- */
.t2 {
  color: #7fb2e8;
}
.t2 .core {
  color: #2f6ea8;
  background: #e2f0ff;
}

/* ---------- 三级：银 ---------- */
.t3 {
  color: #b9c3d2;
}
.t3 .core {
  color: #5a6678;
  background: linear-gradient(135deg, #f2f5f9, #d8dfe9 55%, #f6f8fb);
}

/* ---------- 四级：金 ---------- */
.t4 {
  color: #f0b945;
}
.t4 .core {
  color: #8a5a06;
  background: linear-gradient(135deg, #ffeab5, #f6c661 55%, #ffe3a0);
  box-shadow: 0 0 10px rgba(240, 185, 69, 0.45);
}

/* ---------- 五级：暗金 + 流光 ---------- */
.t5 {
  color: #e8c37a;
}
.t5 .core {
  color: #ffe6b0;
  background: linear-gradient(135deg, #3a3326, #1f1c16 55%, #4a3f2c);
  box-shadow: 0 0 12px rgba(232, 195, 122, 0.5);
}

/* 斜向流光：三级以上才有，靠一条半透明白条反复扫过 */
.shine {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    110deg,
    transparent 35%,
    rgba(255, 255, 255, 0.75) 50%,
    transparent 65%
  );
  transform: translateX(-120%);
  animation: sweep 3.2s ease-in-out infinite;
  pointer-events: none;
}

.t5 .shine {
  background: linear-gradient(
    110deg,
    transparent 35%,
    rgba(255, 230, 176, 0.55) 50%,
    transparent 65%
  );
}

@keyframes sweep {
  0%,
  65% {
    transform: translateX(-120%);
  }
  100% {
    transform: translateX(120%);
  }
}

/* 用户在系统里关了动效就别晃 */
@media (prefers-reduced-motion: reduce) {
  .shine {
    animation: none;
    opacity: 0;
  }
}
</style>
