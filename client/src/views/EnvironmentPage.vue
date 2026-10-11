<template>
  <div class="page">
    <!-- 页头：标题 + 主操作 -->
    <div class="page-header">
      <div class="page-title">环境管理</div>
      <a-space>
        <a-button type="primary" @click="openCreate"> <plus-outlined />新建环境 </a-button>
      </a-space>
    </div>

    <!-- 环境表格 -->
    <div class="panel">
      <a-table :data-source="envs" :columns="columns" row-key="id" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button size="small" type="link" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="确认删除？" @confirm="remove(record)">
                <a-button size="small" type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal v-model:open="modal" :title="form.id ? '编辑环境' : '新建环境'" width="680" @ok="save">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="名称" required>
              <a-input v-model:value="form.name" placeholder="如 开发 / 测试 / 生产" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="BaseURL">
              <a-input v-model:value="form.baseUrl" placeholder="https://api.example.com" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="公共 Header">
          <KeyValueEditor v-model="form.headers" />
        </a-form-item>
        <a-form-item label="公共变量">
          <KeyValueEditor v-model="form.variables" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
/**
 * 环境管理页：维护各环境的 baseUrl、公共请求头与环境变量。
 *
 * <p>环境的 variables/headers 在后端以 JSON 字符串存储，
 * api 层已做数组与字符串的双向转换，本页直接按数组编辑即可。</p>
 */
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import { EnvironmentApi } from '@/api';
import { useProjectStore } from '@/stores/project';
import type { Environment, KeyValueItem } from '@/types';
import KeyValueEditor from '@/components/KeyValueEditor.vue';

const projectStore = useProjectStore();
const projectId = computed(() => projectStore.currentProjectId);

const envs = ref<Environment[]>([]);
const loading = ref(false);
const modal = ref(false);
/**
 * 环境新建 / 编辑表单模型。
 *
 * <p>headers / variables 在模板里直接绑定 KeyValueEditor（要求数组），
 * 因此不声明为可选，避免多余的空值分支。
 */
interface EnvironmentForm {
  id?: string;
  name: string;
  baseUrl: string;
  headers: KeyValueItem[];
  variables: KeyValueItem[];
}

const form = reactive<EnvironmentForm>({
  name: '',
  baseUrl: '',
  headers: [],
  variables: [],
});

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: 'BaseURL', dataIndex: 'baseUrl', ellipsis: true },
  {
    title: '公共 Header',
    key: 'headerCount',
    width: 100,
    customRender: ({ record }: any) => record.headers?.length || 0,
  },
  {
    title: '公共变量',
    key: 'varCount',
    width: 100,
    customRender: ({ record }: any) => record.variables?.length || 0,
  },
  { title: '操作', key: 'action', width: 140 },
];

async function reload() {
  if (!projectId.value) return;
  loading.value = true;
  try {
    envs.value = await EnvironmentApi.list(projectId.value);
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    name: '',
    baseUrl: '',
    headers: [] as KeyValueItem[],
    variables: [] as KeyValueItem[],
  });
  modal.value = true;
}

/** 打开编辑：可选字段补默认值，保证表单绑定不出现 undefined */
function openEdit(record: Environment) {
  // baseUrl 在 Environment 中可选，补齐为 ''；数组字段保证非空
  Object.assign(form, record, {
    baseUrl: record.baseUrl ?? '',
    headers: record.headers ?? [],
    variables: record.variables ?? [],
  });
  modal.value = true;
}

async function save() {
  if (!form.name) {
    message.warning('请填写名称');
    return;
  }
  const payload = { ...form, projectId: projectId.value };
  if (form.id) {
    await EnvironmentApi.update(form.id, form);
  } else {
    await EnvironmentApi.create(payload);
  }
  message.success('已保存');
  modal.value = false;
  await reload();
}

async function remove(record: Environment) {
  await EnvironmentApi.remove(record.id);
  await reload();
}

watch(projectId, reload, { immediate: false });
onMounted(reload);
</script>

<style scoped>
/* BaseURL 等可能较长的列在窄屏不撑破表格 */
:deep(.ant-table-tbody > tr > td) {
  word-break: break-all;
}
</style>
