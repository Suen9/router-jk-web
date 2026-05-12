<template>
  <div>
    <div class="topbar">
      <div>
        <h2>PC 远程管控</h2>
        <p>管理受控 PC 设备，支持远程进程查看、锁屏和关机</p>
      </div>
      <div class="toolbar">
        <button class="btn" @click="refresh" :disabled="loading">刷新列表</button>
        <button class="btn btn-primary" @click="showAddPanel = true">+ 添加 PC</button>
      </div>
    </div>

    <a-spin :spinning="loading">
    <div class="section">
      <div class="section-header">
        <div>
          <h3>设备列表</h3>
          <div class="sub">所有已注册的 PC 设备</div>
        </div>
      </div>
      <div class="section-body">
        <div class="filters">
          <div class="field"><label>关键字</label><input v-model="keyword" placeholder="主机名 / IP / MAC" @input="refresh" /></div>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>主机名</th><th>IP</th><th>MAC</th><th>系统版本</th><th>状态</th><th>最后心跳</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in list" :key="item.id">
                <td>{{ item.hostname || '-' }}</td>
                <td>{{ item.ip || '-' }}</td>
                <td>{{ item.mac || '-' }}</td>
                <td>{{ item.osVersion || '-' }}</td>
                <td>
                  <span :class="['tag', item.status === 'online' ? 'tag-success' : '']">
                    {{ item.status === 'online' ? '在线' : '离线' }}
                  </span>
                </td>
                <td>{{ item.lastHeartbeat ? formatTime(item.lastHeartbeat) : '-' }}</td>
                <td>
                  <div class="actions">
                    <button class="btn btn-mini" @click="goDetail(item.id)">详情</button>
                    <button class="btn btn-mini" @click="handleCommand(item.id, 'LOCK_SCREEN')" :disabled="item.status !== 'online'">锁屏</button>
                    <button class="btn btn-mini btn-danger" @click="handleShutdown(item.id)" :disabled="item.status !== 'online'">关机</button>
                  </div>
                </td>
              </tr>
              <tr v-if="list.length === 0">
                <td colspan="7" style="text-align:center;color:#999;padding:40px 0;">暂无 PC 设备</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Add Panel -->
    <div class="split" v-if="showAddPanel">
      <div class="panel">
        <h4>添加 PC 设备</h4>
        <div class="field" style="margin-bottom:10px;"><label>MAC 地址</label><input v-model="addForm.mac" placeholder="00:11:22:33:44:55" /></div>
        <div class="field" style="margin-bottom:10px;"><label>主机名</label><input v-model="addForm.hostname" placeholder="可选" /></div>
        <div class="field" style="margin-bottom:10px;"><label>备注</label><textarea v-model="addForm.remark" placeholder="可选"></textarea></div>
        <div style="display:flex;justify-content:flex-end;gap:10px;">
          <button class="btn" @click="showAddPanel = false; clearAddForm()">取消</button>
          <button class="btn btn-primary" @click="handleAdd" :disabled="submitting">{{ submitting ? '提交中...' : '提交' }}</button>
        </div>
      </div>
      <div class="panel">
        <h4>说明</h4>
        <div class="notice">
          PC 设备需先安装 Agent 程序才能被管控。<br/>
          手动添加仅预注册设备信息，Agent 首次运行时会自动绑定。<br/>
          状态通过心跳判断，超过 30 秒未上报即显示离线。
        </div>
      </div>
    </div>

    <!-- Shutdown confirm modal -->
    <div class="modal-mask" v-if="showConfirm" @click.self="showConfirm = false">
      <div class="modal" style="max-width:400px;">
        <div class="modal-header">
          <h3>确认操作</h3>
          <button class="close" @click="showConfirm = false">&times;</button>
        </div>
        <div class="modal-body">
          <p style="margin-bottom:16px;">确认远程{{ confirmAction === 'SHUTDOWN' ? '关机' : '重启' }}该 PC？</p>
          <p style="color:#999;font-size:13px;">指令将通过 Agent 下发，预计延迟约 30 秒后执行。</p>
          <div style="display:flex;justify-content:flex-end;gap:10px;">
            <button class="btn" @click="showConfirm = false">取消</button>
            <button class="btn btn-danger" @click="confirmSend">确认{{ confirmAction === 'SHUTDOWN' ? '关机' : '重启' }}</button>
          </div>
        </div>
      </div>
    </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getPcDeviceList, addPcDevice, sendCommand } from '../api/pc'

const router = useRouter()
const list = ref<any[]>([])
const keyword = ref('')
const loading = ref(false)
const submitting = ref(false)
const showAddPanel = ref(false)
const showConfirm = ref(false)
const confirmDeviceId = ref(0)
const confirmAction = ref('SHUTDOWN')

const addForm = ref({ mac: '', hostname: '', remark: '' })

function formatTime(t: string) {
  return t ? t.replace('T', ' ').substring(0, 19) : '-'
}

async function refresh() {
  loading.value = true
  try {
    const res: any = await getPcDeviceList({ keyword: keyword.value || undefined })
    list.value = res.data || []
  } catch (e) { console.error(e) } finally { loading.value = false }
}

async function handleAdd() {
  if (!addForm.value.mac) { message.warning('请输入MAC地址'); return }
  submitting.value = true
  try {
    await addPcDevice(addForm.value)
    message.success('添加成功')
    showAddPanel.value = false
    clearAddForm()
    refresh()
  } catch (e) { message.error('添加失败') } finally { submitting.value = false }
}

function clearAddForm() { addForm.value = { mac: '', hostname: '', remark: '' } }

function goDetail(id: number) {
  router.push(`/pc/detail/${id}`)
}

async function handleCommand(deviceId: number, type: string) {
  try {
    await sendCommand(deviceId, type)
    message.success(`指令已下发`)
  } catch (e) { message.error('指令下发失败') }
}

function handleShutdown(deviceId: number) {
  confirmDeviceId.value = deviceId
  confirmAction.value = 'SHUTDOWN'
  showConfirm.value = true
}

async function confirmSend() {
  try {
    await sendCommand(confirmDeviceId.value, confirmAction.value)
    message.success('指令已下发')
    showConfirm.value = false
  } catch (e) { message.error('指令下发失败') }
}

onMounted(refresh)
</script>
