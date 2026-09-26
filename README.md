# 校园自习室预约与管理系统（sseat）

基于 Spring Boot 3.5 + Vue 3 的前后端分离毕业设计项目。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.5.16、Spring Security + JWT（jjwt 0.12）、MyBatis-Plus 3.5.17、MySQL 8、Lombok、JDK 17 |
| 前端 | Vue 3、Vite、Element Plus、vue-router、pinia、axios |
| 鉴权 | 登录签发 JWT，请求头 `Authorization: Bearer <token>`；每请求查库核对用户/角色状态 |
| 密码 | SHA-256 十六进制加密存储 |
| 验证码 | 后端本地生成图形码（内存存储、一次性）；短信为模拟模式（固定码 8888） |

## 目录结构

```
SimpleSeat/
├── sseat/                 后端 Spring Boot 项目
│   ├── db/schema.sql      建库脚本（10张表+种子数据）
│   ├── process.md         开发流程记录（选型原因/踩坑/进度）
│   └── src/main/java/com/wyb/sseat/
│       ├── common/        统一响应Result、异常处理、JwtUtil、Sha256Util等
│       ├── config/        SecurityConfig、JWT过滤器、MyBatis-Plus、上传映射
│       ├── controller/    auth前台认证 / user用户端 / admin管理端
│       ├── dto/ vo/       请求参数 / 返回视图
│       ├── entity/ mapper/ service/ task/
├── sseat-ui/              前端 Vue 项目（用户端 / + 管理端 /admin）
```

## 一、环境准备

1. **JDK 17**（已装：`C:\Users\30837\.jdks\ms-17.0.20.1`）
   > ⚠️ 重要：IDEA 中 `File → Project Structure → SDK` 必须选 **17**，不要用手头默认的 JDK 25
   > （Lombok 在 25 下会静默失效，代码里 getter 全部"找不到符号"）。
2. **Node.js ≥ 18**（已装 v24）
3. **MySQL 8**（已装 8.0.41）

## 二、初始化数据库（只需一次）

方式 A（命令行，Git Bash）：
```bash
cd sseat
mysql -u root -p --default-character-set=utf8mb4 < db/schema.sql
```
> `--default-character-set=utf8mb4` 必须加，否则 Windows 默认 GBK 会把中文种子数据写坏。

方式 B：用 Navicat 打开 `db/schema.sql` 执行。

> 如果是**旧版数据库**（预约还是单小时时段）升级，执行 `db/upgrade_end_hour.sql` 即可，数据不丢。

## 三、启动后端

命令行（在项目根目录 `sseat/`）：
```bash
export JAVA_HOME="C:\\Users\\30837\\.jdks\\ms-17.0.20.1"
./mvnw.cmd spring-boot:run
```
IDEA 里直接运行 `SseatApplication` 的 main 方法即可。

看到 `Started SseatApplication` 即成功，端口 **8080**。
数据库密码在 `src/main/resources/application.yml` 的 `spring.datasource.password`（当前已配好）。

## 四、启动前端

```bash
cd sseat-ui
npm install        # 首次
npm run dev        # 开发模式，地址 http://localhost:5173
```
生产打包：`npm run build`（产物在 dist/）。

Vite 已配置代理：前端所有 `/api`、`/uploads` 请求自动转发到 8080，无需处理跨域。

## 五、演示账号

| 端 | 账号 | 密码 | 说明 |
|---|---|---|---|
| 管理后台 http://localhost:5173/admin/login | admin | admin123 | 超级管理员，拥有全部菜单 |
| 用户端 http://localhost:5173/login | student | 123456 | 普通用户（手机 13900000001 可用于短信登录/找回） |
| 用户端 | lihua | 123456 | 另一个普通用户（演示同座位冲突用） |

