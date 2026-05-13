# PC 远程管控方案设计

## Context

现有路由监控系统（锐捷 RG-MA3063）仅能管控接入路由器的终端设备（手机、平板等）的上网权限。需要设计一套 PC 客户端程序，实现对 Windows 7 电脑的深度管控：远程进程查看、远程锁屏、远程关机，与现有系统统一管理。

关键约束：PC 为 Windows 7 x86_64，内存仅 4GB，必须使用超轻量级方案。

---

## 整体架构

```
┌──────────────────────────────────────────────────────────────┐
│                    Web 前端（Vue 3）                          │
│   PC 管理页面：设备列表 / 进程查看 / 锁屏 / 关机             │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTP (BaseResponse)
                           ▼
┌──────────────────────────────────────────────────────────────┐
│              后端 SpringBoot（端口 9070）                      │
│                                                              │
│  ┌─────────────────┐  ┌──────────────────┐                    │
│  │ PcDeviceController│  │ PcCommandController│                 │
│  │ /api/pc/device/*  │  │ /api/pc/command/*  │                │
│  └────────┬─────────┘  └────────┬──────────┘                   │
│           │                     │                              │
│  ┌────────▼─────────────────────▼──────────┐                   │
│  │           PC Agent API                   │                   │
│  │   /api/pc/agent/register                │                   │
│  │   /api/pc/agent/heartbeat               │                   │
│  │   /api/pc/agent/poll                    │                   │
│  │   /api/pc/agent/report                  │                   │
│  └────────▲────────────────────────────────┘                   │
│           │ HTTP（无状态轮询）                                  │
│           │                                                    │
│  ┌────────┴────────┐  ┌─────────────┐                         │
│  │  pc_device 表    │  │ pc_command 表│                        │
│  │  (MySQL)         │  │ (MySQL)      │                        │
│  └─────────────────┘  └──────────────┘                         │
└───────────────────────────────────────────────────────────────┘
           │ HTTP polling（每 5 秒）
           ▼
┌──────────────────────────────────────────────────────────────┐
│            PC Agent（Go 单文件，约 8MB，无运行时依赖）          │
│                                                              │
│  注册 → 心跳上报(含IP/MAC/主机名) → 指令轮询 → 执行 → 回传结果 │
│                                                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                    │
│  │ getProcess│  │ lockScreen│  │ shutdown  │                  │
│  │ tasklist  │  │ rundll32 │  │ shutdown /s│                 │
│  └──────────┘  └──────────┘  └──────────┘                    │
│                                                              │
│  运行方式：Windows 服务（nssm 注册）或 开机自启目录             │
└──────────────────────────────────────────────────────────────┘
```

---

## 技术选型

| 层面 | 选择 | 理由 |
|---|---|---|
| PC Agent 语言 | **Go 1.20.14**（最高支持 Windows 7 的版本） | 单二进制无运行时依赖，内存占用 ~8MB，原生 Windows 7 兼容。Go 1.21+ 已放弃 Win7 支持 |
| 通信方式 | **HTTP 短轮询**（5 秒间隔） | 简单可靠，Windows 7 下 WebSocket 连接不稳定，4G 内存下长连接资源开销大 |
| Agent 运行方式 | **Windows 服务（nssm）** | 开机自启、后台静默运行、崩溃自动重启 |
| 编译目标 | `GOOS=windows GOARCH=amd64` | 匹配 Windows 7 x86_64 |

---

## 数据库表设计

沿用现有 `utf8mb4`、`DATETIME`、`COMMENT` 风格。

### pc_device — PC 设备表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT AUTO_INCREMENT | 主键 |
| hostname | VARCHAR(100) | 主机名 |
| ip | VARCHAR(50) | IP 地址（最后心跳时） |
| mac | VARCHAR(50) | MAC 地址（唯一标识） |
| os_version | VARCHAR(200) | 操作系统版本 |
| agent_version | VARCHAR(20) | 客户端版本 |
| status | VARCHAR(20) DEFAULT 'offline' | online/offline |
| agent_token | VARCHAR(64) | 认证令牌（注册时生成） |
| last_heartbeat | DATETIME | 最后心跳时间 |
| remark | VARCHAR(500) | 备注 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

