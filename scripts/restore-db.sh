#!/usr/bin/env bash
#
# 从备份恢复。
#
#   ./scripts/restore-db.sh backups/private_checkout-20261115-030000.sql.gz [目标库名]
#
# 默认恢复到 ${DB_NAME}_restore 而不是覆盖生产库——恢复演练不该有把生产库
# 覆盖掉的风险。确认无误后再决定怎么切换。

set -euo pipefail

ARCHIVE="${1:-}"
if [ -z "$ARCHIVE" ] || [ ! -f "$ARCHIVE" ]; then
  echo "用法：$0 <备份文件.sql.gz> [目标库名]" >&2
  exit 1
fi

DB_USER="${DB_USER:-root}"
DB_HOST="${DB_HOST:-127.0.0.1}"
SOURCE_DB="${DB_NAME:-private_checkout}"
TARGET_DB="${2:-${SOURCE_DB}_restore}"

if [ "$TARGET_DB" = "$SOURCE_DB" ]; then
  echo "拒绝直接恢复到 $SOURCE_DB。请显式指定另一个库名，确认数据无误后再切换。" >&2
  exit 1
fi

echo "==> 从 $ARCHIVE 恢复到 $TARGET_DB"
MYSQL_PWD="${DB_PASSWORD:-}" mysql -h "$DB_HOST" -u "$DB_USER" \
  -e "DROP DATABASE IF EXISTS \`$TARGET_DB\`;
      CREATE DATABASE \`$TARGET_DB\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

gunzip -c "$ARCHIVE" | MYSQL_PWD="${DB_PASSWORD:-}" mysql \
  -h "$DB_HOST" -u "$DB_USER" --default-character-set=utf8mb4 "$TARGET_DB"

echo "==> 恢复完成，表清单："
MYSQL_PWD="${DB_PASSWORD:-}" mysql -h "$DB_HOST" -u "$DB_USER" \
  --default-character-set=utf8mb4 "$TARGET_DB" -e "SHOW TABLES;"

echo
echo "用恢复出的库启动应用验证："
echo "  DB_NAME=$TARGET_DB java -jar build/private-checkout.jar"
