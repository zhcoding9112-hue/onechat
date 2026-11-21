# 美术资产来源

本项目不使用任何竞品素材。粉丝端出现的全部图形资产只有两类来源：

1. **自制**：由本项目手写的 SVG，列在下表。
2. **依赖库自带**：随 npm 依赖一起安装的字形与图标，使用其开源许可。

## 自制资产

| 文件 | 用途 | 来源 |
| --- | --- | --- |
| `frontend/public/images/avatar-placeholder.svg` | 无头像时的默认头像 | 自制 |
| `frontend/src/components/EmptyState.vue` 内联 SVG | 空状态三图（无内容 / 取数失败 / 走不通） | 自制 |
| `frontend/src/components/FanTabBar.vue` 内联 SVG | 粉丝端底部导航四个图标 | 自制 |
| 各页内联 SVG（收藏心形、打赏、聊天等） | 按钮图标 | 自制 |

除头像占位图外，其余图形一律内联在组件里：它们都很小，内联省掉一次请求，
也让颜色能直接跟着设计 token 走。

## 依赖库自带

| 资产 | 来自 | 许可 |
| --- | --- | --- |
| Vant 图标字体（woff2，内联为 data URI） | `vant` | MIT |
| Element Plus 图标（仅运营后台） | `element-plus` | MIT |

Vant 的 `@font-face` 原本还有一条指向 `//at.alicdn.com/...woff` 的回退。
产物里已由 `vite.config.ts` 的 `stripRemoteIconFont` 去掉——留着它意味着
某些客户端会让粉丝的浏览器去连一个我们不控制的域名。实测粉丝端各页的资源请求里
本来也没有它（浏览器用的是内联的 woff2）。

## 不使用 Vant 自带插画

Vant 的 `Empty` 组件自带一套插画。它是 Vant 自己的美术资产（MIT，不是竞品素材），
但配色与本站无关，摆在品牌色的页面里像是从别处粘过来的；而且每张都是一大段
带多个渐变定义的内联 SVG，全站三十多处空状态都要各带一份。
因此改用自制的 `EmptyState` 组件。

## 这份清单由构建守住

`AssetProvenanceTest` 会扫描 `frontend/public` 与 `frontend/src` 下的全部二进制图片文件，
出现任何不在上表中的文件就让构建失败。新增资产时，先在这里写清来源。

## 首屏体积

`frontend/scripts/first-screen.mjs` 顺着 Vite manifest 的 imports 闭包算「打开首页
要下载哪些 chunk」，`--check` 时超过上限返回非零退出码，由 `FirstScreenBudgetTest`
在构建里跑起来。

| | 首屏 JS | 说明 |
| --- | --- | --- |
| 改版前（2bf9ef1） | 158,552 B | 第 1 组之前 |
| 当前 | 169,783 B | 1.071×，上限 1.5×（237,828 B）|

两个数字都在真实浏览器里按 resource timing 复量过一次，与脚本逐字节一致。

## 内联图形：等级徽章与头像框

`frontend/src/components/LevelBadge.vue`、`frontend/src/components/AvatarFrame.vue`

翅膀、顶冠的路径是手写的 SVG，直接内联在组件里；头像框是 CSS 的
`conic-gradient` 画的。**没有任何外部素材**，也没有新增图片文件，
所以不出现在上面的清单里（那个清单只盯独立的图片文件）。

记在这里是为了把话说全：这两处的观感来自渐变、阴影与动画，不是找来的图。
竞品的翅膀/头像框素材一张都不能拿——要换风格就改这两个文件里的路径与配色。