### pc_command — 指令表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT AUTO_INCREMENT | 主键 |
| pc_device_id | BIGINT NOT NULL | 目标 PC ID |
| command_type | VARCHAR(30) NOT NULL | PROCESSES / LOCK_SCREEN / SHUTDOWN / RESTART / LOGOFF / KILL_PROCESS / SHOW_MESSAGE |
| params | VARCHAR(1000) | 指令参数（JSON） |
| status | VARCHAR(20) DEFAULT 'pending' | pending / sent / executing / success / failed |
| result | TEXT | 执行结果（进程列表 JSON 或执行日志） |
| error_message | VARCHAR(500) | 错误信息 |
| created_by | VARCHAR(50) DEFAULT 'web' | 操作来源 |
| create_time | DATETIME | 创建时间 |
| send_time | DATETIME | 下发时间 |
| complete_time | DATETIME | 完成时间 |

索引：
- `(pc_device_id, status)` — 按设备查询待处理指令
- `(status, create_time)` — Agent 轮询 pending 指令

### pc_message_template — 弹窗消息预设表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT AUTO_INCREMENT | 主键 |
| content | VARCHAR(500) NOT NULL | 消息内容 |
| sort_order | INT DEFAULT 0 | 排序号 |
| create_time | DATETIME | 创建时间 |

---

## 后端 API 设计

### 1. PC 管理接口（Web 前端使用）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/pc/device/list` | 设备列表（支持 keyword 关键字搜索） |
| GET | `/api/pc/device/{id}` | 设备详情 |
| POST | `/api/pc/device/add` | 手动添加设备 |
| PUT | `/api/pc/device/{id}` | 更新设备信息 |
| DELETE | `/api/pc/device/{id}` | 删除设备 |
| POST | `/api/pc/device/{id}/sendCommand` | 向指定 PC 下发指令 |

### 2. PC 指令接口（Web 前端使用）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/pc/command/list` | 指令历史（支持 pcDeviceId / commandType / status 筛选 + 分页） |
| GET | `/api/pc/command/{id}` | 指令详情（含执行结果） |

### 3. PC Agent 接口（Agent 专用，无前端路由）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/pc/agent/register` | Agent 注册。Body: `{mac, hostname, ip, osVersion, agentVersion}` → 返回 `{deviceId, agentToken}` |
| POST | `/api/pc/agent/heartbeat` | 心跳上报。Header: `X-Agent-Token` |
| GET | `/api/pc/agent/poll` | 轮询待执行指令。返回第一条 pending 指令，置为 sent |
| POST | `/api/pc/agent/report` | 上报执行结果。Body: `{commandId, success, result, errorMessage}` |

**鉴权方式：** Agent 接口均需 Header `X-Agent-Token`，注册后返回并在本地缓存，每次请求验证有效性。

---

## 后端文件清单

| 文件 | 说明 |
|---|---|
| `controller/PcDeviceController.java` | PC 设备 CRUD + 指令下发 |
| `controller/PcCommandController.java` | 指令历史查询 |
| `controller/PcAgentController.java` | Agent 通信四接口（register / heartbeat / poll / report） |
| `service/PcDeviceService.java` | 设备服务接口 |
| `service/impl/PcDeviceServiceImpl.java` | 设备管理实现（注册 / 心跳 / Token 生成验证 / 在线判断） |
| `service/PcCommandService.java` | 指令服务接口 |
| `service/impl/PcCommandServiceImpl.java` | 指令生命周期管理（创建 / 轮询 / 完成） |
| `model/entity/PcDevice.java` | PC 设备实体 |
| `model/entity/PcCommand.java` | 指令实体 |
| `model/vo/PcDeviceVO.java` | 设备列表 VO（含实时 online/offline 状态计算） |
| `model/vo/PcCommandVO.java` | 指令展示 VO（含关联设备名） |
| `mapper/PcDeviceMapper.java` | 设备 Mapper |
| `mapper/PcCommandMapper.java` | 指令 Mapper |
| `controller/PcMessageTemplateController.java` | 弹窗消息预设 CRUD |
| `service/PcMessageTemplateService.java` | 消息预设服务接口 |
| `service/impl/PcMessageTemplateServiceImpl.java` | 消息预设服务实现 |
| `model/entity/PcMessageTemplate.java` | 消息预设实体 |
| `mapper/PcMessageTemplateMapper.java` | 消息预设 Mapper |

