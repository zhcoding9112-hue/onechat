<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pricingApi, type ItemView, type SpecView } from '@/admin/api/pricing'
import { centsToYuan, formatPrice, yuanToCents } from '@/utils/money'

const items = ref<ItemView[]>([])
const loading = ref(false)

// ---- 项目 ----
const itemDialog = reactive({
  visible: false,
  editing: null as ItemView | null,
  code: '',
  name: '',
  description: '',
  sortOrder: 0,
})

// ---- 档位 ----
const specDialog = reactive({
  visible: false,
  itemId: 0,
  itemName: '',
  editing: null as SpecView | null,
  code: '',
  name: '',
  durationMinutes: undefined as number | undefined,
  sortOrder: 0,
})

// ---- 价格 ----
const priceDialog = reactive({
  visible: false,
  talentId: 1 as number,
  spec: null as SpecView | null,
  yuan: '',
})

// el-table 的插槽把 row 类型抹成 DefaultRow，这里统一收口成本页的类型
function asSpec(row: unknown): SpecView {
  return row as SpecView
}

onMounted(reload)

async function reload() {
  loading.value = true
  try {
    items.value = await pricingApi.listItems()
  } finally {
    loading.value = false
  }
}

function openCreateItem() {
  Object.assign(itemDialog, {
    visible: true, editing: null, code: '', name: '', description: '', sortOrder: 0,
  })
}

function openEditItem(item: ItemView) {
  Object.assign(itemDialog, {
    visible: true,
    editing: item,
    code: item.code,
    name: item.name,
    description: item.description ?? '',
    sortOrder: item.sortOrder,
  })
}

async function submitItem() {
  if (!itemDialog.name.trim()) {
    ElMessage.warning('项目名称不能为空')
    return
  }
  if (itemDialog.editing) {
    await pricingApi.updateItem(itemDialog.editing.id, {
      name: itemDialog.name,
      description: itemDialog.description,
      sortOrder: itemDialog.sortOrder,
    })
  } else {
    if (!itemDialog.code.trim()) {
      ElMessage.warning('项目编码不能为空')
      return
    }
    await pricingApi.createItem({
      code: itemDialog.code,
      name: itemDialog.name,
      description: itemDialog.description,
      sortOrder: itemDialog.sortOrder,
    })
  }
  itemDialog.visible = false
  ElMessage.success('已保存')
  await reload()
}

async function toggleItem(item: ItemView) {
  const next = !item.enabled
  if (!next) {
    await ElMessageBox.confirm(
      `停用「${item.name}」后，粉丝端将不再展示该项目的下单入口。既有订单不受影响。`,
      '确认停用',
      { type: 'warning' },
    )
  }
  await pricingApi.setItemEnabled(item.id, next)
  ElMessage.success(next ? '已启用' : '已停用')
  await reload()
}

function openCreateSpec(item: ItemView) {
  Object.assign(specDialog, {
    visible: true, itemId: item.id, itemName: item.name, editing: null,
    code: '', name: '', durationMinutes: undefined, sortOrder: 0,
  })
}

function openEditSpec(item: ItemView, spec: SpecView) {
  Object.assign(specDialog, {
    visible: true, itemId: item.id, itemName: item.name, editing: spec,
    code: spec.code, name: spec.name,
    durationMinutes: spec.durationMinutes ?? undefined,
    sortOrder: spec.sortOrder,
  })
}

async function submitSpec() {
  if (!specDialog.name.trim()) {
    ElMessage.warning('档位名称不能为空')
    return
  }
  if (specDialog.editing) {
    await pricingApi.updateSpec(specDialog.editing.id, {
      name: specDialog.name,
      durationMinutes: specDialog.durationMinutes,
      sortOrder: specDialog.sortOrder,
    })
  } else {
    if (!specDialog.code.trim()) {
      ElMessage.warning('档位编码不能为空')
      return
    }
    await pricingApi.createSpec(specDialog.itemId, {
      code: specDialog.code,
      name: specDialog.name,
      durationMinutes: specDialog.durationMinutes,
      sortOrder: specDialog.sortOrder,
    })
  }
  specDialog.visible = false
  ElMessage.success('已保存')
  await reload()
}

async function toggleSpec(spec: SpecView) {
  await pricingApi.setSpecEnabled(spec.id, !spec.enabled)
  ElMessage.success(spec.enabled ? '已停用' : '已启用')
  await reload()
}

function openPrice(spec: SpecView) {
  Object.assign(priceDialog, { visible: true, spec, yuan: '' })
}

