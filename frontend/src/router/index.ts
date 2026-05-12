import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('../components/AppLayout.vue'),
      children: [
        { path: '', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
        { path: 'terminal', name: 'Terminal', component: () => import('../views/Terminal.vue') },
        { path: 'blacklist', name: 'Blacklist', component: () => import('../views/Blacklist.vue') },
        { path: 'supervision', name: 'Supervision', component: () => import('../views/Supervision.vue') },
        { path: 'whitelist', name: 'Whitelist', component: () => import('../views/Whitelist.vue') },
        { path: 'logs', name: 'Logs', component: () => import('../views/Logs.vue') },
        { path: 'pc', name: 'PcManagement', component: () => import('../views/PcManagement.vue') },
        { path: 'pc/detail/:id', name: 'PcDetail', component: () => import('../views/PcDetail.vue') }
      ]
    }
  ]
})

export default router
