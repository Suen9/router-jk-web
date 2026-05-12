<template>
  <div>
    <div class="topbar">
      <div>
        <h2>终端管理</h2>
        <p>支持筛选、查看详情、修改接入方式、限速、拉黑与加入监管</p>
      </div>
      <div class="toolbar">
        <button class="btn" @click="refreshAll" :disabled="loading">刷新设备列表</button>
        <router-link to="/supervision" class="btn btn-primary" style="text-decoration:none;">+ 加入监管</router-link>
      </div>
    </div>

    <a-spin :spinning="loading">
    <div class="section">
      <div class="section-header">
        <div>
          <h3>在线终端列表</h3>
          <div class="sub">当前已连接到路由器的设备</div>
        </div>
      </div>
      <div class="section-body">
        <div class="filters">
          <div class="field"><label>设备名</label><input v-model="filters.keyword" placeholder="输入设备名" /></div>
          <div class="field"><label>MAC 地址</label><input v-model="filters.mac" placeholder="输入 MAC" /></div>
          <div class="field"><label>IP 地址</label><input v-model="filters.ip" placeholder="输入 IP" /></div>
          <div class="field"><label>接入方式</label>
            <select v-model="filters.accessType">
              <option value="">全部</option>
              <option value="2">允许接入并上网</option>
              <option value="1">允许接入但禁网</option>
              <option value="0">不允许接入</option>
            </select>
          </div>
          <div class="field"><label>状态</label>
            <select v-model="filters.status">
              <option value="">全部</option>
              <option value="1">在线</option>
              <option value="0">离线</option>
            </select>
          </div>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>设备名</th><th>MAC</th><th>IP</th><th>在线状态</th><th>在线时长</th>
                <th>接入方式</th><th>限速</th><th>频段</th><th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in filteredDevices" :key="d.idx">
                <td>{{ d.hostName || '-' }}</td>
                <td>{{ d.mac }}</td>
                <td>{{ d.userIp }}</td>
                <td>
                  <span :class="['tag', d.active === 1 ? 'tag-success' : 'tag-gray']">{{ d.active === 1 ? '在线' : '离线' }}</span>
                </td>
                <td>{{ formatOnlineTime(d.onlinetime) }}</td>
                <td>
                  <span :class="['tag', accessTagClass(d.internetaccess)]">{{ accessLabel(d.internetaccess) }}</span>
                </td>
                <td>
                  <span class="tag tag-gray" v-if="d.usbandwidth === 0 && d.dsbandwidth === 0">不限速</span>
                  <span class="tag tag-gray" v-else>上行 {{ d.usbandwidth }}M / 下行 {{ d.dsbandwidth }}M</span>
                </td>
                <td>{{ d.band || '-' }}</td>
                <td>
                  <div class="actions">
                    <button class="btn btn-mini" @click="openDetail(d.idx)">详情</button>
                    <button class="btn btn-mini btn-success" @click="setAccess(d.idx, 2)"
                      :disabled="d.internetaccess === 2" :style="d.internetaccess === 2 ? 'opacity:.4;cursor:not-allowed' : ''">允许上网</button>
                    <button class="btn btn-mini" @click="setAccess(d.idx, 1)"
                      :disabled="d.internetaccess === 1" :style="d.internetaccess === 1 ? 'opacity:.4;cursor:not-allowed' : ''">禁网</button>
                    <button class="btn btn-mini btn-danger" @click="handleBlacklist(d)"
                      :disabled="isInBlacklist(d.mac)" :style="isInBlacklist(d.mac) ? 'opacity:.4;cursor:not-allowed' : ''">
                      {{ isInBlacklist(d.mac) ? '已拉黑' : '拉黑' }}</button>
                    <button class="btn btn-mini" @click="openSpeedLimit(d)">限速</button>
                    <button class="btn btn-mini" :style="isInWhitelist(d.mac) ? 'opacity:.4;cursor:not-allowed' : {background:'#f0fdf4',borderColor:'#bbf7d0',color:'#166534'}"
                      @click="handleWhitelist(d)" :disabled="isInWhitelist(d.mac)">
                      {{ isInWhitelist(d.mac) ? '已加白' : '白名单' }}</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="footer-note">接口映射：获取终端列表、获取设备详情、设置限速、修改接入方式、加入黑名单。</div>
      </div>
    </div>

    <!-- Detail Modal -->
    <div class="modal-mask" v-if="showModal" @click.self="closeModals">
      <div class="modal" style="max-height:90vh;overflow:auto;">
        <div class="modal-header">
          <h3>{{ modalTitle }}</h3>
          <button class="close" @click="closeModals">&times;</button>
        </div>
        <div class="modal-body">
          <div v-if="detailDevice" class="kv" style="margin-bottom:14px;">
            <div class="item"><div class="k">MAC</div><div class="v">{{ detailDevice.mac }}</div></div>
            <div class="item"><div class="k">IP</div><div class="v">{{ detailDevice.ipaddr }}</div></div>
            <div class="item"><div class="k">品牌</div><div class="v">{{ detailDevice.brand || '-' }}</div></div>
            <div class="item"><div class="k">在线时长</div><div class="v">{{ detailDevice.onlinetime }}s</div></div>
          </div>
          <div class="form-grid" v-if="speedLimitTarget">
            <div class="field"><label>上传限速 (Mbps)</label><input v-model="speedForm.uploadspeed" /></div>
            <div class="field"><label>下载限速 (Mbps)</label><input v-model="speedForm.downloadspeed" /></div>
            <div class="field"><label>上传带宽 (0不限速)</label>
              <select v-model="speedForm.usbandwidth"><option :value="0">不限速</option><option :value="1">限速</option></select>
            </div>
            <div class="field"><label>下载带宽 (0不限速)</label>
              <select v-model="speedForm.dsbandwidth"><option :value="0">不限速</option><option :value="1">限速</option></select>
            </div>
          </div>
          <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:12px;">
            <button class="btn" @click="closeModals">取消</button>
            <button class="btn btn-primary" @click="confirmAction">确认提交</button>
          </div>
        </div>
      </div>
    </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getTerminalList, getTerminalDetail, setAccess as apiSetAccess, setSpeedLimit, addToBlacklist } from '../api/terminal'
