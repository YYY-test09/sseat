-- ============================================================
-- 简座（sseat）校园自习室预约与管理系统 建库脚本（MySQL 8）
-- 用法：用 Navicat 或命令行执行本脚本，会创建 sseat_db 库和 10 张表
-- ============================================================

DROP DATABASE IF EXISTS sseat_db;
CREATE DATABASE sseat_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE sseat_db;

-- ------------------------------------------------------------
-- 1. 角色表（先建，user 表的 role_code 引用它的编码）
--    menus：该角色可看到的后台菜单 key，逗号分隔（简化的菜单权限）
-- ------------------------------------------------------------
CREATE TABLE `role` (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  role_name   VARCHAR(50)  NOT NULL COMMENT '角色名称',
  role_code   VARCHAR(50)  NOT NULL COMMENT '角色编码(admin/user)',
  menus       VARCHAR(500) NOT NULL DEFAULT '' COMMENT '可见后台菜单key,逗号分隔',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  remark      VARCHAR(200) DEFAULT '' COMMENT '备注',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_role_code (role_code),
  UNIQUE KEY uk_role_name (role_name)
) COMMENT '角色表';

-- ------------------------------------------------------------
-- 2. 用户表
--    password 存 BCrypt 哈希（$2a$10$ 开头 60 位，自带随机盐）；role_code 关联 role 表
--    phone 唯一：短信登录时靠手机号定位用户
-- ------------------------------------------------------------
CREATE TABLE `user` (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  username  VARCHAR(50)  NOT NULL COMMENT '账号',
  password  VARCHAR(64)  NOT NULL COMMENT 'BCrypt加密后的密码',
  nickname  VARCHAR(50)  DEFAULT '' COMMENT '昵称',
  avatar    VARCHAR(255) DEFAULT '' COMMENT '头像URL',
  gender    TINYINT      NOT NULL DEFAULT 0 COMMENT '0未知 1男 2女',
  phone     VARCHAR(20)  DEFAULT NULL COMMENT '手机号(短信登录用)',
  email     VARCHAR(100) DEFAULT '' COMMENT '邮箱',
  address   VARCHAR(200) DEFAULT '' COMMENT '地址',
  intro     VARCHAR(500) DEFAULT '' COMMENT '个人简介',
  role_code VARCHAR(50)  NOT NULL DEFAULT 'user' COMMENT '角色编码',
  status    TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1正常',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username),
  UNIQUE KEY uk_phone (phone)
) COMMENT '用户表';