---

## PC Agent 设计（Go）

### 目录结构

```
pc-agent/
├── main.go                # 入口：加载配置 → 注册/加载 token → 主循环
├── config.go              # Config / TokenStore 结构体 + 文件读写
├── register.go            # 注册逻辑（采集 MAC/IP/主机名 → 调用后端 → 缓存 token）
├── client.go              # HTTP 客户端（封装后端 Agent API 调用）
├── executor.go            # Executor 接口 + Execute 分发
├── executor_windows.go    # Windows 实现（build tag: windows）
├── executor_default.go    # 非 Windows 回退（build tag: !windows）
└── config.json            # 部署配置文件
```

### 核心流程

```
启动
  ├─ 读取 config.json（backend_url）
  ├─ 读取 .agent_token.json
  │   ├─ 有 token → 直接使用
  │   └─ 无 token → 注册（POST /register）→ 缓存 token
  ├─ 启动主循环（每 5 秒）：
  │   1. POST /heartbeat（上报心跳和 IP）
  │   2. GET /poll（拉取待执行指令）
  │   3. 有指令 → Execute → POST /report 回传结果
  └─ 捕获 SIGINT/SIGTERM → 退出
```

### 指令执行实现

| 指令类型 | Windows 7 API | 说明 |
|---|---|---|
| PROCESSES | `tasklist /FO CSV /NH` | 解析 CSV 按内存降序返回前 20 条进程 |
| LOCK_SCREEN | `rundll32.exe user32.dll,LockWorkStation` | 立即锁屏 |
| SHUTDOWN | `shutdown /s /t 30` | 30 秒延迟关机，可取消 |
| RESTART | `shutdown /r /t 30` | 30 秒延迟重启 |
| LOGOFF | `shutdown /l` | 注销当前用户 |
| KILL_PROCESS | `taskkill /F /PID` | 强制结束指定 PID 进程 |
| SHOW_MESSAGE | PowerShell MessageBox / `msg.exe` 回退 | 弹窗消息提示。Windows 服务环境下 msg.exe 优先（确保置顶），PowerShell 作为回退 |

### 运行方式

1. **Windows 服务（推荐）：**
   ```
   nssm install PCAgent "C:\Program Files\PCAgent\pc-agent.exe"
   nssm start PCAgent
   ```
2. **开机自启：** 创建快捷方式到 `shell:startup`

### 资源占用

- 磁盘：~8MB（二进制）+ ~1KB（配置文件）
- 内存：~5-10MB
- CPU：近乎为 0（大部分时间 sleep）

---

## PC 弹窗消息功能

### 数据流

```
PcDetail.vue 发送弹窗按钮
  → 模态框输入消息 / 选择预设
  → POST /api/pc/device/{id}/sendCommand { commandType: "SHOW_MESSAGE", params: '{"message":"..."}' }
  → PcCommandService.create() → pc_command 表 status=pending
  → PC Agent 轮询 → 执行 PowerShell MessageBox / msg.exe → 上报结果
```

### 后端

| 文件 | 说明 |
|---|---|
| `controller/PcMessageTemplateController.java` | 消息预设 CRUD（路由 `/api/pc/message-template`） |
| `service/PcMessageTemplateService.java` | 接口 |
| `service/impl/PcMessageTemplateServiceImpl.java` | 实现 |
| `model/entity/PcMessageTemplate.java` | 实体类 |
| `mapper/PcMessageTemplateMapper.java` | Mapper |

### 前端

