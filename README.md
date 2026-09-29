# 简座 · 校园自习室预约与管理系统（sseat）

**简座**（Simple Seat，代号 sseat）——基于 Spring Boot 3 + Vue 3 的**前后端分离**毕业设计项目。
面向校园场景，提供自习室浏览与选座预约、签到与违约管理、论坛、资讯、意见反馈，
以及带角色权限的完整管理后台。

- 用户端地址：`http://localhost:5173/`（首页/预约/论坛/资讯/反馈/个人中心）
- 管理端地址：`http://localhost:5173/admin/login`（数据仪表盘/自习室/预约/帖子审核/反馈/资讯/角色/用户）
- 在线接口文档：`http://localhost:8080/doc.html`（Knife4j，中文界面，可直接调试接口）

---

## 一、技术栈

### 后端（sseat/）

| 技术 | 版本 | 用途 |
|---|---|---|
| Java (JDK) | 17 | 语言运行时（⚠️ 必须是 17，见"常见问题"） |
| Spring Boot | 3.5.16 | Web 框架 / 依赖管理 |
| Spring Security | 随 Boot | 接口鉴权、URL 白名单、角色控制（`hasRole`） |
| JWT（jjwt） | 0.12.7 | 无状态登录令牌：登录签发、请求头 `Authorization: Bearer <token>` 携带 |
| MyBatis-Plus | 3.5.17 | ORM：单表 CRUD、条件构造器 LambdaQueryWrapper、分页插件 |
| MySQL | 8.0.41 | 主数据库，10 张表，utf8mb4 |
| Redis | 8.x（本机 `D:\redis\Redis-8.10.2-Windows-x64-msys2`） | 登录失败计数（INCR+TTL）、图形验证码（SETEX）、自习室列表/时段余量缓存（Cache-Aside）、抢座分布式锁（SETNX+Lua） |
| Lombok | 随 Boot | `@Data/@RequiredArgsConstructor` 等样板代码消除 |
| Bean Validation | 随 Boot | `@NotBlank/@Size/@Pattern` 入参校验 |
| Knife4j + springdoc-openapi | 4.5.0 / 2.8.13 | OpenAPI 3 在线接口文档（增强 UI） |
| Spring Task | 随 Boot | 定时任务（`@Scheduled` 每 5 分钟巡检预约状态） |

### 前端（sseat-ui/）

| 技术 | 版本 | 用途 |
|---|---|---|
| Vue | 3.5 | 组合式 API（`<script setup>`） |
| Vite | 8 | 开发服务器与打包（`/api` 代理到 8080，开发免跨域） |
| Element Plus | 2.14 | UI 组件库（表格/表单/弹窗/消息） |
| vue-router | 4.6 | 路由 + 导航守卫（登录/角色拦截） |
| Pinia | 4 | 全局状态（当前用户信息） |
| axios | 1.20 | HTTP 封装（请求自动带 token、响应统一处理 401） |
| ECharts | 6.1 | 管理端数据统计图表（趋势折线/排行条形/分布环形） |

### 其他约定

| 项 | 方案 |
|---|---|
| 密码存储 | **BCrypt**（`spring-security-crypto`，自带随机盐的慢哈希，$2a$10$ 开头 60 位）；早期"无盐 SHA-256"方案经 `db/upgrade_bcrypt.sql` 迁移 |
| 图形验证码 | 后端本地绘制，答案存 **Redis**（SETEX 5 分钟自动过期，校验即删的一次性使用），返回 Base64 给 `<img>` |
| 短信验证码 | **模拟模式**：固定码 8888，控制台打印，不接真实网关（预留 `SmsService` 接口） |
| 文件上传 | 存服务器本地磁盘，`/uploads/**` 静态映射，UUID 重命名 |
| 登录保护 | 同一账号密码连续错 5 次锁定 10 分钟——**Redis INCR 原子计数 + TTL 窗口**，重启不丢；Redis 故障时降级放行只记日志 |
| 登录态存储 | `sessionStorage`——每个浏览器标签页独立，可同时演示两个不同账号 |
| 统一响应 | 所有接口返回 `{ code, msg, data }`；异常经全局处理器转中文提示 |

## 二、功能模块

