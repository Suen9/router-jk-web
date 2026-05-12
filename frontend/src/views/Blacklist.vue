<template>
  <div>
    <div class="topbar">
      <div>
        <h2>黑名单管理</h2>
        <p>列出当前黑名单设备，支持新增、删除、备注和来源记录</p>
      </div>
      <div class="toolbar">
        <button class="btn" @click="refresh" :disabled="loading">同步路由黑名单</button>
        <button class="btn btn-primary" @click="openAddModal">+ 添加黑名单</button>
      </div>
    </div>

    <a-spin :spinning="loading">
    <div class="section">
      <div class="section-header">
        <div>
          <h3>黑名单列表</h3>
          <div class="sub">当前路由中的黑名单设备</div>
        </div>
      </div>
      <div class="section-body">
        <div class="filters">
          <div class="field"><label>MAC</label><input v-model="filters.mac" placeholder="输入 MAC" /></div>
          <div class="field"><label>设备名</label><input v-model="filters.name" placeholder="输入设备名" /></div>
          <div class="field"><label>来源</label>
            <select v-model="filters.source"><option value="">全部</option><option>手动添加</option><option>监管自动拉黑</option><option>异常告警</option></select>
          </div>
          <div class="field"><label>状态</label>
            <select v-model="filters.status"><option value="">全部</option><option>已生效</option><option>待同步</option></select>
          </div>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>设备名</th><th>MAC</th><th>来源</th><th>备注</th><th>状态</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="(item, i) in filteredList" :key="i">
                <td>{{ item.name || '-' }}</td>
                <td>{{ item.mac }}</td>
                <td>{{ item.source || '手动添加' }}</td>
                <td>{{ item.remark || '-' }}</td>
                <td><span :class="['tag', item.status === '已生效' ? 'tag-success' : 'tag-warning']">{{ item.status || '已生效' }}</span></td>
                <td>
                  <div class="actions">
                    <button class="btn btn-mini" @click="openDetail(item)">查看详情</button>
                    <button class="btn btn-mini btn-danger" @click="handleDelete(item)">删除</button>
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
        <h4>黑名单新增规则</h4>
        <div class="field" style="margin-bottom:10px;"><label>设备名</label><input v-model="addForm.name" placeholder="请输入设备名" /></div>
        <div class="field" style="margin-bottom:10px;"><label>MAC 地址</label><input v-model="addForm.mac" placeholder="00:11:22:33:44:55" /></div>
        <div class="field" style="margin-bottom:10px;"><label>备注</label><textarea v-model="addForm.remark" placeholder="填写拉黑原因"></textarea></div>
        <div style="display:flex;justify-content:flex-end;gap:10px;">
          <button class="btn" @click="clearAddForm">取消</button>
          <button class="btn btn-primary" @click="handleAdd" :disabled="submitting">{{ submitting ? '提交中...' : '提交' }}</button>
        </div>
      </div>
      <div class="panel">
        <h4>操作说明</h4>
        <div class="notice">
          支持按 MAC 增删黑名单，记录来源与备注。<br />
          建议与终端管理联动：拉黑后自动踢下线或禁止接入。
        </div>
      </div>
    </div>

    <!-- Detail Modal -->
    <div class="modal-mask" v-if="showModal" @click.self="showModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3>黑名单详情</h3>
          <button class="close" @click="showModal = false">&times;</button>
        </div>
        <div class="modal-body">
          <div class="notice" style="margin-bottom:14px;">
            MAC: {{ detailItem?.mac }}<br/>设备名: {{ detailItem?.name || '-' }}<br/>来源: {{ detailItem?.source || '-' }}
          </div>
          <div class="field" style="margin-bottom:12px;"><label>备注</label><textarea v-model="detailRemark"></textarea></div>
          <div style="display:flex;justify-content:flex-end;gap:10px;">
            <button class="btn" @click="showModal = false">取消</button>
            <button class="btn btn-primary" @click="showModal = false">保存</button>
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
import { getBlacklist, addBlacklist, deleteBlacklist } from '../api/blacklist'

const list = ref<any[]>([])
const filters = ref({ mac: '', name: '', source: '', status: '' })

const filteredList = computed(() => {
  return list.value.filter(item => {
    if (filters.value.mac && !(item.mac || '').includes(filters.value.mac)) return false
    if (filters.value.name && !(item.name || '').includes(filters.value.name)) return false
    if (filters.value.source && item.source !== filters.value.source) return false
    if (filters.value.status && item.status !== filters.value.status) return false
    return true
  })
})

const addForm = ref({ name: '', mac: '', remark: '' })
const showModal = ref(false)
const detailItem = ref<any>(null)
const detailRemark = ref('')

const submitting = ref(false)
const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    const res: any = await getBlacklist()
    list.value = res.data || []
  } catch (e) { console.error(e) } finally { loading.value = false }
}

async function handleAdd() {
  if (!addForm.value.mac) { message.warning('请输入MAC地址'); return }
  submitting.value = true
  try {
    await addBlacklist({ mac: addForm.value.mac, name: addForm.value.name })
    message.success('添加成功')
    clearAddForm()
    refresh()
  } catch (e) { message.error('添加失败') } finally { submitting.value = false }
}

function clearAddForm() { addForm.value = { name: '', mac: '', remark: '' } }

async function handleDelete(item: any) {
  submitting.value = true
  try {
    await deleteBlacklist({ macList: [item.mac] })
    message.success('删除成功')
    refresh()
  } catch (e) { message.error('删除失败') } finally { submitting.value = false }
}

function openDetail(item: any) {
  detailItem.value = item
  detailRemark.value = item.remark || ''
  showModal.value = true
}

function openAddModal() {
  const el = document.querySelector('.panel .field input')
  if (el) { (el as HTMLInputElement).focus(); el.scrollIntoView({ behavior: 'smooth', block: 'center' }) }
}

onMounted(refresh)
</script>
