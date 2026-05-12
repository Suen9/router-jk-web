<template>
  <div>
    <div class="topbar">
      <div>
        <h2>首页概览</h2>
        <p>查看在线终端、监管状态、黑名单与异常日志概览</p>
      </div>
      <div class="toolbar">
        <button class="btn" @click="refresh" :disabled="loading">刷新状态</button>
        <router-link to="/terminal" class="btn btn-primary" style="text-decoration:none;">进入终端管理</router-link>
      </div>
    </div>

    <a-spin :spinning="loading">
    <div class="grid cards">
      <div class="card stat">
        <div class="icon blue">终</div>
        <div class="label">在线终端</div>
        <div class="value">{{ stats.onlineDevices }}</div>
        <div class="desc">当前在线设备数</div>
      </div>
      <div class="card stat">
        <div class="icon red">黑</div>
        <div class="label">黑名单</div>
        <div class="value">{{ stats.blacklistCount }}</div>
        <div class="desc">已拉黑设备，禁止接入网络</div>
      </div>
      <div class="card stat">
        <div class="icon green">监</div>
        <div class="label">监管设备</div>
        <div class="value">{{ stats.supervisionCount }}</div>
        <div class="desc">受时间段和时长规则控制的设备</div>
      </div>
      <div class="card stat">
        <div class="icon yellow">告</div>
        <div class="label">今日告警</div>
        <div class="value">{{ stats.todayAlerts }}</div>
        <div class="desc">白名单外设备异常连接行为</div>
      </div>
    </div>

    <div class="split">
      <div class="section">
        <div class="section-header">
          <div>
            <h3>快速操作</h3>
            <div class="sub">常用功能入口</div>
          </div>
          <button class="btn btn-primary" @click="router.push('/supervision')">+ 新增监管规则</button>
        </div>
        <div class="section-body">
          <div class="grid" style="grid-template-columns:repeat(3,1fr);">
            <div class="notice">终端管理：快速修改接入方式、限速、加入黑名单。</div>
            <div class="notice">黑名单：支持按 MAC 添加/删除，并保留来源记录。</div>
            <div class="notice">日志中心：记录连接时间、次数、时长和异常告警。</div>
          </div>
        </div>
      </div>

      <div class="section">
        <div class="section-header">
          <div>
            <h3>系统状态</h3>
            <div class="sub">路由器与授权信息</div>
          </div>
          <span style="display:inline-flex;padding:5px 10px;border-radius:999px;background:#ecfdf5;color:#047857;font-size:12px;font-weight:600;">
            Session {{ stats.sessionStatus }}
          </span>
        </div>
        <div class="section-body">
          <div class="kv">
            <div class="item"><div class="k">路由器地址</div><div class="v">{{ stats.routerAddress }}</div></div>
            <div class="item"><div class="k">授权状态</div><div class="v">{{ stats.sessionStatus }}</div></div>
            <div class="item"><div class="k">会话 ID</div><div class="v">{{ stats.sessionId || '-' }}</div></div>
            <div class="item"><div class="k">过期策略</div><div class="v">自动重登覆盖</div></div>
          </div>
        </div>
      </div>
    </div>

    <div class="section" style="margin-top:16px;">
      <div class="section-header">
        <div>
          <h3>近期告警</h3>
          <div class="sub">异常连接、超时和黑名单触发记录</div>
        </div>
      </div>
      <div class="section-body">
        <div class="timeline">
          <div class="timeline-item" v-for="log in recentLogs" :key="log.id">
            <div class="t">{{ formatTime(log.createTime) }}</div>
            <div class="c">{{ log.deviceName }} — {{ log.remark || log.logType }}</div>
          </div>
          <div v-if="recentLogs.length === 0" class="notice">暂无告警记录</div>
        </div>
      </div>
    </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardStats } from '../api/dashboard'
import { getLogs } from '../api/logs'
import dayjs from 'dayjs'

const router = useRouter()

const stats = ref({
  onlineDevices: 0,
  blacklistCount: 0,
  supervisionCount: 0,
  todayAlerts: 0,
  sessionStatus: '未登录',
  routerAddress: '-',
  sessionId: ''
})

const recentLogs = ref<any[]>([])
const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    const res: any = await getDashboardStats()
    stats.value = res.data
    const logRes: any = await getLogs({ page: 1, size: 10 })
    recentLogs.value = logRes.data || []
  } catch (e) {
    console.error('Failed to load dashboard', e)
  } finally {
    loading.value = false
  }
}

function formatTime(t: string) {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-'
}

onMounted(refresh)
</script>
