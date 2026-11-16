/**
 * 全局「当前正在播放的试听」登记处。
 *
 * 必须放在独立模块里，不能写在组件的 `<script setup>` 顶层：
 * `<script setup>` 的顶层代码是 setup 函数体，每个组件实例都会重新执行一遍，
 * 在那里声明的变量是实例私有的。曾经就是这么写的，于是「同时只播一条」
 * 完全没生效——两个播放器可以一起响，而代码看上去毫无问题。
 */

let currentStop: (() => void) | null = null

/** 开始播放前调用：停掉上一个，并把自己登记为当前播放者。 */
export function takeOver(stop: () => void) {
  if (currentStop && currentStop !== stop) {
    currentStop()
  }
  currentStop = stop
}

/** 停止播放时调用：只有自己仍是当前播放者时才清空登记。 */
export function release(stop: () => void) {
  if (currentStop === stop) {
    currentStop = null
  }
}
