<template>
  <div>
    <div class="topbar">
      <div>
        <h2>日志中心</h2>
        <p>按设备、时间段、告警类型筛选日志，记录连接次数与时长</p>
      </div>
      <div class="toolbar">
        <button class="btn" @click="handleExport">导出日志</button>
        <button class="btn btn-primary" @click="refresh">刷新日志</button>
      </div>
    </div>

    <a-spin :spinning="loading">
    <div class="section">
      <div class="section-header">
        <div>
          <h3>日志列表</h3>
          <div class="sub">监管对象与未白名单设备的行为记录</div>
        </div>
      </div>
      <div class="section-body">
        <div class="filters">
          <div class="field"><label>设备</label><input v-model="filters.device" @keyup.enter="refresh" placeholder="设备名 / MAC" /></div>
          <div class="field"><label>告警类型</label>
            <select v-model="filters.type" @change="refresh"><option value="">全部</option><option value="normal">普通连接</option><option value="abnormal">异常连接</option><option value="timeout">超时告警</option><option value="whitelist_violation">白名单外设备</option><option value="blacklist">黑名单触发</option></select>
          </div>
          <div class="field"><label>时间范围</label><input v-model="filters.timeRange" @keyup.enter="refresh" placeholder="2026-04-18 ~ 2026-04-20" /></div>
          <div class="field"><label>处理状态</label>
            <select v-model="filters.status" @change="refresh"><option value="">全部</option><option value="pending">未处理</option><option value="processed">已处理</option><option value="ignored">忽略</option></select>
          </div>
          <div class="field"><label>来源</label>
            <select v-model="filters.source" @change="refresh"><option value="">全部</option><option value="supervision">监管设备</option><option value="whitelist">白名单外设备</option><option value="blacklist">黑名单触发</option></select>
          </div>
          <div class="field" style="justify-content:flex-end;"><label>&nbsp;</label><button class="btn btn-primary" @click="refresh">查询</button></div>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>时间</th><th>设备名</th><th>MAC</th><th>类型</th><th>连接次数</th><th>单次时长</th><th>状态</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="log in logs" :key="log.id">
                <td>{{ formatTime(log.createTime) }}</td>
                <td>{{ log.deviceName }}</td>
                <td>{{ log.mac }}</td>
                <td><span :class="['tag', logTypeClass(log.logType)]">{{ logTypeLabel(log.logType) }}</span></td>
                <td>{{ log.connectCount || 1 }}</td>
                <td>{{ log.duration || 0 }} 分钟</td>
                <td><span :class="['tag', logStatusClass(log.status)]">{{ logStatusLabel(log.status) }}</span></td>
                <td>
                  <div class="actions">
                    <button class="btn btn-mini" @click="openDetail(log)">查看详情</button>
                    <button class="btn btn-mini" @click="handleMark(log)">标记处理</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Detail Modal -->
    <div class="modal-mask" v-if="showModal" @click.self="showModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3>日志详情</h3>
          <button class="close" @click="showModal = false">&times;</button>
        </div>
        <div class="modal-body">
          <div class="kv" style="margin-bottom:14px;">
            <div class="item"><div class="k">设备名</div><div class="v">{{ detailLog?.deviceName }}</div></div>
            <div class="item"><div class="k">MAC</div><div class="v">{{ detailLog?.mac }}</div></div>
            <div class="item"><div class="k">连接时间</div><div class="v">{{ formatTime(detailLog?.connectTime) }}</div></div>
            <div class="item"><div class="k">时长</div><div class="v">{{ detailLog?.duration || 0 }} 分钟</div></div>
          </div>
          <div class="field" style="margin-bottom:12px;">
            <label>处理状态</label>
            <select v-model="processForm.status"><option value="processed">已处理</option><option value="pending">未处理</option><option value="ignored">忽略</option></select>
          </div>
          <div class="field" style="margin-bottom:12px;"><label>备注</label><textarea v-model="processForm.remark" placeholder="处理备注"></textarea></div>
          <div style="display:flex;justify-content:flex-end;gap:10px;">
            <button class="btn" @click="showModal = false">取消</button>
            <button class="btn btn-primary" @click="confirmProcess" :disabled="submitting">{{ submitting ? '保存中...' : '保存处理结果' }}</button>
          </div>
        </div>
      </div>
    </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getLogs, updateLogStatus, exportLogs } from '../api/logs'
import dayjs from 'dayjs'

const logs = ref<any[]>([])
const filters = ref({ device: '', type: '', timeRange: '', status: '', source: '' })
const showModal = ref(false)
const detailLog = ref<any>(null)
const processForm = ref({ status: 'processed', remark: '' })

function logTypeClass(t: string) {
  const m: Record<string, string> = { normal: 'tag-info', abnormal: 'tag-danger', timeout: 'tag-warning', blacklist: 'tag-purple', whitelist_violation: 'tag-danger' }
  return m[t] || 'tag-gray'
}
function logTypeLabel(t: string) {
  const m: Record<string, string> = { normal: '普通连接', abnormal: '异常连接', timeout: '超时告警', blacklist: '黑名单触发', whitelist_violation: '白名单外异常' }
  return m[t] || t
}
function logStatusClass(s: string) {
  const m: Record<string, string> = { pending: 'tag-warning', processed: 'tag-success', ignored: 'tag-gray' }
  return m[s] || 'tag-gray'
}
function logStatusLabel(s: string) {
  const m: Record<string, string> = { pending: '未处理', processed: '已处理', ignored: '忽略' }
  return m[s] || s
}

function formatTime(t: string | null) {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-'
}

const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    const res: any = await getLogs({ ...filters.value, page: 1, size: 50 })
    logs.value = res.data || []
  } catch (e) { console.error(e) } finally { loading.value = false }
}

function openDetail(log: any) {
  detailLog.value = log
  processForm.value = { status: 'processed', remark: '' }
  showModal.value = true
}

async function handleMark(log: any) {
  detailLog.value = log
  processForm.value = { status: 'processed', remark: '' }
  showModal.value = true
}

const submitting = ref(false)

async function confirmProcess() {
  if (detailLog.value) {
    submitting.value = true
    try {
      await updateLogStatus(detailLog.value.id, processForm.value)
      message.success('处理成功')
      showModal.value = false
      refresh()
    } catch (e) { message.error('操作失败') } finally { submitting.value = false }
  }
}

async function handleExport() {
  try {
    const res: any = await exportLogs()
    const csv = '时间,设备名,MAC,类型,时长,状态\n' +
      (res.data || []).map((l: any) =>
        `${formatTime(l.createTime)},${l.deviceName},${l.mac},${logTypeLabel(l.logType)},${l.duration}分钟,${logStatusLabel(l.status)}`
      ).join('\n')
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = 'logs.csv'; a.click()
    URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch (e) { message.error('导出失败') }
}

onMounted(refresh)
</script>
