<template>
  <div>
    <div class="topbar">
      <div>
        <h2>监管设备</h2>
        <p>支持多时间段可上网规则、单次使用时长、总时长限制和延长时长</p>
      </div>
      <div class="toolbar">
        <button class="btn btn-primary" @click="openAddModal">+ 添加监管设备</button>
      </div>
    </div>

    <a-spin :spinning="loading">
    <div class="section">
      <div class="section-header">
        <div>
          <h3>监管设备列表</h3>
          <div class="sub">对加入监管名单的设备进行规则控制</div>
        </div>
      </div>
      <div class="section-body">
        <div class="notice" style="margin-bottom:14px;">
          监管策略建议优先级：<b>黑名单 &gt; 禁网 &gt; 限速 &gt; 白名单</b>。
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>设备名</th><th>MAC</th><th>可上网时间段</th><th>单次时长</th>
                <th>每日总时长</th><th>已使用</th><th>剩余</th><th>状态</th><th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in list" :key="item.id">
                <td>{{ item.hostname }}</td>
                <td>{{ item.mac }}</td>
                <td>{{ item.timeSlots || '-' }}</td>
                <td>{{ item.singleDuration }} 分钟</td>
                <td>{{ item.dailyLimit }} 分钟</td>
                <td>{{ item.usedToday || 0 }} 分钟</td>
                <td>{{ item.remaining }} 分钟</td>
                <td>
                  <span :class="['tag', statusClass(item.remaining, item.status, item.blacklistedBySupervision)]">{{ statusLabel(item.remaining, item.status, item.blacklistedBySupervision) }}</span>
                </td>
                <td>
                  <div class="actions">
                    <button class="btn btn-mini" @click="openExtend(item)">延长时长</button>
                    <button class="btn btn-mini" @click="openEdit(item)">编辑规则</button>
                    <button class="btn btn-mini btn-danger" @click="handleRemove(item.id)">移除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="split">
      <div class="panel">
        <h4>{{ editMode ? '编辑监管规则' : '新增监管规则' }}</h4>
        <div class="form-grid">
          <div class="field"><label>设备名</label><input v-model="form.hostname" placeholder="请输入设备名" /></div>
          <div class="field"><label>MAC 地址</label><input v-model="form.mac" placeholder="00:11:22:33:44:55" /></div>
          <div class="field"><label>单次使用时长 </label>
            <select v-model="form.singleDuration">
              <option :value="0">10秒 (测试)</option><option :value="0">30秒 (测试)</option><option :value="1">1分钟 (测试)</option>
              <option :value="2">2分钟 (测试)</option><option :value="3">3分钟 (测试)</option><option :value="5">5分钟 (测试)</option>
              <option :value="10">10分钟 (测试)</option>
              <option :value="15">15 分钟</option><option :value="30">30 分钟</option><option :value="45">45 分钟</option>
              <option :value="60">60 分钟</option><option :value="90">90 分钟</option><option :value="120">120 分钟</option>
            </select>
          </div>
          <div class="field"><label>每日总时长 </label>
            <select v-model="form.dailyLimit">
              <option :value="0">10秒 (测试)</option><option :value="0">30秒 (测试)</option><option :value="1">1分钟 (测试)</option>
              <option :value="2">2分钟 (测试)</option><option :value="3">3分钟 (测试)</option><option :value="5">5分钟 (测试)</option>
              <option :value="10">10分钟 (测试)</option>
              <option :value="30">30 分钟</option><option :value="60">60 分钟</option><option :value="90">90 分钟</option>
              <option :value="120">120 分钟</option><option :value="180">180 分钟</option><option :value="240">240 分钟</option><option :value="300">300 分钟</option>
            </select>
          </div>
          <div class="field"><label>备注</label><input v-model="form.remark" placeholder="规则说明" /></div>
        </div>

        <!-- 可视化时间段编辑 -->
        <div style="margin-top:16px;">
          <label style="font-size:12px;color:var(--sub);display:block;margin-bottom:8px;">可上网时间段</label>
          <div v-for="(slot, i) in timeSlots" :key="i" style="display:flex;align-items:center;gap:10px;margin-bottom:8px;">
            <input type="time" v-model="slot.start" style="width:130px;" />
            <span style="color:var(--sub);">至</span>
            <input type="time" v-model="slot.end" style="width:130px;" />
            <button class="btn btn-mini btn-danger" @click="removeSlot(i)" style="padding:5px 8px;font-size:12px;">&times;</button>
          </div>
          <button class="btn btn-mini" @click="addSlot" style="margin-top:4px;">+ 添加时间段</button>
        </div>

        <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:16px;">
          <button class="btn" @click="resetForm">取消</button>
          <button class="btn btn-primary" @click="handleSave" :disabled="submitting">{{ submitting ? '保存中...' : (editMode ? '更新规则' : '保存规则') }}</button>
        </div>
      </div>
      <div class="panel">
        <h4>监管规则说明</h4>
        <div class="notice">
          支持多时间段规则（可视化选择起止时间）、单次使用时长、每日总时长、延长时长和移除监管。<br />
          建议监管设备与日志中心联动。
        </div>
      </div>
    </div>

    <!-- Extend Modal -->
    <div class="modal-mask" v-if="showExtendModal" @click.self="showExtendModal = false">
      <div class="modal">
        <div class="modal-header"><h3>延长时长 — {{ extendTarget?.hostname }}</h3><button class="close" @click="showExtendModal = false">&times;</button></div>
        <div class="modal-body">
          <p style="font-size:13px;color:var(--sub);margin-bottom:12px;">选择延长分钟数或自定义输入</p>
          <div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:14px;">
            <button v-for="min in [15,30,45,60,90,120]" :key="min"
              @click="extendMinutes = min"
              :style="extendMinutes === min
                ? 'border-color:#3b82f6;background:#eff6ff;color:#2563eb;'
                : 'border:1px solid var(--line);background:#fff;color:var(--text);'"
              style="padding:8px 16px;border-radius:12px;cursor:pointer;font-size:13px;">
              +{{ min }} 分钟
            </button>
          </div>
          <div class="field"><label>自定义分钟数</label><input v-model.number="extendMinutes" type="number" placeholder="输入分钟数" /></div>
          <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:14px;">
            <button class="btn" @click="showExtendModal = false">取消</button>
            <button class="btn btn-primary" @click="confirmExtend" :disabled="submitting">{{ submitting ? '延长中...' : '确认延长' }}</button>
          </div>
        </div>
      </div>
    </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getSupervisionList, addSupervision, updateSupervisionRule, extendSupervision, removeSupervision } from '../api/supervision'