- PcDetail.vue 增加"发送弹窗消息"按钮 → 模态框含输入框 + 预设标签列表
- 预设标签可点击填充、可删除，底部可新增
- API: `pc.ts` 新增 `getMessageTemplates()` / `addMessageTemplate()` / `removeMessageTemplate()`

---

## 前端扩展

### 新增路由

| 路径 | 组件 | 说明 |
|---|---|---|
| `/pc` | PcManagement.vue | PC 设备列表 + 状态总览 |
| `/pc/detail/:id` | PcDetail.vue | 设备详情 + 进程列表 + 操作按钮 |

### 新增文件

| 文件 | 说明 |
|---|---|
| `api/pc.ts` | 8 个 API 封装函数 |
| `views/PcManagement.vue` | PC 列表页 |
| `views/PcDetail.vue` | PC 详情页 |

### 关键 UI

**PcManagement.vue：**
- 表格：主机名 / IP / MAC / 系统版本 / 状态（绿色在线 / 灰色离线）/ 最后心跳 / 操作
- 操作：详情 / 锁屏 / 关机（带二次确认弹窗）
- 状态规则：`lastHeartbeat < now - 30s` → offline

**PcDetail.vue：**
- 基本信息卡片（主机名、IP、MAC、系统版本、Agent 版本、状态、心跳时间）
- 操作区：获取进程 / 锁屏 / 关机 / 重启 / 注销 / 发送弹窗消息
- 监管设置区：绑定状态 / 时间段信息 / 已用时长 / 剩余时间 / 延长 / 绑定/解绑操作
- 进程列表（可筛选）：进程名 / PID / 会话名 / 内存使用
- 指令历史列表：指令类型 / 状态 / 创建时间 / 完成时间 / 结果

---

## 部署流程

1. **数据库：** 执行 `schema.sql` 中的 `pc_device`、`pc_command` 建表语句
2. **后端：** `mvn clean package -DskipTests` 构建，部署到服务器
3. **前端：** `npm run build` 构建，部署到 nginx 的 `/router` 路径
4. **PC Agent：**
   - 填写 `config.json` 中的 `backend_url: "http://192.168.10.122:9070"`
   - 将 `pc-agent.exe` + `config.json` 拷贝到目标 PC
   - 以管理员身份运行一次完成注册
   - 用 nssm 注册为 Windows 服务

---

## 安全设计

- Agent Token 在本地 `.agent_token.json` 存储，文件权限 0600
- 关机 / 重启等破坏性操作在前端增加二次确认弹窗
- Agent 接口在 `PcAgentController` 中校验 `X-Agent-Token`，非法请求返回 403
- 进程列表信息只对已认证用户可见

---

## PC 监管绑定（MAC 匹配方案）

通过 `supervision_rule.mac` 与 `pc_device.mac` 匹配，将监管规则绑定到 PC 设备。PC 规则不通过路由器黑名单限制，而是通过 Agent 指令（LOCK_SCREEN + SHOW_MESSAGE）执行。

### 绑定方式

`supervision_rule.mac` == `pc_device.mac`，无需新增外键字段，零 schema 变更。

### PC 规则 vs 终端规则处理差异

| 维度 | 终端监管 | PC 监管 |
|---|---|---|
| 在线检测 | 路由器 API `getTerminalList()` → `device.active == 1` | `pc_device.lastHeartbeat` 在 30 秒内 |
| 超限执行 | `blacklistService.add(mac)` — 路由器断网 | `PcCommandService.create(LOCK_SCREEN + SHOW_MESSAGE)` — Agent 锁屏 |
| 解除限制 | `blacklistService.delete(mac)` — 路由器恢复 | Agent SHOW_MESSAGE 通知时段刷新（锁屏需密码无法远程解锁） |
| 延长时间 | `blacklistService.delete()` 解除黑名单 | 跳过 router API，发送 SHOW_MESSAGE 通知 PC |
| 时间累积 | TICK_SECONDS=10，依赖路由器 `onlinetime` | TICK_SECONDS=10，心跳在线即累积 |

### 后端改动

