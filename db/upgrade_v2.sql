-- 存量数据库升级脚本 v2（在 upgrade_end_hour.sql 之后执行）：
-- 1) reservation 加签到时间列（违约自动释放功能）
-- 2) admin 角色菜单加"数据统计"（仪表盘）
-- 用法：mysql -u root -p --default-character-set=utf8mb4 < db/upgrade_v2.sql
USE sseat_db;
ALTER TABLE reservation ADD COLUMN check_in_time DATETIME NULL AFTER status;
UPDATE `role` SET menus = 'dashboard,room,reservation,post,feedback,news,role,user'
WHERE role_code = 'admin' AND menus NOT LIKE 'dashboard%';
