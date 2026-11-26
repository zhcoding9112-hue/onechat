# 美术资产来源

2026-11-22 用户更新规则：允许参考或复制竞品素材，所有资产必须登记来源。本轮实际使用下列自制或生成资产，没有下载竞品图片。粉丝端图形资产来源：

1. **自制**：由本项目手写的 SVG，列在下表。
2. **依赖库自带**：随 npm 依赖一起安装的字形与图标，使用其开源许可。

## 自制资产

| 文件 | 用途 | 来源 |
| --- | --- | --- |
| `frontend/public/images/portraits.webp` | 六张成年人物插画头像，封面缺失时替代 | 2026-11-22 使用 ImageGen 原创生成，3 × 2 拼图，cwebp 质量 84 编码；原图保留在本地 generated_images |
| `frontend/src/components/WingFrame.vue` 内联 SVG / CSS | 珠宝羽翼、扇动与光点 | 自制几何路径和动画 |
| `frontend/src/components/GiftArt.vue`、`GiftPreview.vue` | 爱心、盲盒、奖杯、喇叭、耳机、礼物与粒子预览 | 首页五种装饰使用自制渐变 SVG，其余礼物使用系统 Emoji，预览动效为 CSS，无外部图片 |
| `frontend/public/images/avatar-placeholder.svg` | 无头像时的默认头像 | 自制 |
| `frontend/src/components/EmptyState.vue` 内联 SVG | 空状态三图（无内容 / 取数失败 / 走不通） | 自制 |
| `frontend/src/components/FanTabBar.vue` 内联 SVG | 粉丝端底部导航六个图标 | 自制 |
| 各页内联 SVG（收藏心形、打赏、聊天等） | 按钮图标 | 自制 |

除头像占位图、头像拼图外，其余图形内联在组件里：它们都很小，内联省掉一次请求，
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
| 历史改版记录 | 169,783 B | 本轮数据见 docs/visual-replica-acceptance.md；上限 237,828 B |

历史体积记录保留供对照，本轮以构建检查的实测结果为准。

## 内联图形：等级徽章与头像框

`frontend/src/components/LevelBadge.vue`、`frontend/src/components/AvatarFrame.vue`

翅膀、顶冠的路径是手写的 SVG，直接内联在组件里；头像框是 CSS 的
`conic-gradient` 画的。**没有任何外部素材**，也没有新增图片文件，
所以不出现在上面的清单里（那个清单只盯独立的图片文件）。

记在这里是为了把话说全：这两处的观感来自渐变、阴影与动画，不是找来的图。
2026-11-22 用户已允许参考或复制竞品素材。本轮翅膀使用自制路径与动画；后续引入其他素材时须在本清单登记来源。

## 一个踩过的坑：SVG 里的注释

默认头像曾经整整一段时间是裂的——文件在、路径对、Content-Type 对、HTTP 200，
只有浏览器解析时才报错，所以谁都没发现。原因是说明注释里写了 CSS 变量名：

```
<!-- 配色取自 --g-avatar 与 --c-offline -->
```

**XML 注释里不允许出现连续两个减号**，整个文件因此解析失败。

现在那段说明改用 `<desc>` 承载，并且 `AssetProvenanceTest.SVG必须是合法XML`
会把 public/ 和 src/ 下每个 .svg 都解析一遍——再出这种事会在构建时就红，
而不是等谁盯着头像看出来。

## 插画头像生成说明

提示词要点：精确三列两行无边框，六位 25–30 岁成年人物，柔和写实插画，粉／紫／蓝／薄荷／蜜桃背景，完整着装，肩部肖像，无文字商标。运营上传的真实封面优先，生成头像只用于缺图或加载失败时的占位。

### 2026-11-23 紫色装饰头像框参考

- `public/images/peiyi-magic-frame-color.png`：从用户打开的“陪一”竞品页面打印参考 PDF 第 2 页提取的 200×200 装饰框色彩图；来源文件为 `/Users/richard/Downloads/peiyi-reference-20261123.pdf`，提取对象 image-020。用户已允许复用竞品素材。图中不包含主播照片。
- `public/images/peiyi-magic-frame-mask.png`：同一图片的灰度透明蒙版，提取对象 image-021；由 CSS `mask-mode: luminance` 恢复透明区域。
- `TalentPortrait.vue` 使用上述原始静帧。星光闪烁及幽灵、南瓜挂件浮动是参照实时页面自行实现的 CSS 动画，并非提取到的竞品原始动画。人气横栏使用普通头像，推荐区只有指定模拟主播显示装饰框。
- 新增 14 位开发预览模拟主播与 3 张演示横幅，头像沿用已登记的 `portraits.webp`。模拟资料明确标注，不写入数据库、不提供下单支付与聊天入口，生产构建关闭。

### 2026-11-23 好运蝴蝶结立体头像框

- `public/images/lucky-bow-frame.png`：使用内置 image_gen 工具（imagegen 技能）依据用户提供的粉色蝴蝶结、金星、许愿瓶、金币云朵参考图重绘的透明装饰素材，1254×1254 RGBA，中心及外围透明。生成输出来源：`/Users/richard/.codex/generated_images/01a20315-8669-7d30-a181-3dd384aca470/exec-d1140c72-1550-46a6-9478-6832721050d4.png`。没有使用 CLI/API 回退。
- 完整提示词及参考处理方式见 [avatar-frame-lucky-prompt.md](avatar-frame-lucky-prompt.md)。移除了参考中的人物、文字、音频按钮和页面背景；生成素材不含真人肖像。
- `LuckyAvatarFrame.vue` 通过 CSS 将静态素材的蝴蝶结、许愿瓶、金币云朵分区摆动并补充星光；不是竞品原始动画。用户否定的四套简笔 SVG 框已撤掉，当前使用新框、原魔法框和普通头像搭配。

### 2026-11-23 模拟动态配图

- `public/images/feed-lifestyle-scenes.png`：内置 image_gen 生成的 1536×1024 六格生活照片，包含咖啡、公园、鲜花、甜点、雨天读书、河畔晚霞；由 `DemoFeedGallery.vue` 使用 CSS 定位逐格展示，支持放大。生成输出为 `exec-27f32a21-5a68-4f78-8a0e-990dfd52aaa9.png`，完整提示词见 [feed-scenes-prompt.md](feed-scenes-prompt.md)。未使用 CLI/API 回退。
- 24 条模拟动态均明确标注；内容与配图不代表主播真实生活，互动仅保存在当前页面会话。

### 2026-11-23 主播合成示例语音

- `frontend/public/audio/demo/*.mp3`：通过 AI Voice Generator 的标准公开音色生成，未克隆任何真实主播声音。为当前 16 位展示主播分别编写中文自我介绍，完整文案、音色、生成来源、字节数、实际时长见 [demo-voices.json](demo-voices.json)。每段源文件约 16.6–18.3 秒，播放器四舍五入显示。
- 文件保存在项目内并由同源地址加载，不依赖生成工具的临时播放链接。仅开发视觉演示启用替换；列表、详情、盲盒结果与试音页使用相同对应关系。正式上传资料和数据库字段不变，按钮标为“合成”。
