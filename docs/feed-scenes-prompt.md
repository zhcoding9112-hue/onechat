# 动态广场模拟配图

内置 image_gen（imagegen 技能）生成六格生活照片素材，保存到 `frontend/public/images/feed-lifestyle-scenes.png`。页面以 CSS 背景定位分别显示六张照片，不把整张拼图作为单条动态配图。未使用 CLI/API 回退。

这些是模拟内容配图，不代表主播的真实生活记录。主题为咖啡、公园、鲜花、甜点、雨天读书和河畔晚霞。

## 最终提示词

Use case: photorealistic-natural. Asset type: six lifestyle photos in one exact 3-column by 2-row square-tile contact sheet for a simulated social feed. Create a 1536x1024 landscape canvas divided into six equal 512x512 squares, edge to edge, NO borders, NO gutters, NO collage overlaps, NO text, NO watermarks, no logos. Each tile is an independent candid smartphone photograph, warm clean contemporary East Asian daily-life aesthetic, realistic details, natural daylight, subtle film color. Top left: ceramic latte cup with beautiful leaf latte art beside a croissant on a light wood cafe table near window, close-up. Top middle: sunny city park walkway lined with green trees, afternoon sun and distant unfocused people, peaceful walking mood. Top right: small bouquet of pastel pink tulips and white daisies wrapped in cream paper, lying on a simple table in soft sunshine. Bottom left: strawberry cream cake slice and iced fruit tea on a cafe table, delicious realistic food photograph. Bottom middle: quiet rainy window, open book and ceramic tea mug on wooden sill, cozy reading mood, no legible book text. Bottom right: riverside city sunset, pink peach sky reflected in water, silhouetted skyline and bridge, atmospheric calm evening. Keep the six tile subjects completely separate, aligned precisely on the grid. Photographs, not illustrations or diagrams.
