<template>
  <div>
    <div class="topbar">
      <div>
        <h2>PC 详情</h2>
        <p v-if="device">{{ device.hostname || device.mac }} — 远程管控</p>
      </div>
      <div class="toolbar">
        <button class="btn" @click="router.back()">返回列表</button>
      </div>
    </div>

    <a-spin :spinning="loading">
    <!-- Basic Info -->
    <div class="section">
      <div class="section-header">
        <div>
          <h3>基本信息</h3>
        </div>
      </div>
      <div class="section-body">
        <div class="notice">
          <table style="width:100%;">
            <tr><td style="width:100px;color:#999;">主机名</td><td>{{ device?.hostname || '-' }}</td></tr>
            <tr><td style="color:#999;">IP</td><td>{{ device?.ip || '-' }}</td></tr>
            <tr><td style="color:#999;">MAC</td><td>{{ device?.mac || '-' }}</td></tr>
            <tr><td style="color:#999;">系统版本</td><td>{{ device?.osVersion || '-' }}</td></tr>
            <tr><td style="color:#999;">客户端版本</td><td>{{ device?.agentVersion || '-' }}</td></tr>
            <tr><td style="color:#999;">状态</td>
              <td>
                <span :class="['tag', deviceStatus === 'online' ? 'tag-success' : '']">
                  {{ deviceStatus === 'online' ? '在线' : '离线' }}
                </span>
              </td>
            </tr>
            <tr><td style="color:#999;">最后心跳</td><td>{{ device?.lastHeartbeat ? formatTime(device.lastHeartbeat) : '-' }}</td></tr>
            <tr><td style="color:#999;">备注</td><td>{{ device?.remark || '-' }}</td></tr>
          </table>
        </div>
      </div>
    </div>

    <!-- Actions -->
    <div class="section">
      <div class="section-header">
        <div>
          <h3>远程操作</h3>
          <div class="sub">指令将通过 PC Agent 下发执行</div>
        </div>
      </div>
      <div class="section-body">
        <div style="display:flex;gap:12px;flex-wrap:wrap;">
          <button class="btn" @click="handleCommand('PROCESSES')" :disabled="deviceStatus !== 'online' || processLoading">
            {{ processLoading ? '获取中...' : '获取进程列表' }}
          </button>
          <button class="btn" @click="handleCommand('LOCK_SCREEN')" :disabled="deviceStatus !== 'online'">远程锁屏</button>
          <button class="btn btn-danger" @click="confirmAction = 'SHUTDOWN'; showConfirm = true" :disabled="deviceStatus !== 'online'">远程关机</button>
          <button class="btn btn-danger" @click="confirmAction = 'RESTART'; showConfirm = true" :disabled="deviceStatus !== 'online'">远程重启</button>
          <button class="btn" @click="handleCommand('LOGOFF')" :disabled="deviceStatus !== 'online'">注销用户</button>
          <button class="btn" @click="showMessageModal = true" :disabled="deviceStatus !== 'online'">发送弹窗消息</button>
        </div>
      </div>
    </div>

    <!-- Process List -->
    <div class="section" v-if="processes.length > 0">
      <div class="section-header">
        <div>
          <h3>进程列表</h3>
          <div class="sub">内存占用最高的20个进程</div>
        </div>
        <button class="btn btn-mini" @click="processes = []; commands = []; refreshCommands()">关闭</button>
      </div>
      <div class="section-body">
        <div class="filters">
          <div class="field"><label>筛选</label><input v-model="processFilter" placeholder="输入进程名" /></div>
        </div>
        <div class="table-wrap" style="max-height:400px;overflow-y:auto;">
          <table>
            <thead>
              <tr><th>进程名</th><th>PID</th><th>会话名</th><th>内存使用</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="(p, i) in filteredProcesses" :key="i">
                <td>{{ p.name }}</td>
                <td>{{ p.pid }}</td>
                <td>{{ p.sessionName || '-' }}</td>
                <td>{{ p.memUsage || '-' }}</td>
                <td>
                  <button class="btn btn-mini btn-danger" @click="confirmKill(p)" :disabled="deviceStatus !== 'online'">结束</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Command History -->
    <div class="section">
      <div class="section-header">
        <div>
          <h3>指令历史</h3>
          <div class="sub">最近操作记录</div>
        </div>
      </div>
      <div class="section-body">
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>指令类型</th><th>状态</th><th>创建时间</th><th>完成时间</th><th>结果</th></tr>
            </thead>
            <tbody>
              <tr v-for="(c, i) in commands" :key="i">
                <td>{{ commandLabel(c.commandType) }}</td>
                <td>
                  <span :class="['tag', statusClass(c.status)]">{{ statusLabel(c.status) }}</span>
                </td>
                <td>{{ c.createTime ? formatTime(c.createTime) : '-' }}</td>
                <td>{{ c.completeTime ? formatTime(c.completeTime) : '-' }}</td>
                <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">
                  {{ c.status === 'success' ? '执行成功' : c.errorMessage || '-' }}
                </td>
              </tr>
              <tr v-if="commands.length === 0">
                <td colspan="5" style="text-align:center;color:#999;padding:40px 0;">暂无指令记录</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Confirm modal (shutdown/restart) -->
    <div class="modal-mask" v-if="showConfirm" @click.self="showConfirm = false">
      <div class="modal" style="max-width:400px;">
        <div class="modal-header">
          <h3>确认操作</h3>
          <button class="close" @click="showConfirm = false">&times;</button>
        </div>
        <div class="modal-body">
          <p style="margin-bottom:16px;">确认远程{{ actionLabel(confirmAction) }}该 PC？</p>
          <p style="color:#999;font-size:13px;">
            {{ confirmAction === 'SHUTDOWN' ? '系统将在 30 秒后关闭。' : '系统将在 30 秒后重启。' }}
          </p>
          <div style="display:flex;justify-content:flex-end;gap:10px;">
            <button class="btn" @click="showConfirm = false">取消</button>
            <button class="btn btn-danger" @click="confirmSend">确认{{ actionLabel(confirmAction) }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Kill process confirm modal -->
    <div class="modal-mask" v-if="showKillConfirm" @click.self="showKillConfirm = false">
      <div class="modal" style="max-width:400px;">
        <div class="modal-header">
          <h3>确认结束进程</h3>
          <button class="close" @click="showKillConfirm = false">&times;</button>
        </div>
        <div class="modal-body">
          <p style="margin-bottom:16px;">
            确认结束进程 <strong>{{ killTarget?.name }}</strong>（PID: {{ killTarget?.pid }}）？
          </p>
          <p style="color:red;font-size:13px;">结束进程可能导致数据丢失，请谨慎操作。</p>
          <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:16px;">
            <button class="btn" @click="showKillConfirm = false">取消</button>
            <button class="btn btn-danger" @click="doKillProcess" :disabled="killingProcess">{{ killingProcess ? '执行中...' : '确认结束' }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Send message modal -->
    <div class="modal-mask" v-if="showMessageModal" @click.self="showMessageModal = false">
      <div class="modal" style="max-width:500px;">
        <div class="modal-header">
          <h3>发送弹窗消息</h3>
          <button class="close" @click="showMessageModal = false">&times;</button>
        </div>
        <div class="modal-body">
          <div class="field">
            <label>消息内容</label>
            <textarea v-model="messageText" placeholder="请输入要发送的消息内容" style="min-height:80px;"></textarea>
          </div>

          <div style="margin-top:16px;">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
              <label style="font-weight:600;font-size:13px;color:#333;">常用提示词</label>
            </div>
            <div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:8px;">
              <span v-for="tpl in templates" :key="tpl.id"
                style="display:inline-flex;align-items:center;gap:6px;padding:6px 12px;background:#f0f5ff;border:1px solid #d6e4ff;border-radius:16px;font-size:13px;cursor:pointer;color:#2b5fd9;max-width:100%;"
                @click="messageText = tpl.content">
                <span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ tpl.content }}</span>
                <span style="cursor:pointer;font-size:16px;line-height:1;color:#999;flex-shrink:0;" @click.stop="doRemoveTemplate(tpl.id)">&times;</span>
              </span>
              <span v-if="templates.length === 0" style="color:#999;font-size:13px;">暂无预设提示词</span>
            </div>
            <div style="display:flex;gap:8px;">
              <input v-model="newTemplateContent" placeholder="新增常用提示词" style="flex:1;" />
              <button class="btn btn-mini btn-primary" @click="doAddTemplate" :disabled="!newTemplateContent.trim()">添加</button>
            </div>
          </div>

          <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:16px;padding-top:16px;border-top:1px solid var(--line);">
            <button class="btn" @click="showMessageModal = false">取消</button>
            <button class="btn btn-primary" @click="doSendMessage" :disabled="!messageText.trim() || sendingMessage">
              {{ sendingMessage ? '发送中...' : '发送' }}
            </button>
          </div>
        </div>
      </div>
    </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, modal } from 'ant-design-vue'
