-- 存量数据库升级脚本（老库→区间预约）：给 reservation 表加 end_hour 列
-- 新库不需要执行本脚本（schema.sql 已包含 end_hour）
-- 用法：mysql -u root -p --default-character-set=utf8mb4 < db/upgrade_end_hour.sql
USE sseat_db;
ALTER TABLE reservation ADD COLUMN end_hour INT NULL AFTER start_hour;
-- 存量数据按原来的一小时时段补齐：结束=开始+1
UPDATE reservation SET end_hour = start_hour + 1 WHERE end_hour IS NULL;
ALTER TABLE reservation MODIFY COLUMN end_hour INT NOT NULL COMMENT '结束整点,不含(如12=到12:00止)';