-- ------------------------------------------------------------
-- 3. 自习室表
--    open_start/open_end：开放整点，如 8~21 表示可约 08:00-21:00
--    max_capacity：每个时段可容纳人数（默认等于座位数）
-- ------------------------------------------------------------
CREATE TABLE study_room (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  name         VARCHAR(100)  NOT NULL COMMENT '自习室名称',
  type         VARCHAR(30)   NOT NULL COMMENT '普通自习室/研讨室/电子设备自习室/VIP自习室',
  description  VARCHAR(1000) DEFAULT '' COMMENT '详细描述',
  price        DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '每小时价格(元)',
  discount     DECIMAL(3,2)  NOT NULL DEFAULT 1.00 COMMENT '折扣0.01~1,如0.8=八折',
  seat_count   INT           NOT NULL COMMENT '座位数',
  max_capacity INT           NOT NULL COMMENT '每时段最大预约人数',
  facilities   VARCHAR(200)  DEFAULT '' COMMENT '设施配置,逗号分隔:空调,电源,WiFi,白板',
  cover_img    VARCHAR(255)  DEFAULT '' COMMENT '展示图片URL',
  open_start   INT NOT NULL DEFAULT 8  COMMENT '开放起始整点',
  open_end     INT NOT NULL DEFAULT 21 COMMENT '开放结束整点',
  like_count   INT NOT NULL DEFAULT 0 COMMENT '点赞数',
  status       TINYINT NOT NULL DEFAULT 1 COMMENT '0停用 1开放预约',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '自习室(场地)表';

-- ------------------------------------------------------------
-- 4. 预约表
--    时段 = start_hour ~ end_hour 的整点区间（用户自选，如 9~12 表示 09:00-12:00）
--    seat_no：座位号 1..seat_count；取消预约时置 NULL 释放座位
--      （利用 MySQL 唯一索引允许多个 NULL 的特性）
--    区间重叠校验在 ReservationService 内完成（start<已有end 且 end>已有start 即冲突）；
--    唯一索引作为"同一起始时段同座位"的最后防线，兜底并发重复提交
-- ------------------------------------------------------------
CREATE TABLE reservation (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id      BIGINT  NOT NULL COMMENT '预约人',
  room_id      BIGINT  NOT NULL COMMENT '自习室',
  reserve_date DATE    NOT NULL COMMENT '预约日期',
  start_hour   INT     NOT NULL COMMENT '起始整点(如9=09:00)',
  end_hour     INT     NOT NULL COMMENT '结束整点,不含(如12=到12:00止)',
  seat_no      INT     DEFAULT NULL COMMENT '座位号,取消后置NULL',
  status       TINYINT NOT NULL DEFAULT 0 COMMENT '0待确认 1已确认 2已完成 3已取消',
  check_in_time DATETIME DEFAULT NULL COMMENT '用户签到时间(开始后15分钟内,超时未签到定时置已取消)',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_seat (room_id, reserve_date, start_hour, seat_no),
  KEY idx_user_date (user_id, reserve_date)
) COMMENT '预约记录表';

-- ------------------------------------------------------------
-- 5. 论坛帖子表（status=1 审核通过才在前台展示）
-- ------------------------------------------------------------
CREATE TABLE post (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id       BIGINT       NOT NULL COMMENT '发帖人',
  title         VARCHAR(100) NOT NULL COMMENT '标题',
  type          VARCHAR(20)  NOT NULL COMMENT '求助/讨论/分享/失物/活动',
  cover_img     VARCHAR(255) DEFAULT '' COMMENT '封面图',
  content       TEXT         NOT NULL COMMENT '正文',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已驳回',
  reject_reason VARCHAR(200) DEFAULT '' COMMENT '驳回原因',
  view_count    INT NOT NULL DEFAULT 0 COMMENT '浏览量',
  like_count    INT NOT NULL DEFAULT 0 COMMENT '点赞数',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status (status)
) COMMENT '论坛帖子表';

-- ------------------------------------------------------------
-- 6. 帖子评论表
-- ------------------------------------------------------------
CREATE TABLE comment (
  id       BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id  BIGINT       NOT NULL COMMENT '所属帖子',
  user_id  BIGINT       NOT NULL COMMENT '评论人',
  content  VARCHAR(500) NOT NULL COMMENT '评论内容',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_post (post_id)
) COMMENT '帖子评论表';

-- ------------------------------------------------------------
-- 7. 意见反馈表
-- ------------------------------------------------------------
CREATE TABLE feedback (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL COMMENT '提交人',
  content    VARCHAR(1000) NOT NULL COMMENT '反馈内容',
  status     TINYINT NOT NULL DEFAULT 0 COMMENT '0待回复 1已回复',
  reply      VARCHAR(1000) DEFAULT '' COMMENT '管理员回复',
  reply_time DATETIME      DEFAULT NULL COMMENT '回复时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user (user_id)
) COMMENT '意见反馈表';

-- ------------------------------------------------------------
-- 8. 资讯表
-- ------------------------------------------------------------
CREATE TABLE news (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  type      VARCHAR(20)  NOT NULL COMMENT '系统公告/功能更新/学习资源/活动通知',
  title     VARCHAR(100) NOT NULL COMMENT '标题',
  author    VARCHAR(50)  DEFAULT '' COMMENT '作者',
  summary   VARCHAR(300) DEFAULT '' COMMENT '简介',
  content   TEXT         NOT NULL COMMENT '正文',
  cover_img VARCHAR(255) DEFAULT '' COMMENT '封面图',
  view_count INT NOT NULL DEFAULT 0 COMMENT '浏览量',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_type (type)
) COMMENT '资讯信息表';

-- ------------------------------------------------------------
-- 9. 收藏表（自习室/帖子通用，target_type 区分）
--    唯一索引：每人对同一内容只能收藏一次
-- ------------------------------------------------------------
CREATE TABLE favorite (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  target_type VARCHAR(20) NOT NULL COMMENT 'room/post',
  target_id   BIGINT      NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fav (user_id, target_type, target_id)
) COMMENT '收藏表';

-- ------------------------------------------------------------
-- 10. 点赞记录表（每人每内容仅一次，再次点击删除记录即取消）
--     like_count 冗余存在 post/study_room 表上，点赞/取消时同步增减
-- ------------------------------------------------------------
CREATE TABLE like_record (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  target_type VARCHAR(20) NOT NULL COMMENT 'room/post',
  target_id   BIGINT      NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_like (user_id, target_type, target_id)
) COMMENT '点赞记录表';

-- ============================================================
-- 种子数据（保证第0步就能登录看到内容）
-- 密码为 BCrypt 哈希（明文 admin123 / 123456，matches() 校验）：
--   admin123 -> $2a$10$rxGcN/fR0K/lECwZbst/heLi7qYvit3IiOovaIkRRdXFCkq0Adt/S
--   123456   -> $2a$10$bP0UfPVhbeZWU2fXt9B1tOkDN2ckvJN85SB7I7qLpWX8mFi78AQ3u
-- ============================================================

INSERT INTO `role` (role_name, role_code, menus, status, remark) VALUES
('超级管理员', 'admin', 'dashboard,room,reservation,post,feedback,news,role,user', 1, '系统内置角色，不可删除'),
('普通用户',   'user',  '', 1, '前台用户角色');

INSERT INTO `user` (username, password, nickname, phone, email, role_code, gender) VALUES
('admin',  '$2a$10$rxGcN/fR0K/lECwZbst/heLi7qYvit3IiOovaIkRRdXFCkq0Adt/S', '系统管理员', '13800000000', 'admin@sseat.com', 'admin', 1),
('student','$2a$10$bP0UfPVhbeZWU2fXt9B1tOkDN2ckvJN85SB7I7qLpWX8mFi78AQ3u',  '测试同学',   '13900000001', 'stu@sseat.com', 'user',  2),
('lihua',  '$2a$10$bP0UfPVhbeZWU2fXt9B1tOkDN2ckvJN85SB7I7qLpWX8mFi78AQ3u',  '李华',       '13900000002', '', 'user', 1);

INSERT INTO study_room (name, type, description, price, discount, seat_count, max_capacity, facilities, open_start, open_end) VALUES
('一号自习室（教学楼A101）', '普通自习室', '安静整洁，适合自习备考，共40座。', 2.00, 0.90, 40, 40, '空调,电源,WiFi', 8, 21),
('研讨室B203',            '研讨室',     '配备白板与圆桌，适合4-8人小组讨论。', 8.00, 1.00, 8,  8,  '空调,白板,电源,WiFi', 8, 21),
('电子设备自习室C305',    '电子设备自习室', '允许使用笔记本电脑与台灯，每座配插口。', 4.00, 0.80, 24, 24, '电源,WiFi,空调', 8, 21),
('VIP静音自习室D101',     'VIP自习室',  '独立隔间、需保持绝对安静，提供饮品。', 15.00, 1.00, 10, 10, '空调,电源,WiFi,白板', 9, 21);

INSERT INTO news (type, title, author, summary, content) VALUES
('系统公告', '自习室开放时间调整通知', '管理员', '十一假期后开放时间调整说明。', '各位同学：\n十一假期结束后，所有自习室开放时间调整为 08:00-21:00，请合理安排预约时间。'),
('学习资源', '考研自习指南分享', '管理员', '座位选择与时间段安排建议。', '建议早起的同学预约 8-10 点时段，该时段人流较少；需要讨论的同学可选择研讨室。'),
('活动通知', '"最美自习室"评选活动开始', '管理员', '为你常用的自习室投票。', '即日起至本月底，可在论坛"活动"分类下为喜欢的自习室打call，点赞数最高的自习室将获得表彰。');