import { getPcDeviceDetail, sendCommand, getPcCommandList, getMessageTemplates, addMessageTemplate, removeMessageTemplate } from '../api/pc'

const route = useRoute()
const router = useRouter()
const deviceId = Number(route.params.id)

const device = ref<any>(null)
const loading = ref(false)
const processLoading = ref(false)
const showConfirm = ref(false)
const confirmAction = ref('SHUTDOWN')

const processes = ref<any[]>([])
const processFilter = ref('')
const commands = ref<any[]>([])
const showKillConfirm = ref(false)
const killTarget = ref<any>(null)
const killingProcess = ref(false)

// 弹窗消息
const showMessageModal = ref(false)
const messageText = ref('')
const templates = ref<any[]>([])
const newTemplateContent = ref('')
const sendingMessage = ref(false)

const filteredProcesses = computed(() => {
  if (!processFilter.value) return processes.value
  return processes.value.filter((p: any) =>
    (p.name || '').toLowerCase().includes(processFilter.value.toLowerCase())
  )
})

const deviceStatus = computed(() => {
  if (!device.value) return 'offline'
  if (!device.value.lastHeartbeat) return 'offline'
  const now = Date.now()
  const hb = new Date(device.value.lastHeartbeat).getTime()
  return (now - hb) < 30000 ? 'online' : 'offline'
})

