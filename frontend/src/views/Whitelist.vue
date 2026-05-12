<template>
  <div>
    <div class="topbar">
      <div>
        <h2>白名单管理</h2>
        <p>管理白名单设备，白名单内设备不受异常连接监控</p>
      </div>
      <div class="toolbar">
        <button class="btn" @click="refresh">刷新列表</button>
        <button class="btn btn-primary" @click="openAddModal">+ 添加白名单</button>
      </div>
    </div>

    <a-spin :spinning="loading">
    <div class="section">
      <div class="section-header">
        <div>
          <h3>白名单设备列表</h3>
          <div class="sub">已加入白名单的设备，不受异常连接监控</div>
        </div>
      </div>
      <div class="section-body">
        <div class="filters">
          <div class="field"><label>设备名</label><input v-model="filters.hostname" placeholder="输入设备名" /></div>
          <div class="field"><label>MAC 地址</label><input v-model="filters.mac" placeholder="输入 MAC" /></div>
          <div class="field"><label>备注</label><input v-model="filters.remark" placeholder="输入备注关键字" /></div>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>设备名</th><th>MAC</th><th>备注</th><th>添加时间</th><th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in filteredList" :key="item.id">
                <td>{{ item.hostname || '-' }}</td>
                <td>{{ item.mac }}</td>
                <td>{{ item.remark || '-' }}</td>
                <td>{{ item.createTime || '-' }}</td>
                <td>
                  <div class="actions">
                    <button class="btn btn-mini" @click="openEdit(item)">编辑</button>
                    <button class="btn btn-mini btn-danger" @click="handleRemove(item.id)">移除</button>
                  </div>
                </td>
              </tr>
              <tr v-if="filteredList.length === 0">
                <td colspan="5" style="text-align:center;color:var(--sub);">暂无白名单设备</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Add/Edit Modal -->
    <div class="modal-mask" v-if="showModal" @click.self="showModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ editMode ? '编辑白名单' : '添加白名单设备' }}</h3>
          <button class="close" @click="showModal = false">&times;</button>
        </div>
        <div class="modal-body">
          <div class="field" style="margin-bottom:12px;"><label>设备名</label><input v-model="form.hostname" placeholder="请输入设备名" /></div>
          <div class="field" style="margin-bottom:12px;"><label>MAC 地址</label><input v-model="form.mac" placeholder="00:11:22:33:44:55" /></div>
          <div class="field" style="margin-bottom:12px;"><label>备注</label><textarea v-model="form.remark" placeholder="备注信息"></textarea></div>
          <div style="display:flex;justify-content:flex-end;gap:10px;">
            <button class="btn" @click="showModal = false">取消</button>
            <button class="btn btn-primary" @click="confirmSave" :disabled="submitting">{{ submitting ? '提交中...' : (editMode ? '保存' : '确认添加') }}</button>
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
import { getWhitelist, addWhitelist, removeWhitelist } from '../api/whitelist'

const list = ref<any[]>([])
const filters = ref({ hostname: '', mac: '', remark: '' })

const filteredList = computed(() => {
  return list.value.filter((item: any) => {
    if (filters.value.hostname && !(item.hostname || '').includes(filters.value.hostname)) return false
    if (filters.value.mac && !(item.mac || '').includes(filters.value.mac)) return false
    if (filters.value.remark && !(item.remark || '').includes(filters.value.remark)) return false
    return true
  })
})

const showModal = ref(false)
const editMode = ref(false)
const form = ref({ hostname: '', mac: '', remark: '' })

const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    const res: any = await getWhitelist()
    list.value = res.data || []
  } catch (e) { console.error(e) } finally { loading.value = false }
}

function openAddModal() {
  form.value = { hostname: '', mac: '', remark: '' }
  editMode.value = false
  showModal.value = true
}

function openEdit(item: any) {
  form.value = { hostname: item.hostname || '', mac: item.mac || '', remark: item.remark || '' }
  editMode.value = true
  showModal.value = true
}

const submitting = ref(false)

async function confirmSave() {
  if (!form.value.mac) { message.warning('请输入MAC地址'); return }
  submitting.value = true
  try {
    await addWhitelist(form.value)
    message.success(editMode.value ? '保存成功' : '添加成功')
    showModal.value = false
    refresh()
  } catch (e) { message.error('操作失败') } finally { submitting.value = false }
}

async function handleRemove(id: number) {
  try {
    await removeWhitelist(id)
    message.success('已移除')
    refresh()
  } catch (e) { message.error('移除失败') }
}

onMounted(refresh)
</script>