- 图形验证码：页面上看图输入，点击图片可刷新。
- 短信验证码：**模拟模式，固定输入 8888**（后端控制台也会打印）。
- 管理端"重置密码"会把用户密码重置为 `123456`。
- **同时演示两个账号**：登录状态存在 `sessionStorage`（每个浏览器标签页独立），
  开两个标签页分别登录 student 和 admin 互不影响；代价是关闭标签页后需重新登录。

## 六、功能清单与演示路径

**用户端**：注册（图形码/手机号）→ 登录（密码/短信）→ 首页浏览自习室（卡片/筛选/剩余名额）
→ 详情页（大图/设施）→ 预约（选日期→选开始/结束整点时段→座位网格→提交）→ 我的预约（取消）
→ 论坛（浏览/发帖/评论/点赞收藏）→ 资讯（筛选/详情）→ 意见反馈（提交/看回复）
→ 个人中心（资料/头像/改密/我的帖子/我的收藏）。

**管理端**：登录（仅 ROLE_admin）→ 自习室座位管理（增删改+设施+时段+容量+图片）
→ 预约管理（筛选/确认/完成/取消/改删异常数据）→ 论坛管理（通过/驳回带原因/删除）
→ 反馈管理（回复→用户端同步可见）→ 资讯管理 → 角色管理（菜单权限勾选）→ 用户管理（增改/启禁用/重置密码/调角色/删除级联）。

## 七、答辩前检查清单

- [ ] `application.yml` 里 `sseat.captcha.debug-reveal` 改为 `false`（关闭验证码答案回显）
- [ ] `mybatis-plus.configuration.log-impl` 那两行注释掉（不再刷屏 SQL 日志）
- [ ] 演示环境：后端 8080 + 前端 `npm run dev`；或用打包版
- [ ] 若接真实短信：实现 `SmsService.send()` 内部并置 `sseat.sms.mock: false`

## 八、常见问题

| 现象 | 原因与解决 |
|---|---|
| 启动报 `Port 8080 was already in use` | 旧进程没关：任务管理器结束 java.exe，或重启电脑 |
| 前端提示"网络异常，请检查后端是否启动" | 后端没起来，先启动后端 |
| 登录后点任何东西 401 | JWT 有效期 24h，过期需重新登录；管理员禁用账号也会即时 401（这是特性） |
| 中文乱码（数据库） | 导入 SQL 时加 `--default-character-set=utf8mb4` 重导 |
| 编译报 getter 找不到符号 | IDE/命令行用的 JDK ≥24，切回 JDK 17 |

## 九、核心设计说明（论文可用）

1. **区间预约与冲突双重防线**：预约时段为用户自选的整点区间 `start_hour~end_hour`；
   提交时先按（自习室、日期）查有效预约做两道内存校验——①区间内每个整点占用数不超过容量
   ②同一座位与已有预约时间重叠即拒绝（重叠判定：`已有.start < 新.end 且 已有.end > 新.start`）；
   数据库唯一索引 `(room_id, reserve_date, start_hour, seat_no)` 兜底并发重复提交，冲突转友好提示。
2. **取消即释放**：取消预约时 `seat_no` 置 NULL，利用 MySQL 唯一索引允许多 NULL 的特性，
   历史取消记录不占座仍可追溯。
3. **禁用即时生效**：JWT 过滤器每次请求都查库核对用户与角色状态，被禁用账号的旧令牌立即失效。
4. **状态自动流转**：定时任务（`ReservationTask`，每小时 01 分）把已整体结束（过了结束整点）
   的"待确认/已确认"预约自动置"已完成"。
5. **审核闭环**：帖子默认待审核，`status=1` 才进前台列表/搜索；驳回带原因，作者端可见；
   编辑已发布帖会重新进入待审核。
6. **点赞/收藏通用模型**：`like_record`/`favorite` 以 `target_type + target_id` 定位内容，
   自习室与帖子共用一套表与接口；点赞数冗余计数用 SQL 原子加减（`GREATEST(count±1,0)`）。