function formatTime(t: string) {
  return t ? t.replace('T', ' ').substring(0, 19) : '-'
}

function commandLabel(type: string) {
  const map: Record<string, string> = {
    PROCESSES: '获取进程',
    LOCK_SCREEN: '锁屏',
    SHUTDOWN: '关机',
    RESTART: '重启',
    LOGOFF: '注销',
    KILL_PROCESS: '结束进程'
  }
  return map[type] || type
}

function statusLabel(s: string) {
  const map: Record<string, string> = {
    pending: '待下发',
    sent: '已下发',
    executing: '执行中',
    success: '成功',
    failed: '失败'
  }
  return map[s] || s
}

function statusClass(s: string) {
  if (s === 'success') return 'tag-success'
  if (s === 'failed') return 'tag-danger'
  if (s === 'sent' || s === 'executing') return 'tag-warning'
  return ''
}

function actionLabel(a: string) {
  return a === 'SHUTDOWN' ? '关机' : '重启'
}

async function loadDevice() {
  loading.value = true
  try {
    const res: any = await getPcDeviceDetail(deviceId)
    device.value = res.data
  } catch (e) { console.error(e) } finally { loading.value = false }
}

async function refreshCommands() {
  try {
    const res: any = await getPcCommandList({ pcDeviceId: deviceId, page: 1, size: 20 })
    commands.value = res.data?.list || []
  } catch (e) { console.error(e) }
}

