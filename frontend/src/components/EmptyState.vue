<script setup lang="ts">
/**
 * 空状态。
 *
 * 自制的两张小图，替换 Vant 自带的那套插画：
 *   - Vant 的插画是它自己的美术资产（MIT，不是竞品素材），但配色与本站无关，
 *     摆在品牌色的页面里像是从别处粘过来的；
 *   - 它每张都是一大段带多个渐变定义的内联 SVG，全站 38 处空状态都要带上一份。
 *
 * 这里的两张图只用线条与品牌色，尺寸统一 120，形状差异足够一眼分辨
 * 「这里本来就没有东西」和「没拉到数据」。
 */
withDefaults(
  defineProps<{
    /**
     * 'default' 本来就没有内容；'network' 取数失败；'error' 走不通（缺授权、环境不对）。
     */
    image?: 'default' | 'network' | 'error'
    description?: string
  }>(),
  { image: 'default', description: '' },
)
</script>

<template>
  <div class="empty">
    <svg class="art" viewBox="0 0 120 120" aria-hidden="true">
      <!-- 共用的底托阴影，让图形不至于浮在空白里 -->
      <ellipse cx="60" cy="100" rx="34" ry="6" fill="var(--c-border)" />

      <template v-if="image === 'network'">
        <!-- 取数失败：一朵断开的云 -->
        <path
          d="M38 74a16 16 0 0 1 1.6-31.9A23 23 0 0 1 83 46a14 14 0 0 1 1 28z"
          fill="var(--c-brand-soft)"
          stroke="var(--c-offline)"
          stroke-width="2.5"
          stroke-linejoin="round"
        />
        <path
          d="M47 86l26-26M73 86L47 60"
          stroke="var(--c-offline)"
          stroke-width="3"
          stroke-linecap="round"
        />
      </template>

      <template v-else-if="image === 'error'">
        <!-- 走不通：一个带感叹号的圆 -->
        <circle
          cx="60"
          cy="58"
          r="30"
          fill="var(--c-danger-soft)"
          stroke="var(--c-danger)"
          stroke-width="2.5"
        />
        <path
          d="M60 42v20"
          stroke="var(--c-danger)"
          stroke-width="4"
          stroke-linecap="round"
        />
        <circle cx="60" cy="72" r="3" fill="var(--c-danger)" />
      </template>

      <template v-else>
        <!-- 空：一个打开的空盒子 -->
        <path
          d="M30 54h60v32a6 6 0 0 1-6 6H36a6 6 0 0 1-6-6z"
          fill="var(--c-brand-soft)"
          stroke="var(--c-offline)"
          stroke-width="2.5"
          stroke-linejoin="round"
        />
        <path
          d="M24 38h72l-6 16H30z"
          fill="#fff"
          stroke="var(--c-offline)"
          stroke-width="2.5"
          stroke-linejoin="round"
        />
        <path d="M60 38v16" stroke="var(--c-offline)" stroke-width="2.5" />
      </template>
    </svg>

    <!--
      具名 description 插槽：有两个页面要在这里放多行说明（标题 + 正文），
      一行 description 放不下。Vant 的 Empty 有这个插槽，换成自制组件时
      若不一并提供，那两页的说明文字会无声无息地消失——
      页面不报错、也不溢出，只是从此没人知道出了什么事。
    -->
    <div v-if="$slots.description" class="desc">
      <slot name="description" />
    </div>
    <p v-else-if="description" class="desc">{{ description }}</p>
    <div class="extra"><slot /></div>
  </div>
</template>

<style scoped>
.empty {
  padding: var(--sp-8) var(--sp-4);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-3);
}

.art {
  width: 120px;
  height: 120px;
  display: block;
}

.desc {
  margin: 0;
  font-size: var(--fs-md);
  line-height: 1.6;
  color: var(--c-text-3);
  text-align: center;
}

.extra:empty {
  display: none;
}
</style>
