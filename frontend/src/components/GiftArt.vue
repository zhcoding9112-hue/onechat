<script setup lang="ts">
import { useId } from 'vue';
withDefaults(defineProps<{ kind?: string; size?: number }>(), {
  kind: 'heart',
  size: 64,
});
const uid = useId();
const paint = (name: string) => 'url(#' + uid + '-' + name + ')';
const symbols: Record<string, string> = {
  rose: '🌹',
  crown: '👑',
  diamond: '💎',
  castle: '🏰',
  rocket: '🚀',
  finger: '🫰',
  candy: '🍭',
  ring: '💍',
};
</script>
<template>
  <span
    class="gift-art"
    :class="kind"
    :style="{ width: size + 'px', height: size + 'px' }"
    aria-hidden="true"
  >
    <svg
      v-if="['heart', 'box', 'speaker', 'trophy', 'headset'].includes(kind)"
      viewBox="0 0 120 120"
      fill="none"
    >
      <defs>
        <linearGradient
          :id="uid + '-pink'"
          x1="30"
          y1="15"
          x2="95"
          y2="108"
          gradientUnits="userSpaceOnUse"
        >
          <stop stop-color="#ffdbed" />
          <stop offset=".35" stop-color="#ff82bf" />
          <stop offset=".72" stop-color="#ff41a6" />
          <stop offset="1" stop-color="#dd1d8d" />
        </linearGradient>
        <linearGradient
          :id="uid + '-pearl'"
          x1="20"
          y1="5"
          x2="70"
          y2="100"
          gradientUnits="userSpaceOnUse"
        >
          <stop stop-color="#fffede" />
          <stop offset=".5" stop-color="#fff8ff" />
          <stop offset="1" stop-color="#eabfff" />
        </linearGradient>
        <linearGradient
          :id="uid + '-blue'"
          x1="30"
          y1="15"
          x2="85"
          y2="110"
          gradientUnits="userSpaceOnUse"
        >
          <stop stop-color="#b0faff" />
          <stop offset=".35" stop-color="#39c6ff" />
          <stop offset="1" stop-color="#057cf1" />
        </linearGradient>
        <linearGradient
          :id="uid + '-side'"
          x1="75"
          y1="25"
          x2="110"
          y2="107"
          gradientUnits="userSpaceOnUse"
        >
          <stop stop-color="#129fe9" />
          <stop offset="1" stop-color="#0066d7" />
        </linearGradient>
        <linearGradient
          :id="uid + '-gold'"
          x1="35"
          y1="15"
          x2="80"
          y2="110"
          gradientUnits="userSpaceOnUse"
        >
          <stop stop-color="#fff6b0" />
          <stop offset=".2" stop-color="#ffe14b" />
          <stop offset=".62" stop-color="#ffbf03" />
          <stop offset="1" stop-color="#f39a00" />
        </linearGradient>
        <linearGradient
          :id="uid + '-violet'"
          x1="15"
          y1="15"
          x2="105"
          y2="110"
          gradientUnits="userSpaceOnUse"
        >
          <stop stop-color="#f5e5ff" />
          <stop offset=".4" stop-color="#c098ff" />
          <stop offset="1" stop-color="#8556e4" />
        </linearGradient>
      </defs>
      <g v-if="kind === 'heart'">
        <path
          d="M48 74C21 62 8 41 13 25C18 10 33 10 42 20C47 4 65 5 73 19C85 37 67 57 48 74Z"
          :fill="paint('pearl')"
        />
        <path
          d="M79 113C58 102 19 77 21 51C23 26 49 19 64 38C76 16 104 21 112 42C122 67 100 92 79 113Z"
          :fill="paint('pink')"
          stroke="#ffadcf"
        />
        <path
          d="M29 53C29 36 45 30 56 40M81 36C90 30 103 36 105 44"
          stroke="white"
          stroke-opacity=".65"
          stroke-width="4"
          stroke-linecap="round"
        />
        <ellipse
          cx="97"
          cy="79"
          rx="7"
          ry="13"
          transform="rotate(34 97 79)"
          fill="#fff8e6"
          opacity=".85"
        />
      </g>
      <g v-else-if="kind === 'box'">
        <path
          d="M28 29L61 11L110 29L78 46Z"
          fill="#7fe8ff"
          stroke="#c6f7ff"
          stroke-width="2"
        />
        <path d="M78 46L110 29L110 91L78 111Z" :fill="paint('side')" />
        <path
          d="M28 29L78 46L78 111L20 90Z"
          :fill="paint('blue')"
          stroke="#b4f3ff"
          stroke-width="2"
          stroke-linejoin="round"
        />
        <path
          d="M28 29L61 11L111 26L111 35L78 53L25 36Z"
          :fill="paint('blue')"
        />
        <path
          d="M28 29L79 45L111 26M79 45V53"
          stroke="#d7ffff"
          stroke-width="2"
        />
        <path
          d="M52 18L69 24L82 18L92 23L79 28L98 35L87 41L69 34L55 41L45 36L59 29L41 23Z"
          fill="#c9f7ff"
          opacity=".85"
        />
        <text
          x="40"
          y="87"
          fill="white"
          font-size="43"
          font-weight="800"
          font-family="Arial"
          transform="rotate(17 45 67)"
        >
          ?
        </text>
        <ellipse cx="32" cy="46" rx="4" ry="6" fill="#ddfbff" />
        <ellipse cx="65" cy="99" rx="4" ry="6" fill="#ddfbff" />
        <path
          d="M27 65C-5 68 11 85 44 90C74 95 110 84 117 74"
          stroke="#e9fcff"
          stroke-width="4"
          stroke-linecap="round"
        />
        <path d="M96 55L98 64L94 73L92 64Z" fill="#c3f3ff" />
      </g>
      <g v-else-if="kind === 'trophy'">
        <path
          d="M31 26H16V45C16 62 30 64 40 61M89 26H104V45C104 62 90 64 80 61"
          stroke="#ffd22f"
          stroke-width="9"
          stroke-linejoin="round"
        />
        <path
          d="M31 26H16V45M89 26H104V45"
          stroke="#fff0a1"
          stroke-width="3"
          stroke-linecap="round"
        />
        <path
          d="M28 12H92V46C92 65 78 80 60 80C42 80 28 65 28 46Z"
          :fill="paint('gold')"
        />
        <path
          d="M35 18V41"
          stroke="#fff9d9"
          stroke-width="5"
          stroke-linecap="round"
        />
        <path
          d="M60 26L68 41L85 43L73 55L76 72L60 64L44 72L47 55L35 43L52 41Z"
          fill="#ffe976"
          stroke="#fff3aa"
          stroke-width="2"
          stroke-linejoin="round"
        />
        <path d="M53 78H67V94H53Z" fill="#ffc523" />
        <path d="M43 94H77L86 104H34Z" :fill="paint('gold')" />
        <rect
          x="28"
          y="103"
          width="64"
          height="10"
          rx="3"
          :fill="paint('gold')"
        />
        <path d="M33 106H83" stroke="#fff1a4" stroke-width="2" />
      </g>
      <g v-else-if="kind === 'speaker'" transform="rotate(-20 60 60)">
        <path
          d="M40 65L58 70L54 102C52 110 39 108 40 101Z"
          :fill="paint('blue')"
        />
        <path
          d="M27 42H46L86 24V86L46 68H27C15 68 15 42 27 42Z"
          :fill="paint('blue')"
          stroke="#d1faff"
          stroke-width="2"
        />
        <ellipse cx="84" cy="55" rx="22" ry="34" fill="#c3f7ff" />
        <ellipse cx="85" cy="55" rx="16" ry="28" :fill="paint('blue')" />
        <ellipse cx="82" cy="57" rx="8" ry="12" fill="#8ae2ff" />
        <path
          d="M93 17L99 7M106 33L116 28M110 54H119"
          stroke="#f9ffff"
          stroke-width="5"
          stroke-linecap="round"
        />
      </g>
      <g v-else-if="kind === 'headset'">
        <ellipse cx="60" cy="63" rx="43" ry="48" fill="#f2e9ff" opacity=".65" />
        <path
          d="M23 72V49C23 28 39 13 60 13C81 13 97 28 97 49V72"
          :stroke="paint('violet')"
          stroke-width="14"
          stroke-linecap="round"
        />
        <path
          d="M30 45C32 27 45 20 59 20"
          stroke="#fbf4ff"
          stroke-width="3"
          stroke-linecap="round"
        />
        <rect
          x="13"
          y="57"
          width="23"
          height="38"
          rx="10"
          :fill="paint('violet')"
        />
        <rect
          x="84"
          y="57"
          width="23"
          height="38"
          rx="10"
          :fill="paint('violet')"
        />
        <path
          d="M89 91C84 104 72 105 61 105"
          stroke="#c6a0f8"
          stroke-width="5"
          stroke-linecap="round"
        />
        <rect x="49" y="101" width="18" height="8" rx="4" fill="#a47ce1" />
        <path
          d="M51 50V82M51 55L73 49V75M51 82C51 89 39 92 37 85C35 78 48 75 51 82M73 75C73 82 61 85 59 78C57 71 70 68 73 75"
          fill="#b58be9"
          stroke="#b58be9"
          stroke-width="3"
        />
      </g>
    </svg>
    <span v-else class="emoji">{{ symbols[kind] ?? '🎁' }}</span>
  </span>
</template>
<style scoped>
.gift-art {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  filter: drop-shadow(0 3px 3px #839db328);
}
.gift-art svg {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
}
.emoji {
  font-size: calc(v-bind(size) * 0.75px);
  line-height: 1;
}
</style>