### 用户端
- **注册/登录**：账号密码（图形码）或手机号（短信码）注册；密码/短信两种方式登录；找回密码
- **自习室**：首页卡片列表（类型/关键词筛选、剩余名额实时）、详情页（大图/设施/时段/**点赞/收藏**）
- **预约**：选日期 → **自选开始/结束整点时段**（开放时间内，最短 1 小时）→ 座位网格选座 → 提交
- **我的预约**：**时段开始后 15 分钟内签到**、取消未开始的预约、状态跟踪（待确认/已确认/已完成/已取消）
- **论坛**：浏览/发帖（待审核）/评论/点赞收藏/我的帖子（含驳回原因）
- **资讯**：按类型筛选、详情、浏览量统计
- **意见反馈**：提交、查看管理员回复
- **个人中心**：资料/头像、修改密码、我的预约、我的帖子、我的收藏（自习室+帖子）、我的反馈

### 管理端
- **数据统计仪表盘**：8 张概览卡片 + 未来 7 天预约趋势 + 自习室预约排行 + 预约状态分布 + 帖子类型分布
- **自习室管理**：增删改、封面上传、设施/开放时段/容量配置、启停用
- **预约管理**：关键词/状态/日期筛选、确认/完成/取消、异常数据修改与删除
- **论坛审核**：通过 / 驳回（带原因，用户端可见）/ 删除
- **反馈管理**：回复后用户端同步可见
- **资讯管理**：CRUD + 封面
- **角色管理**：新增角色、勾选菜单权限（登录后侧边栏随之变化）、启停用
- **用户管理**：增改、启用/禁用（旧 token 即时失效）、重置密码、调整角色、删除（级联）

## 三、项目结构

```
SimpleSeat/
├── sseat/                        ── 后端 Spring Boot 项目
│   ├── pom.xml                   Maven 依赖
│   ├── db/
│   │   ├── schema.sql            建库脚本（10 张表 + 种子数据）
│   │   ├── upgrade_end_hour.sql  旧库升级：预约改区间制
│   │   ├── upgrade_v2.sql        旧库升级：签到字段 + 仪表盘菜单
│   │   └── upgrade_bcrypt.sql    旧库升级：密码 SHA-256 → BCrypt
│   ├── process.md                开发流程记录（选型原因/踩坑/修复日志）
│   ├── 111.md                    通用项目开发流程复盘（方法论笔记）
│   └── src/
│       ├── main/java/com/wyb/sseat/
│       │   ├── SseatApplication.java   启动类
│       │   ├── common/       Result 统一响应、GlobalExceptionHandler、
│       │   │                 JwtUtil、CurrentUser、BizException
│       │   ├── config/       SecurityConfig（含 BCrypt PasswordEncoder Bean）、
│       │   │                 JwtAuthenticationFilter、
│       │   │                 MybatisPlusConfig(分页插件)、WebMvcConfig(上传映射)、
│       │   │                 OpenApiConfig(文档元信息)
│       │   ├── controller/   AuthController(/api/auth/**)
│       │   │                 user/ 用户端 8 个 / admin/ 管理端 8 个
│       │   ├── dto/ vo/      请求参数载体 / 返回视图对象
│       │   ├── entity/       10 张表对应实体
│       │   ├── mapper/       MyBatis-Plus BaseMapper
│       │   ├── service/      业务层（预约/签到/统计/登录安全等 13 个）
│       │   └── task/         ReservationTask 定时巡检
│       ├── main/resources/application.yml   端口/数据源/JWT/验证码等
│       └── test/java/.../task/ReservationTaskTest.java  定时任务单元测试
└── sseat-ui/                     ── 前端 Vue 项目（用户端 + 管理端同一工程）
    ├── vite.config.js            /api、/uploads 代理到 8080
    └── src/
        ├── api/                  按模块封装的 axios 接口
        ├── layouts/              UserLayout（顶部导航）/ AdminLayout（侧边菜单按权限显示）
        ├── router/               路由 + 守卫（/admin 需 ROLE_admin）
        ├── store/                Pinia 用户状态（sessionStorage 持久）
        └── views/                user/（首页/详情/预约/论坛/资讯/反馈/登录注册/mine/*）
                                  admin/（Dashboard/Room/Reservation/Post/Feedback/News/Role/User/Login）
```

## 四、快速开始

### 1. 环境要求

| 软件 | 版本 | 备注 |
|---|---|---|
| JDK | **17**（已装 `C:\Users\30837\.jdks\ms-17.0.20.1`） | ⚠️ IDEA 的 `File → Project Structure → SDK` 必须选 17，JDK 25 下 Lombok 静默失效 |
| Node.js | ≥ 18（已装 v24） | |
| MySQL | 8.x（已装 8.0.41） | |
| Redis | 8.10.2（绿色版 `D:\redis\`） | 验证码/登录计数依赖，**后端启动前先运行** `D:\redis\Redis-8.10.2-Windows-x64-msys2\redis-server.exe` |

### 2. 初始化数据库（只需一次）

```bash
cd sseat
mysql -u root -p --default-character-set=utf8mb4 < db/schema.sql
```
> `--default-character-set=utf8mb4` 必须加，否则 Windows 默认 GBK 会把中文种子数据写坏。
> 旧库升级执行 `db/upgrade_end_hour.sql`、`db/upgrade_v2.sql`、`db/upgrade_bcrypt.sql`，数据不丢
> （bcrypt 迁移会把当时自己注册的账号密码统一重置为 123456）。
> 或直接用 Navicat 打开 schema.sql 执行。

### 3. 启动后端

**先确认 Redis 已运行**（双击 `D:\redis\Redis-8.10.2-Windows-x64-msys2\redis-server.exe`，保持窗口不关），
否则登录验证码接口会报"系统繁忙"。IDEA 里运行 `SseatApplication` 的 main 方法即可；命令行：
```bash
cd sseat
export JAVA_HOME="C:\\Users\\30837\\.jdks\\ms-17.0.20.1"
./mvnw.cmd spring-boot:run
```
看到 `Started SseatApplication` 即成功，端口 **8080**。
数据库连接密码在 `src/main/resources/application.yml`（本机已配好；开源/答辩演示前记得改占位符）。

### 4. 启动前端

```bash
cd sseat-ui
npm install        # 首次
npm run dev        # http://localhost:5173
```

### 5. 演示账号

| 端 | 账号 | 密码 | 说明 |
|---|---|---|---|
| 管理后台 `/admin/login` | admin | admin123 | 超级管理员，全部菜单 |
| 用户端 `/login` | student | 123456 | 普通用户（手机 13900000001 可短信登录/找回） |
| 用户端 | lihua | 123456 | 另一普通用户（演示座位/时段冲突用） |

- 图形验证码：看图输入，点击图片可刷新
- 短信验证码：模拟模式固定 **8888**
- 登录防爆破：连错 5 次锁 10 分钟（演示别拿 admin 试错超过 4 次）
- 多账号同开：登录态在 `sessionStorage`，两个标签页分别登录 student/admin 互不影响

## 五、接口文档（Knife4j）

启动后端后打开 `http://localhost:8080/doc.html`：
- 左侧按业务分组（01-认证 ~ 17-管理端统计），中文接口名与参数说明
- **调试**：右上角"Authorize"填入登录返回的 `token` 值（会自动加 `Bearer ` 前缀，
  若手工填头请注意大小写兼容）→ 点开任意接口"发送"即可真实调用
- 备用原生 UI：`http://localhost:8080/swagger-ui/index.html`

## 六、核心设计说明（论文可用）

1. **区间预约与冲突多重防线**：时段为用户自选整点区间 `start_hour~end_hour`（最短 1 小时）；
   提交时四道校验：① 区间内每个整点占用数不超容量；② 同一座位与已有预约不重叠
   （重叠判定 `已有.start < 新.end 且 已有.end > 新.start`）；③ **同一用户与自己的已有预约
   （不限自习室）不允许时段重叠——一人同时段只能保留一个座位**，取消即解除；
   ④ **Redis 分布式锁**（`SET NX`+Lua 原子解锁，室+日期粒度，10s TTL）串行化"读占用→校验→写库"
   临界区，关闭容量校验的并发窗口（已用"1 占用+2 并发"用例验证不超员）；
   ⑤ 数据库唯一索引 `(room_id, reserve_date, start_hour, seat_no)` 最后兜底。
2. **取消即释放**：取消/违约时 `seat_no` 置 NULL，利用 MySQL 唯一索引允许多 NULL 的特性，
   历史取消记录不占座仍可追溯。
3. **签到与状态自动流转**：时段开始后 15 分钟内需签到（`ReservationTask` 每 5 分钟巡检）：
   逾期未签到 → 置"已取消"并释放座位（违约）；已签到且时段结束 → 置"已完成"。
4. **禁用即时生效**：JWT 过滤器每次请求查库核对用户与角色状态，被禁用账号的旧令牌立即失效。
5. **角色-菜单权限**：`role.menus` 存逗号分隔菜单 key，登录后下发，前端侧边栏按 key 显隐，
   后端 `/api/admin/**` 整体 `hasRole("admin")` 兜底。
6. **审核闭环**：帖子默认待审核，`status=1` 才进前台；驳回带原因，作者端可见；编辑已发布帖重新进审核。
7. **点赞/收藏通用模型**：`like_record`/`favorite` 以 `target_type + target_id` 定位内容，
   自习室与帖子共用一套表与接口；冗余计数用 SQL 原子加减（`GREATEST(count±1,0)`）。
8. **登录安全**：BCrypt 加盐慢哈希存储密码（防彩虹表/GPU 撞库）；失败 5 次锁 10 分钟
   （`LoginSecurityService` 用 Redis `INCR+EXPIRE` 原子计数，重启不丢、多实例共享，
   Redis 故障时降级放行保证主流程可用）；JWT 密钥为随机 80 位串；认证头方案名按 RFC 7235 大小写不敏感解析。
9. **验证码与计数的 Redis 化**：`captcha:{key}` SETEX 5 分钟——TTL 自动过期天然替代内存方案的
   "手动清理 + 重启丢失 + 单实例独占"三个问题；校验后立即 DELETE 保证一次性。
10. **读缓存策略（Cache-Aside + 粗粒度失效）**：前台自习室列表 TTL 60s、"当日占用数据"（时段余量
    与座位网格共用一份）TTL 30s；失效不靠 SCAN 删散列键，而是 key 里嵌版本号、写操作 INCR 版本号
    整体作废（旧键等 TTL 自然回收）。卡片"剩余名额"本就是参考值，正确性由预约接口的实时校验+锁兜底；
    缓存读写全部 try/catch——Redis 故障自动回源查库，可用性优先。

## 七、测试

```bash
export JAVA_HOME="C:\\Users\\30837\\.jdks\\ms-17.0.20.1"
./mvnw.cmd test          # 定时任务单元测试（违约释放/正常完结/未来预约不受扰）
cd ../sseat-ui && npm run build   # 前端生产构建通过性检查
```

## 八、答辩前检查清单

- [ ] **先启动 Redis 再启动后端**（演示中途 Redis 窗口被关会导致验证码接口不可用）
- [ ] `application.yml` 里 `sseat.captcha.debug-reveal` 改为 `false`（关闭验证码答案回显）
- [ ] `application.yml` 数据库密码改为占位符或演示环境专用口令
- [ ] `mybatis-plus.configuration.log-impl` 两行注释掉（不再刷屏 SQL 日志）
- [ ] 演示环境：后端 8080 + 前端 `npm run dev`；或 `npm run build` 打包版
- [ ] 打开 `http://localhost:8080/doc.html` 确认接口文档可展示（论文截图用）
- [ ] 端到端走一遍演示路径：注册→登录→预约（含"同时段重复预约被拒"）→签到→审核→回复→仪表盘
- [ ] 若接真实短信：实现 `SmsService.send()` 内部并置 `sseat.sms.mock: false`

## 九、常见问题

| 现象 | 原因与解决 |
|---|---|
| 启动报 `Port 8080 was already in use` | 旧进程没关：任务管理器结束 java.exe |
| 前端提示"网络异常，请检查后端是否启动" | 后端没起来，先启动后端 |
| 登录后操作 401 | JWT 有效期 24h，过期需重登；管理员禁用账号也会即时 401（特性） |
| 中文乱码（数据库） | 导入 SQL 加 `--default-character-set=utf8mb4` 重导 |
| 编译报 getter 找不到符号 | 用了 JDK ≥24，切回 17 |
| 选了开始时间没有"往后 1 小时"的结束项 | 旧版前端缓存，刷新或重启 `npm run dev` |

## 十、已知局限与展望（论文"不足"一节可用）

1. **锁的策略偏保守**：抢座锁为 fail-fast（抢不到直接提示稍后再试，不排队自旋），锁粒度是"室+日期"，
   跨自习室的用户维度并发校验仍依赖应用层顺序读（窗口极小）；可改进为排队重试或按"用户"再加一把锁。
   ~~容量校验应用层竞态窗口~~ 已由分布式锁关闭（2026-09-29，见 process.md 十二）。
2. **短信为模拟网关**：固定 8888；真实场景需对接运营商并加发送频控。
3. **Redis 单机无持久化**：演示用单机 Redis（RDB 默认）；生产应主从/哨兵并评估 AOF。
4. **登录保护只做账号维度**：未叠加 IP 维度频控与行为验证码。
5. **未做接口级自动化测试与 CI/CD**、无环境分离（dev/prod）——与工业标准的差距，见 `111.md`。

> 历史条目"登录计数/验证码存内存"已随 Redis 化解决（2026-09-29，见 process.md 十一）。

## 十一、相关文档

| 文件 | 内容 |
|---|---|
| `process.md` | 开发全流程记录：选型理由、里程碑明细、踩坑与修复日志（含 401 大小写缺陷复盘） |
| `111.md` | 以本项目为例的通用项目编写流程（需求→选型→建表→骨架→纵切→收尾） |
| `db/*.sql` | 建库与升级脚本 |