async function submitPrice() {
  const cents = yuanToCents(priceDialog.yuan)
  if (cents === null || cents <= 0) {
    ElMessage.warning('请输入正确的金额，最多两位小数')
    return
  }
  if (!priceDialog.spec) return
  await pricingApi.setPrice(priceDialog.talentId, priceDialog.spec.id, cents)
  priceDialog.visible = false
  ElMessage.success(`已设为 ${formatPrice(cents)}`)
}
</script>

<template>
  <div v-loading="loading">
    <div class="toolbar">
      <el-button type="primary" @click="openCreateItem">新增服务项目</el-button>
      <el-button @click="reload">刷新</el-button>
    </div>

    <el-empty v-if="!loading && items.length === 0" description="还没有服务项目" />

    <el-card v-for="item in items" :key="item.id" class="item-card" shadow="never">
      <template #header>
        <div class="item-header">
          <span class="item-name">{{ item.name }}</span>
          <el-tag size="small" type="info">{{ item.code }}</el-tag>
          <el-tag size="small" :type="item.enabled ? 'success' : 'danger'">
            {{ item.enabled ? '启用中' : '已停用' }}
          </el-tag>
          <span class="spacer" />
          <el-button link type="primary" @click="openEditItem(item)">编辑</el-button>
          <el-button link type="primary" @click="openCreateSpec(item)">新增档位</el-button>
          <el-button link :type="item.enabled ? 'danger' : 'success'" @click="toggleItem(item)">
            {{ item.enabled ? '停用' : '启用' }}
          </el-button>
        </div>
      </template>

      <el-table :data="item.specs" size="small" empty-text="该项目还没有档位">
        <el-table-column prop="name" label="档位" min-width="120" />
        <el-table-column prop="code" label="编码" min-width="100" />
        <el-table-column label="时长" min-width="90">
          <template #default="scope">
            {{ asSpec(scope.row).durationMinutes ? `${asSpec(scope.row).durationMinutes} 分钟` : '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="70" />
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <el-tag size="small" :type="asSpec(scope.row).enabled ? 'success' : 'info'">
              {{ asSpec(scope.row).enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="openEditSpec(item, asSpec(scope.row))">编辑</el-button>
            <el-button link type="primary" size="small" @click="openPrice(asSpec(scope.row))">配价</el-button>
            <el-button link size="small" :type="asSpec(scope.row).enabled ? 'danger' : 'success'"
                       @click="toggleSpec(asSpec(scope.row))">
              {{ asSpec(scope.row).enabled ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 项目弹窗 -->
    <el-dialog v-model="itemDialog.visible" :title="itemDialog.editing ? '编辑服务项目' : '新增服务项目'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="编码">
          <el-input v-model="itemDialog.code" :disabled="!!itemDialog.editing" placeholder="如 listen" />
          <div v-if="itemDialog.editing" class="hint">编码是报表与埋点的归类依据，不可修改</div>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="itemDialog.name" placeholder="如 情感倾听" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="itemDialog.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="itemDialog.sortOrder" :min="0" />
          <div class="hint">数字小的排在前面</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitItem">保存</el-button>
      </template>
    </el-dialog>

    <!-- 档位弹窗 -->
    <el-dialog v-model="specDialog.visible" :title="specDialog.editing ? '编辑档位' : `为「${specDialog.itemName}」新增档位`" width="460px">
      <el-form label-width="90px">
        <el-form-item label="编码">
          <el-input v-model="specDialog.code" :disabled="!!specDialog.editing" placeholder="如 m30" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="specDialog.name" placeholder="如 30分钟" />
        </el-form-item>
        <el-form-item label="时长">
          <el-input-number v-model="specDialog.durationMinutes" :min="1" placeholder="分钟" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="specDialog.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="specDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitSpec">保存</el-button>
      </template>
    </el-dialog>

    <!-- 配价弹窗 -->
    <el-dialog v-model="priceDialog.visible" title="设定主播价格" width="420px">
      <el-form label-width="90px">
        <el-form-item label="档位">
          <span>{{ priceDialog.spec?.name }}</span>
        </el-form-item>
        <el-form-item label="主播 ID">
          <el-input-number v-model="priceDialog.talentId" :min="1" />
          <div class="hint">主播档案建立后此处将改为下拉选择</div>
        </el-form-item>
        <el-form-item label="单价">
          <el-input v-model="priceDialog.yuan" placeholder="元，最多两位小数">
            <template #prepend>¥</template>
          </el-input>
          <div v-if="yuanToCents(priceDialog.yuan) !== null" class="hint">
            将保存为 {{ yuanToCents(priceDialog.yuan) }} 分（{{ centsToYuan(yuanToCents(priceDialog.yuan)!) }} 元）
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="priceDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitPrice">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
}

.item-card {
  margin-bottom: 16px;
}

.item-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-name {
  font-size: 15px;
  font-weight: 600;
}

.spacer {
  flex: 1;
}

.hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