async function handleCommand(type: string) {
  if (type === 'PROCESSES') {
    processLoading.value = true
  }
  try {
    await sendCommand(deviceId, type)
    if (type === 'PROCESSES') {
      message.success('进程列表获取指令已下发，请稍后点击"关闭"重新打开查看结果')
    } else {
      message.success('指令已下发')
    }
    // Poll for result after a short delay
    setTimeout(pollProcessResult, 3000)
    setTimeout(refreshCommands, 2000)
  } catch (e) {
    message.error('指令下发失败')
    processLoading.value = false
  }
}

async function pollProcessResult() {
  if (processLoading.value === false) return // already resolved
  try {
    const res: any = await getPcCommandList({ pcDeviceId: deviceId, commandType: 'PROCESSES', page: 1, size: 1 })
    const cmds = res.data?.list || []
    if (cmds.length > 0 && cmds[0].status === 'success') {
      try {
        processes.value = JSON.parse(cmds[0].result || '[]')
      } catch { processes.value = [] }
      processLoading.value = false
      message.success(`获取到 ${processes.value.length} 个进程`)
    } else if (cmds.length > 0 && cmds[0].status === 'failed') {
      processLoading.value = false
      message.error('获取进程列表失败')
    } else {
      setTimeout(pollProcessResult, 3000)
    }
  } catch {
    setTimeout(pollProcessResult, 3000)
  }
}

async function confirmSend() {
  try {
    await sendCommand(deviceId, confirmAction.value)
    message.success('指令已下发')
    showConfirm.value = false
    setTimeout(refreshCommands, 2000)
  } catch (e) { message.error('指令下发失败') }
}

function confirmKill(p: any) {
  killTarget.value = p
  showKillConfirm.value = true
}

async function doKillProcess() {
  if (!killTarget.value) return
  killingProcess.value = true
  try {
    await sendCommand(deviceId, 'KILL_PROCESS', JSON.stringify({ pid: killTarget.value.pid }))
    message.success('结束进程指令已下发')
    showKillConfirm.value = false
    killTarget.value = null
    setTimeout(refreshCommands, 2000)
  } catch (e) { message.error('指令下发失败') } finally { killingProcess.value = false }
}

// 弹窗消息 — 加载预设模板
async function loadTemplates() {
  try {
    const res: any = await getMessageTemplates()
    templates.value = res.data || []
  } catch { /* ignore */ }
}

// 弹窗消息 — 新增预设
async function doAddTemplate() {
  const content = newTemplateContent.value.trim()
  if (!content) return
  try {
    await addMessageTemplate({ content })
    newTemplateContent.value = ''
    await loadTemplates()
    message.success('预设已添加')
  } catch { message.error('添加失败') }
}

// 弹窗消息 — 删除预设
async function doRemoveTemplate(id: number) {
  try {
    await removeMessageTemplate(id)
    await loadTemplates()
  } catch { message.error('删除失败') }
}

// 弹窗消息 — 发送
async function doSendMessage() {
  const msg = messageText.value.trim()
  if (!msg) return
  sendingMessage.value = true
  try {
    await sendCommand(deviceId, 'SHOW_MESSAGE', JSON.stringify({ message: msg }))
    message.success('弹窗消息指令已下发')
    showMessageModal.value = false
    messageText.value = ''
    setTimeout(refreshCommands, 2000)
  } catch { message.error('指令下发失败') } finally { sendingMessage.value = false }
}

watch(showMessageModal, (val) => {
  if (val) {
    messageText.value = ''
    newTemplateContent.value = ''
    loadTemplates()
  }
})

onMounted(() => {
  loadDevice()
  refreshCommands()
})
</script>
