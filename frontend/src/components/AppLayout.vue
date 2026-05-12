<template>
  <div class="app">
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="brand">
        <div class="brand-badge">
          <img src="/logo.png" alt="logo" style="width:100%;height:100%;object-fit:cover;border-radius:12px;" />
        </div>
        <div v-show="!sidebarCollapsed || !isMobile">
          <h1>路由监控系统</h1>
          <p>基于锐捷 RG-MA3063 API</p>
        </div>
      </div>

      <router-link v-for="item in navItems" :key="item.path" :to="item.path" class="nav-item"
        :class="{ active: currentPath === item.path }" @click="onNavClick">
        <span>{{ item.title }}</span>
        <small>{{ item.desc }}</small>
      </router-link>

      <div class="tip" v-show="!sidebarCollapsed || !isMobile">
        <b>说明</b><br />
        1. 设备控制：允许上网 / 禁网 / 不允许接入 / 限速 / 拉黑。<br />
        2. 已考虑 SessionID 过期后自动重登。<br />
        3. 本系统无需用户登录。
      </div>
    </aside>

    <div class="menu-overlay" v-if="isMobile && !sidebarCollapsed" @click="sidebarCollapsed = true"></div>

    <main class="main">
      <div class="mobile-header" v-if="isMobile">
        <button class="hamburger" @click="sidebarCollapsed = !sidebarCollapsed">&#9776;</button>
        <span class="mobile-title">路由监控系统</span>
      </div>
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const currentPath = computed(() => route.path)

const isMobile = ref(false)
const sidebarCollapsed = ref(false)

const navItems = [
  { path: '/', title: '首页概览', desc: 'Dashboard' },
  { path: '/terminal', title: '终端管理', desc: 'Devices' },
  { path: '/blacklist', title: '黑名单管理', desc: 'Blacklist' },
  { path: '/supervision', title: '监管设备', desc: 'Supervision' },
  { path: '/whitelist', title: '白名单管理', desc: 'Whitelist' },
  { path: '/logs', title: '日志中心', desc: 'Logs' }
]

function onNavClick() {
  if (isMobile.value) sidebarCollapsed.value = true
}

function checkMobile() {
  isMobile.value = window.innerWidth <= 900
  if (window.innerWidth > 900) sidebarCollapsed.value = false
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})
onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>