import { getWhitelist, addWhitelist } from '../api/whitelist'
import { getBlacklist } from '../api/blacklist'

const devices = ref<any[]>([])
const filters = ref({ keyword: '', mac: '', ip: '', accessType: '', status: '' })

const whitelist = ref<any[]>([])
const blacklist = ref<any[]>([])

const whitelistMacs = computed(() => new Set(whitelist.value.map((w: any) => w.mac?.toUpperCase())))
const blacklistMacs = computed(() => new Set(blacklist.value.map((b: any) => b.mac?.toUpperCase())))

function isInWhitelist(mac: string) { return whitelistMacs.value.has(mac?.toUpperCase()) }
function isInBlacklist(mac: string) { return blacklistMacs.value.has(mac?.toUpperCase()) }

const filteredDevices = computed(() => {
  return devices.value.filter(d => {
    if (filters.value.keyword && !(d.hostName || '').includes(filters.value.keyword)) return false
    if (filters.value.mac && !(d.mac || '').includes(filters.value.mac)) return false
    if (filters.value.ip && !(d.userIp || '').includes(filters.value.ip)) return false
    if (filters.value.accessType && d.internetaccess !== parseInt(filters.value.accessType)) return false
    if (filters.value.status && d.active !== parseInt(filters.value.status)) return false
    return true
  })
})

const showModal = ref(false)
const modalTitle = ref('')
const detailDevice = ref<any>(null)
const speedLimitTarget = ref<any>(null)
const speedForm = ref({ uploadspeed: '0.00', downloadspeed: '0.00', usbandwidth: 0, dsbandwidth: 0 })
const loading = ref(false)
const submitting = ref(false)
const currentAction = ref('')

function accessLabel(v: number) {
  return ['不允许接入', '允许接入但禁网', '允许接入并上网'][v] || '未知'
}
function accessTagClass(v: number) {
  return ['tag-danger', 'tag-warning', 'tag-success'][v] || 'tag-gray'
}
function formatOnlineTime(s: number) {
  if (!s) return '-'
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60)
  return `${h}h ${m}m`
}

async function refresh() {
  try {
    const res: any = await getTerminalList({ page: 1, size: 50 })
    devices.value = res.data || []
  } catch (e) { console.error(e) }
}

async function refreshWhitelist() {
  try {
    const res: any = await getWhitelist()
    whitelist.value = res.data || []
  } catch (e) { console.error(e) }
}

async function refreshBlacklist() {
  try {
    const res: any = await getBlacklist()
    blacklist.value = res.data || []
  } catch (e) { console.error(e) }
}

async function refreshAll() {
  loading.value = true
  try { await Promise.all([refresh(), refreshWhitelist(), refreshBlacklist()]) }
  finally { loading.value = false }
}

async function handleWhitelist(d: any) {
  if (isInWhitelist(d.mac)) return
  try {
    const name = d.hostName || d.mac
    await addWhitelist({ hostname: name, mac: d.mac, remark: '从终端列表添加' })
    message.success(`已将 ${name} 加入白名单`)
    refreshWhitelist()
  } catch (e) { message.error('添加白名单失败') }
}

async function openDetail(idx: number) {
  modalTitle.value = '设备详情'
  currentAction.value = 'detail'
  try {
    const res: any = await getTerminalDetail(idx)
    detailDevice.value = res.data
    speedLimitTarget.value = null
    showModal.value = true
  } catch (e) { message.error('获取详情失败') }
}

async function setAccess(idx: number, val: number) {
  submitting.value = true
  try {
    await apiSetAccess({ idx, internetaccess: val })
    message.success('操作成功')
    refresh()
  } catch (e) { message.error('操作失败') } finally { submitting.value = false }
}

function openSpeedLimit(d: any) {
  modalTitle.value = '限速设置 - ' + (d.hostName || d.mac)
  currentAction.value = 'speedLimit'
  speedLimitTarget.value = d
  detailDevice.value = null
  speedForm.value = { uploadspeed: '0.00', downloadspeed: '0.00', usbandwidth: 0, dsbandwidth: 0 }
  showModal.value = true
}

async function handleBlacklist(d: any) {
  if (isInBlacklist(d.mac)) return
  submitting.value = true
  try {
    const name = d.hostName || d.mac
    await addToBlacklist({ mac: d.mac, name })
    message.success('已加入黑名单')
    refreshBlacklist()
  } catch (e) { message.error('拉黑失败') } finally { submitting.value = false }
}

async function confirmAction() {
  submitting.value = true
  try {
    if (currentAction.value === 'speedLimit' && speedLimitTarget.value) {
      await setSpeedLimit({
        idx: speedLimitTarget.value.idx,
        usbandwidth: speedForm.value.usbandwidth,
        dsbandwidth: speedForm.value.dsbandwidth,
        uploadspeed: speedForm.value.uploadspeed,
        downloadspeed: speedForm.value.downloadspeed
      })
      message.success('限速设置成功')
    }
    closeModals()
    refresh()
  } catch (e) { message.error('操作失败') } finally { submitting.value = false }
}

function closeModals() {
  showModal.value = false
  detailDevice.value = null
  speedLimitTarget.value = null
}

onMounted(() => { refreshAll() })
</script>
