-- 升级脚本：密码存储由「无盐 SHA-256」迁移为 BCrypt（执行一次，旧库数据不丢）
-- 说明：散列不可逆，无法把已存的 SHA-256 值直接转换；
--       已知明文的账号按对应 BCrypt 哈希替换，其余历史注册用户统一重置为 123456。
USE `sseat_db`;

-- 1) admin（明文 admin123）
UPDATE `user` SET password = '$2a$10$rxGcN/fR0K/lECwZbst/heLi7qYvit3IiOovaIkRRdXFCkq0Adt/S'
WHERE username = 'admin';

-- 2) 所有密码仍是 SHA-256 旧格式（64位十六进制）的账号 → 重置为 123456 的 BCrypt 哈希
--    （SHA-256 串不含 '$'，BCrypt 串以 $2a$ 开头，用此特征区分）
UPDATE `user` SET password = '$2a$10$bP0UfPVhbeZWU2fXt9B1tOkDN2ckvJN85SB7I7qLpWX8mFi78AQ3u'
WHERE password NOT LIKE '$2a$%' AND password NOT LIKE '$2b$%' AND password NOT LIKE '$2y$%';

-- 验证：应全部返回 $2a$ 开头
SELECT username, LEFT(password, 7) AS hash_prefix FROM `user`;
