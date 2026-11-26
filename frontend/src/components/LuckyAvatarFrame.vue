<script setup lang="ts">
const sparkles = [
  [20, 16],
  [82, 27],
  [9, 66],
  [88, 57],
  [33, 91],
  [71, 9],
];
</script>

<template>
  <span class="lucky-frame" data-frame="lucky" aria-hidden="true">
    <span class="lucky-glow" />
    <span class="lucky-art lucky-ring" />
    <span class="lucky-art lucky-bow" />
    <span class="lucky-art lucky-jar" />
    <span class="lucky-art lucky-cloud" />
    <i
      v-for="(p, i) in sparkles"
      :key="i"
      class="lucky-spark"
      :style="{ left: `${p[0]}%`, top: `${p[1]}%`, '--i': i }"
      >✦</i
    >
  </span>
</template>

<style scoped>
.lucky-frame {
  position: absolute;
  inset: -6%;
  pointer-events: none;
}
.lucky-art {
  position: absolute;
  inset: 0;
  background: url('/images/lucky-bow-frame.png') center / contain no-repeat;
}
.lucky-ring {
  clip-path: polygon(
    0 0,
    28% 0,
    28% 34%,
    74% 34%,
    74% 0,
    100% 0,
    100% 61%,
    60% 61%,
    40% 82%,
    40% 100%,
    0 100%,
    0 64%,
    28% 64%,
    28% 30%,
    0 30%
  );
}
.lucky-bow {
  clip-path: polygon(28% 0, 74% 0, 74% 34%, 28% 34%);
  transform-origin: 50% 22%;
  animation: lucky-bow 3.4s ease-in-out infinite;
}
.lucky-jar {
  clip-path: polygon(0 30%, 28% 30%, 28% 64%, 0 64%);
  transform-origin: 17% 34%;
  animation: lucky-jar 3s ease-in-out infinite;
}
.lucky-cloud {
  clip-path: polygon(60% 61%, 100% 61%, 100% 100%, 40% 100%, 40% 82%);
  transform-origin: 78% 82%;
  animation: lucky-cloud 3.8s ease-in-out infinite;
}
.lucky-glow {
  position: absolute;
  inset: 12%;
  border-radius: 50%;
  box-shadow:
    0 0 9px #ffa9de70,
    0 0 3px #b4eaffaa;
  animation: lucky-glow 3s ease-in-out infinite;
}
.lucky-spark {
  position: absolute;
  font-style: normal;
  font-size: 11px;
  color: #fff3a5;
  text-shadow:
    0 0 4px #fff,
    0 0 7px #f8aeeb;
  animation: lucky-spark 2.8s calc(var(--i) * -0.45s) ease-in-out infinite;
}
@keyframes lucky-bow {
  0%,
  100% {
    transform: rotate(-5deg) translateY(1%);
  }
  50% {
    transform: rotate(5deg) translateY(-2%) scale(1.03);
  }
}
@keyframes lucky-jar {
  0%,
  100% {
    transform: rotate(-7deg);
  }
  50% {
    transform: rotate(7deg) translateY(-1%);
  }
}
@keyframes lucky-cloud {
  0%,
  100% {
    transform: translateY(2%) rotate(-2deg);
  }
  50% {
    transform: translateY(-4%) rotate(2deg);
  }
}
@keyframes lucky-glow {
  0%,
  100% {
    opacity: 0.4;
    transform: scale(0.97);
  }
  50% {
    opacity: 1;
    transform: scale(1.04);
  }
}
@keyframes lucky-spark {
  0%,
  100% {
    opacity: 0;
    transform: translateY(3px) scale(0.3);
  }
  40% {
    opacity: 1;
    transform: translateY(-2px) scale(1.2);
  }
  85% {
    opacity: 0;
    transform: translateY(-7px) scale(0.5);
  }
}
@media (prefers-reduced-motion: reduce) {
  .lucky-frame * {
    animation: none !important;
  }
  .lucky-spark {
    opacity: 0.6;
  }
}
</style>
