# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指导。

## 项目概述

路由监控系统 — 针对**锐捷 RG-MA3063**路由器（地址 `192.168.10.1`）的 Web 管控应用。通过代理路由器 HTTP API，提供终端设备管理、黑名单、监管设备分时段控制、白名单监控、连接日志审计和**PC 远程管控**（进程查看/锁屏/关机/弹窗消息）功能。

## 技术栈

- **后端**: Java 21+, SpringBoot 2.5, Maven 3.9+, MyBatis-Plus 3.5, MySQL, Redis
- **前端**: Vue 3 + TypeScript, Vite 5, Ant Design Vue 4, Axios
- **PC Agent**: Go 1.21+（Windows 7 x86_64），单二进制无运行时依赖
- **无登录机制** — 应用本身无需用户登录，仅管理路由器的 SessionID。

## 常用命令

```bash
# 后端（在 backend/ 目录下执行）
mvn clean package -DskipTests          # 构建
mvn spring-boot:run                    # 启动（端口 9070）

# 前端（在 frontend/ 目录下执行）
npm run dev            # 开发服务器（端口 8888，/api 代理至 127.0.0.1:9070）
npm run build          # 类型检查 + 生产构建（base 路径: /router）
npm run preview        # 预览生产构建

# PC Agent（在 pc-agent/ 目录下执行，需安装 Go）
go build -o pc-agent.exe -ldflags="-s -w"    # 构建 Windows 二进制
GOOS=windows GOARCH=amd64 go build ...       # 交叉编译 Windows x64
```

## 架构

### 后端 (`backend/`) — SpringBoot，端口 9070

```
controller/     → REST 控制器（Terminal, Blacklist, Supervision, Whitelist, Log, Dashboard, PcDevice, PcCommand, PcAgent, PcMessageTemplate）
service/        → 接口 + impl/（业务逻辑）
  RouterApiService  → **核心**：封装所有对路由器的 HTTP 调用，自动维护 SessionID，过期自动重登
  PcDeviceService  → PC 设备管理：注册、心跳、Token 鉴权、在线状态判断
  PcCommandService → 指令全生命周期：创建、轮询（pending→sent）、结果上报（success/failed）
scheduler/      → MonitorScheduler：@Scheduled 每 10 秒执行监管巡检和白名单检查
config/         → RouterConfig（路由器登录凭据）、WebMvcConfig（CORS）、MybatisPlusConfig、RedisConfig
model/
  dto/          → TerminalDevice, DeviceDetail, DashboardStats, BlacklistItem, SupervisionVO
  entity/       → WhitelistDevice, ConnectionLog, SupervisionRule, RouterSession, PcDevice, PcCommand, PcMessageTemplate（MyBatis-Plus 实体）
  vo/           → PcDeviceVO, PcCommandVO（PC 模块展示层 VO）
mapper/         → MyBatis-Plus mapper（无 XML，使用 LambdaQueryWrapper）
common/         → BaseResponse, PageRequest, ResultUtils
exception/      → ErrorCode
```

**Session 管理**：`RouterApiService` 将路由器 SessionID 缓存在 Redis（键 `router:session_id`，TTL 30 分钟）并持久化到 MySQL（`router_session` 表）。调用路由器 API 时如果返回 `code: -1, msg: "AuthRequired"`，自动重新登录并重试一次。

### 前端 (`frontend/`) — Vue 3 + Ant Design，端口 8888

```
src/
  api/          → Axios 实例（baseURL: /api）+ 各模块 API 封装（含 pc.ts）
  router/       → Vue Router 路由：/, /terminal, /blacklist, /supervision, /whitelist, /logs, /pc, /pc/detail/:id
  views/        → 页面组件：Dashboard, Terminal, Blacklist, Supervision, Whitelist, Logs, PcManagement, PcDetail
  components/   → AppLayout.vue（侧边栏导航 + 移动端汉堡菜单）
  styles/       → global.css（移动端适配、侧边栏布局）
```

Vite 开发服务器将 `/api` 代理至 `http://127.0.0.1:9070`。生产构建 base 路径为 `/router`。

### 调度器 (`MonitorScheduler`)

每 10 秒执行一次（`@Scheduled(fixedDelay = 10000)`）：

1. **午夜重置**：还原临时延时，清零 `usedToday`/`sessionUsed` 计数器
2. 检查延时到期计时器，到期自动将设备加入黑名单
3. 遍历所有活跃监管规则，对照当前在线设备：
   - **延时模式**：跳过监管巡视，仅累积使用时长
   - **不在时间段内**：拉黑设备（断网）
   - **在时间段内**：累积时长，检查单次时长和每日限额，超限拉黑
   - **时间段切换**：进入新的时间段时自动解除黑名单
4. 检查非白名单设备在线超过 30 分钟 → 记录违规日志

### 核心概念：监管时间段

监管规则的 `timeSlots` 以 JSON 数组存储，格式为 `["HH:mm-HH:mm", ...]`。调度器判断当前时间落入哪个时间段索引。在时间段 N 因超限被拉黑的设备，当时段 M（M ≠ N）开始时会被自动解除黑名单。

### PC 远程管控

通过以下三层架构实现 Windows PC 的远程管控：

**后端** (`controller/PcDeviceController` + `PcCommandController` + `PcAgentController`)：
- Web API：设备 CRUD、指令下发、指令历史查询
- Agent API：注册（MAC/IP/主机名 → 返回 token）、心跳、指令轮询、结果上报
- 鉴权：每个 Agent 持有独立 `agent_token`，通过 `X-Agent-Token` Header 验证

**前端** (`views/PcManagement.vue` + `PcDetail.vue`)：
- 设备列表页：表格展示主机名/IP/MAC/状态/心跳，支持快捷锁屏和关机
- 设备详情页：基本信息卡片、进程列表表格、锁屏/关机/重启/注销/弹窗消息按钮、指令历史

**PC Agent** (`pc-agent/` — Go 程序，~8MB 单二进制)：
- 通信方式：HTTP 短轮询（每 5 秒），心跳与指令轮询合一
- 核心循环：注册 → 心跳上报 → 指令轮询 → 执行 → 回传结果
- 指令实现：
  - `PROCESSES`  → `tasklist /FO CSV /NH`
  - `LOCK_SCREEN` → `rundll32.exe user32.dll,LockWorkStation`
  - `SHUTDOWN`    → `shutdown /s /t 30`
  - `RESTART`     → `shutdown /r /t 30`
  - `LOGOFF`      → `shutdown /l`
  - `KILL_PROCESS` → `taskkill /F /PID`
  - `SHOW_MESSAGE` → PowerShell `[System.Windows.Forms.MessageBox]::Show()` + `msg.exe` 回退
- 运行方式：nssm 注册为 Windows 服务，开机自启

**数据库表：** `pc_device`（设备注册 + 心跳）、`pc_command`（指令生命周期）、`pc_message_template`（弹窗消息预设模板）

## 外部依赖

- **MySQL** 地址 `192.168.10.122:3306`（数据库：`router_monitor`）
- **Redis** 地址 `192.168.10.122:6379`
- **路由器 API** 地址 `192.168.10.1`（接口文档见 @RouterApi.md）

## 重要规则

Git禁止推送到**main/master**分支，推送到Suen9分支

Git commit 消息符合 Conventional Commits（中文），要说明这个版本修改了什么

git仓库地址https://github.com/Suen9/router-jk-web

功能设计按里程碑先后顺序进行，每个功能测试好后等待指令上传git仓库。

自动更新@CLAUDE.md