**SupervisionServiceImpl.java：**
- 注入 `PcDeviceMapper` + `PcCommandService`
- `list()` — MAC 匹配 PC 时填充 `pcHostname`、`pcOnline`、`pcUsedTodayMinutes`
- `extendTime()` — PC 规则跳过 `blacklistService.delete()`，发 SHOW_MESSAGE 通知
- `checkExtendExpiry()` — PC 规则跳过 `blacklistService.add()`，发 LOCK_SCREEN + SHOW_MESSAGE
- `revertExtensions()` — PC 规则跳过 `blacklistService.delete()`
- `remove()` — PC 规则跳过 `blacklistService` 操作

**MonitorScheduler.java：**
- 新增 `isPcRule(mac)` — 查询 `pc_device` 表判断
- 新增 `processPcRule(rule)` — 与 `processRule()` 逻辑镜像，执行动作改为 Agent 指令
- `monitor()` 循环分支：`if (isPcRule)` → `processPcRule()` else → `processRule()`

**SupervisionVO.java：**
- 新增字段：`pcHostname`、`pcOnline`、`extendActive`、`extendExpireMinutes`、`pcUsedTodayMinutes`

### 调度器核心逻辑（processPcRule）

```
processPcRule(rule):
  1. 查 pc_device_by_mac(mac) → 获取 PC 信息
  2. PC 在线：lastHeartbeat 在 30 秒内
  3. 延时模式：仅累积时长，跳过监管
  4. 不在时间段 → 发 SHOW_MESSAGE + LOCK_SCREEN
  5. 在时间段内 → 累积时长（sessionSeconds + TICK_SECONDS）
  6. 单次超时检查 → 超则发锁屏指令
  7. 每日限额检查 → 超则发锁屏指令
  8. 时间段切换 → 发 SHOW_MESSAGE 通知重置
```

### LOCK_SCREEN + SHOW_MESSAGE 5 分钟预警

在 `processPcRule` 的累积时长阶段，单次限制 > 5 分钟且剩余 ≤ 5 分钟时发送 SHOW_MESSAGE 预警弹窗。使用 `Set<Long> warnedPcRules` 在内存中跟踪已预警规则 ID，避免每 10 秒重复发送。以下情况清除预警标记：
- 进入新时间段（解除锁屏）
- 单次超时 / 每日用尽锁屏
- 离线进入新时间段解除限制

### 前端

- PcDetail.vue 新增"监管设置"卡片
- 显示：MAC、绑定状态、时间段、单次/每日限制、已用时长、剩余时间、状态（正常/延时中/已锁屏）
- 操作：延长 X 分钟（输入+按钮）、绑定已有规则、新建并绑定、解绑
- API 复用现有 `api/supervision.ts` 接口

---

| 步骤 | 操作 | 预期结果 |
|---|---|---|
| 1. 后端 API | curl POST `/api/pc/agent/register` | 返回 deviceId + agentToken |
| 2. Agent 启动 | 运行 `pc-agent.exe` | 日志输出注册/心跳/轮询 |
| 3. Web 下发指令 | 页面点击"获取进程" | Agent 收到指令、执行、回传结果 |
| 4. 远程锁屏 | 页面点击"远程锁屏" | PC 立即锁屏 |
| 5. 远程关机 | 页面点击"远程关机" → 确认 | PC 30 秒后关机 |
| 6. 弹窗消息 | 页面输入消息 → 发送 | PC 弹出 MessageBox 提示框 |
| 7. 消息预设 | 新增/删除预设模板 | API 返回正确，前端标签更新 |
| 8. PC 监管绑定 | 创建监管规则（MAC=PC 的 MAC）→ 等待 | PC 在时间段外自动锁屏，时间段内累积时长 |
| 9. 单次超时 | 单次限制 10 分钟，连续使用到 10 分钟 | PC 锁屏 + 弹窗提示 |
| 10. 5 分钟预警 | 单次限制 > 5 分钟，剩余 5 分钟时 | PC 弹窗预警"还剩 X 分钟" |
| 11. PC 延长 | Web 端延长 PC 时间 | PC 弹出延长通知，监管跳过锁屏 |
