#!/usr/bin/env bash
#
# 生产构建。一条命令产出可部署产物。
#
# 产物：build/private-checkout.jar —— 前端静态文件已打进去，
# 直接 java -jar 即可同时提供 API 与页面，不需要单独部署 Nginx 托管前端。
# 真要用 Nginx 托管静态文件也可以，产物里的 BOOT-INF/classes/static 就是前端目录。

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# 本机 Homebrew 的 mvn 默认 JDK 不是 17，这里显式指定
if [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  export JAVA_HOME
fi

echo "==> 1/4 构建前端"
cd "$ROOT/frontend"
npm ci --no-fund --no-audit
npm run build

echo "==> 2/4 把前端产物放进后端静态资源目录"
STATIC_DIR="$ROOT/backend/src/main/resources/static"
rm -rf "$STATIC_DIR"
mkdir -p "$STATIC_DIR"
cp -R "$ROOT/frontend/dist/." "$STATIC_DIR/"

echo "==> 3/4 构建后端（含测试）"
cd "$ROOT/backend"
mvn -B clean package

echo "==> 4/4 收集产物"
mkdir -p "$ROOT/build"
cp "$ROOT/backend/target/private-checkout.jar" "$ROOT/build/"

echo
echo "完成：$ROOT/build/private-checkout.jar"
ls -lh "$ROOT/build/private-checkout.jar"
echo
echo "启动："
echo "  SPRING_PROFILES_ACTIVE=prod \\"
echo "  WECHAT_APP_ID=... WECHAT_APP_SECRET=... APP_BASE_URL=https://你的域名 \\"
echo "  DB_PASSWORD=... java -jar build/private-checkout.jar"