const list = ref<any[]>([])
const editMode = ref(false)
const editId = ref<number | null>(null)
const form = ref({ hostname: '', mac: '', singleDuration: 60, dailyLimit: 120, remark: '' })
const timeSlots = reactive<{ start: string; end: string }[]>([])

const showExtendModal = ref(false)
const extendTarget = ref<any>(null)
const extendMinutes = ref(30)

function statusClass(remaining: number, status: string, blacklisted: number) {
  if (status !== 'active') return 'tag-gray'
  if (blacklisted === 1) return 'tag-danger'
  return remaining <= 0 ? 'tag-danger' : remaining < 20 ? 'tag-info' : 'tag-success'
}
function statusLabel(remaining: number, status: string, blacklisted: number) {
  if (status !== 'active') return '待生效'
  if (blacklisted === 1) return '已拉黑'
  return remaining <= 0 ? '已用完' : remaining < 20 ? '剩余不足' : '监管中'
}

function addSlot() { timeSlots.push({ start: '08:00', end: '12:00' }) }
function removeSlot(i: number) { timeSlots.splice(i, 1) }

function timeSlotsToJson(): string {
  const arr = timeSlots.filter(s => s.start && s.end).map(s => s.start + '-' + s.end)
  return arr.length > 0 ? JSON.stringify(arr) : ''
}

function parseTimeSlots(json: string) {
  timeSlots.length = 0
  if (!json) return
  try {
    const arr: string[] = JSON.parse(json)
    arr.forEach(s => {
      const parts = s.split('-')
      if (parts.length === 2) timeSlots.push({ start: parts[0], end: parts[1] })
    })
  } catch { timeSlots.length = 0 }
}

const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    const res: any = await getSupervisionList()
    list.value = res.data || []
  } catch (e) { console.error(e) } finally { loading.value = false }
}

const submitting = ref(false)

async function handleSave() {
  submitting.value = true
  try {
    const payload = { ...form.value, timeSlots: timeSlotsToJson() }
    if (editMode.value && editId.value) {
      await updateSupervisionRule(editId.value, payload)
      message.success('规则更新成功')
    } else {
      await addSupervision(payload)
      message.success('监管设备添加成功')
    }
    resetForm()
    refresh()
  } catch (e) { message.error('操作失败') } finally { submitting.value = false }
}

function resetForm() {
  form.value = { hostname: '', mac: '', singleDuration: 60, dailyLimit: 120, remark: '' }
  timeSlots.length = 0
  editMode.value = false
  editId.value = null
}

function openAddModal() {
  resetForm()
  const el = document.querySelector('.panel')
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
function openEdit(item: any) {
  form.value = {
    hostname: item.hostname, mac: item.mac,
    singleDuration: item.singleDuration || 60,
    dailyLimit: item.dailyLimit || 120,
    remark: item.remark || ''
  }
  parseTimeSlots(item.timeSlots || '')
  editMode.value = true
  editId.value = item.id
}

function openExtend(item: any) {
  extendTarget.value = item
  extendMinutes.value = 30
  showExtendModal.value = true
}

async function confirmExtend() {
  if (extendTarget.value && extendMinutes.value > 0) {
    submitting.value = true
    try {
      await extendSupervision(extendTarget.value.id, extendMinutes.value)
      message.success(`已延长 ${extendMinutes.value} 分钟`)
      showExtendModal.value = false
      refresh()
    } catch (e) { message.error('操作失败') } finally { submitting.value = false }
  }
}

async function handleRemove(id: number) {
  await removeSupervision(id)
  message.success('已移除监管')
  refresh()
}

onMounted(refresh)
</script>
