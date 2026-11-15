#!/usr/bin/env bash
#
# 数据库备份。建议每日凌晨执行一次。
#
# 装成每日任务（在服务器上）：
#   crontab -e
#   0 3 * * * /opt/private-checkout/scripts/backup-db.sh >> /var/log/pc-backup.log 2>&1
#
# 备份文件带日期，保留最近 N 天。恢复见 scripts/restore-db.sh。

set -euo pipefail

DB_NAME="${DB_NAME:-private_checkout}"
DB_USER="${DB_USER:-root}"
DB_HOST="${DB_HOST:-127.0.0.1}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
TARGET="$BACKUP_DIR/${DB_NAME}-${STAMP}.sql.gz"

echo "==> 备份 $DB_NAME 到 $TARGET"

# --single-transaction：InnoDB 下不锁表，备份期间业务照常
# --routines --triggers：存储过程与触发器一并带走
# 密码走环境变量 MYSQL_PWD，不出现在进程列表里
MYSQL_PWD="${DB_PASSWORD:-}" mysqldump \
  -h "$DB_HOST" -u "$DB_USER" \
  --single-transaction \
  --routines --triggers \
  --default-character-set=utf8mb4 \
  "$DB_NAME" | gzip > "$TARGET"

SIZE="$(du -h "$TARGET" | cut -f1)"
echo "==> 完成，大小 $SIZE"

# 备份文件为空或过小说明备份失败了，宁可报错也不要留一个假的备份
BYTES="$(wc -c < "$TARGET" | tr -d ' ')"
if [ "$BYTES" -lt 1024 ]; then
  echo "备份文件只有 $BYTES 字节，疑似失败" >&2
  exit 1
fi

echo "==> 清理 $KEEP_DAYS 天前的备份"
find "$BACKUP_DIR" -name "${DB_NAME}-*.sql.gz" -type f -mtime "+$KEEP_DAYS" -print -delete
echo "==> 当前备份："
ls -lh "$BACKUP_DIR" | tail -5
