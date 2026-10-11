<template>
  <div class="page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="page-title">Jenkins 服务器</div>
      <a-space>
        <a-button @click="goPipelines"> <rollback-outlined />返回流水线 </a-button>
        <a-button type="primary" @click="openCreate"> <plus-outlined />新增服务器 </a-button>
      </a-space>
    </div>

    <a-alert
      type="info"
      show-icon
      message="配置 Jenkins 实例后，流水线即可选择「Jenkins 执行」：平台会调用 Jenkins Remote API 触发 Job 并跟踪构建结果。API Token 加密存储且不会回显。"
      style="margin-bottom: 12px"
    />

    <!-- 服务器列表 -->
    <div class="panel">
      <a-table :data-source="servers" :columns="columns" row-key="id" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">
              {{ record.enabled ? '已启用' : '已停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button
                size="small"
                type="link"
                :loading="testingId === record.id"
                @click="test(record)"
              >
                测试连接
              </a-button>
              <a-button size="small" type="link" @click="openJobs(record)">Job 列表</a-button>
              <a-button size="small" type="link" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm
                title="确认删除？引用该服务器的流水线将执行失败"
                @confirm="remove(record)"
              >
                <a-button size="small" type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 新增 / 编辑 -->
    <a-modal
      v-model:open="modalOpen"
      :title="form.id ? '编辑 Jenkins 服务器' : '新增 Jenkins 服务器'"
      width="560"
      @ok="save"
    >
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="如：测试环境 Jenkins" />
        </a-form-item>
        <a-form-item label="Jenkins 地址" required>
          <a-input v-model:value="form.baseUrl" placeholder="http://jenkins.example.com:8080" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="认证方式">
              <a-select v-model:value="form.authType" :options="authTypeOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="用户名">
              <a-input v-model:value="form.username" placeholder="Basic 认证必填" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item :label="form.id ? 'API Token（留空表示不修改）' : 'API Token'">
          <a-input-password
            v-model:value="form.apiToken"
            placeholder="Jenkins 用户 → 设置 → API Token"
          />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="2" />
        </a-form-item>
        <a-form-item label="启用">
          <a-switch v-model:checked="form.enabled" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Job 列表 -->
    <a-modal v-model:open="jobsOpen" title="Jenkins Job 列表" width="520">
      <a-spin :spinning="jobsLoading">
        <a-list size="small" bordered :data-source="jobs">
          <template #renderItem="{ item }">
            <a-list-item>{{ item }}</a-list-item>
          </template>
        </a-list>
      </a-spin>
      <template #footer>
        <a-button @click="jobsOpen = false">关闭</a-button>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
/**
 * Jenkins 服务器配置页（管理员）：维护流水线可调用的 Jenkins 实例。
 *
 * <p>出于安全考虑，API Token 只在提交时上传、落库加密，接口不会回显；
 * 编辑时留空表示保持原有凭据不变。</p>
 */
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { PlusOutlined, RollbackOutlined } from '@ant-design/icons-vue';
import { JenkinsApi } from '@/api';
import type { JenkinsServer } from '@/types';
import { formatDateTime } from '@/utils/format';

const router = useRouter();

const servers = ref<JenkinsServer[]>([]);
const loading = ref(false);

/** 认证方式选项（与后端 authType 取值一致） */
const authTypeOptions = [
  { value: 'basic', label: 'Basic（用户名 + API Token）' },
  { value: 'token', label: 'Bearer（仅 API Token）' },
];

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '地址', dataIndex: 'baseUrl' },
  { title: '用户名', dataIndex: 'username', width: 140 },
  { title: '状态', key: 'enabled', width: 90 },
  {
    title: '更新时间',
    key: 'updatedAt',
    width: 180,
    customRender: ({ record }: any) => formatDateTime(record.updatedAt),
  },
  { title: '操作', key: 'action', width: 280 },
];

// ---------------- 表单 ----------------
const modalOpen = ref(false);
const form = reactive<JenkinsServer>({
  name: '',
  baseUrl: '',
  username: '',
  apiToken: '',
  authType: 'basic',
  enabled: true,
  description: '',
});

// ---------------- 测试连接 / Job 列表 ----------------
const testingId = ref('');
const jobsOpen = ref(false);
const jobsLoading = ref(false);
const jobs = ref<string[]>([]);

async function reload() {
  loading.value = true;
  try {
    servers.value = await JenkinsApi.list();
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    name: '',
    baseUrl: '',
    username: '',
    apiToken: '',
    authType: 'basic',
    enabled: true,
    description: '',
  });
  modalOpen.value = true;
}

function openEdit(record: JenkinsServer) {
  Object.assign(form, {
    id: record.id,
    name: record.name,
    baseUrl: record.baseUrl,
    username: record.username,
    apiToken: '', // 不回显旧 Token，留空表示不修改
    authType: record.authType ?? 'basic',
    enabled: record.enabled ?? true,
    description: record.description,
  });
  modalOpen.value = true;
}

async function save() {
  if (!form.name?.trim()) {
    message.warning('请填写名称');
    return;
  }
  if (!form.baseUrl?.trim()) {
    message.warning('请填写 Jenkins 地址');
    return;
  }
  if (form.id) {
    await JenkinsApi.update(form.id, { ...form });
  } else {
    await JenkinsApi.create({ ...form });
  }
  message.success('已保存');
  modalOpen.value = false;
  await reload();
}

async function remove(record: JenkinsServer) {
  if (!record.id) return;
  await JenkinsApi.remove(record.id);
  message.success('已删除');
  await reload();
}

/** 连通性测试：把结果直接提示给用户，便于排查地址/凭据错误 */
async function test(record: JenkinsServer) {
  if (!record.id) return;
  testingId.value = record.id;
  try {
    const resp = await JenkinsApi.test(record.id);
    if (resp.success) {
      message.success(`连接成功${resp.version ? '，Jenkins ' + resp.version : ''}`);
    } else {
      message.error(resp.message || '连接失败');
    }
  } finally {
    testingId.value = '';
  }
}

async function openJobs(record: JenkinsServer) {
  if (!record.id) return;
  jobsOpen.value = true;
  jobsLoading.value = true;
  try {
    jobs.value = await JenkinsApi.jobs(record.id);
    if (jobs.value.length === 0) {
      message.warning('未获取到 Job，请确认地址与凭据正确');
    }
  } finally {
    jobsLoading.value = false;
  }
}

function goPipelines() {
  router.push({ name: 'ci-pipelines' });
}

onMounted(reload);
</script>
