<template>
  <div class="data-page">
    <div class="data-head">
      <span class="data-title">数据管理</span>
      <div class="data-tabs">
        <button v-for="t in tabs" :key="t.key" type="button" class="data-tab" :class="{ active: tab === t.key }" @click="switchTab(t.key)">
          {{ t.label }}
        </button>
      </div>
    </div>

    <section class="panel data-body">
      <!-- 作品 -->
      <div v-if="tab === 'work'" class="panel-body">
        <div class="form-row">
          <input v-model="workTitle" class="input" placeholder="作品标题" />
          <input v-model="workGenre" class="input" placeholder="类型（可选）" style="max-width: 150px" />
          <button type="button" class="btn primary" @click="handleCreateWork">新建作品</button>
        </div>
        <table class="table">
          <thead><tr><th>标题</th><th>类型</th><th></th></tr></thead>
          <tbody>
            <tr v-for="w in works" :key="w.id">
              <td>{{ w.title }}</td>
              <td>{{ w.genre || '-' }}</td>
              <td class="row-actions">
                <button v-if="w.id !== currentWorkId" type="button" class="btn sm" @click="handleSwitch(w.id)">切换</button>
                <span v-else class="chip ok">当前</span>
                <button type="button" class="btn sm danger" @click="handleDeleteWork(w)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 设定 -->
      <div v-else-if="tab === 'setting'" class="panel-body">
        <div class="form-row">
          <input v-model="sEntity" class="input" placeholder="实体（如 主角）" />
          <input v-model="sAttribute" class="input" placeholder="属性（如 惯用手）" />
          <input v-model="sValue" class="input" placeholder="值（如 左撇子）" />
          <button type="button" class="btn primary" @click="handleSaveSetting">{{ sId ? '更新' : '新建' }}</button>
          <button v-if="sId" type="button" class="btn ghost" @click="resetSettingForm">取消</button>
        </div>
        <div class="hint" style="margin-bottom: 10px">保存设定会触发冲突扫描（拿新设定检索章节库），有矛盾时弹出待修正清单。</div>
        <table class="table">
          <thead><tr><th>实体</th><th>属性</th><th>值</th><th>版本</th><th></th></tr></thead>
          <tbody>
            <tr v-for="s in settings" :key="s.id">
              <td>{{ s.entity }}</td>
              <td>{{ s.attribute }}</td>
              <td>{{ s.value }}</td>
              <td>{{ s.version }}</td>
              <td class="row-actions">
                <button type="button" class="btn sm" @click="editSetting(s)">编辑</button>
                <button type="button" class="btn sm danger" @click="handleDeleteSetting(s.id)">删除</button>
              </td>
            </tr>
            <tr v-if="settings.length === 0"><td colspan="5" class="empty">暂无设定</td></tr>
          </tbody>
        </table>
      </div>

      <!-- 伏笔 -->
      <div v-else-if="tab === 'foreshadow'" class="panel-body">
        <div class="form-row">
          <input v-model="fContent" class="input" placeholder="伏笔内容" />
          <input v-model="fChapter" class="input" placeholder="埋设章节（可选）" style="max-width: 130px" />
          <button type="button" class="btn primary" @click="handleCreateForeshadow">新建伏笔</button>
        </div>
        <table class="table">
          <thead><tr><th>内容</th><th>埋设</th><th>状态</th><th></th></tr></thead>
          <tbody>
            <tr v-for="f in foreshadows" :key="f.id">
              <td>{{ f.content }}</td>
              <td>{{ f.plantedChapter || '-' }}</td>
              <td><span class="chip" :class="f.status === 'open' ? 'ok' : ''">{{ f.status }}</span></td>
              <td class="row-actions">
                <button v-if="f.status === 'open'" type="button" class="btn sm" @click="handleResolve(f.id)">回收</button>
                <button type="button" class="btn sm danger" @click="handleDeleteForeshadow(f.id)">删除</button>
              </td>
            </tr>
            <tr v-if="foreshadows.length === 0"><td colspan="4" class="empty">暂无伏笔</td></tr>
          </tbody>
        </table>
      </div>

      <!-- 待确认 -->
      <div v-else class="panel-body">
        <div class="hint" style="margin-bottom: 10px">一致性审查检出的 CONFLICT 会进此队列；采纳用新值更新设定，驳回保留旧值。</div>
        <table class="table">
          <thead><tr><th>实体</th><th>属性</th><th>旧值</th><th>新值</th><th>出处</th><th></th></tr></thead>
          <tbody>
            <tr v-for="p in pendings" :key="p.id">
              <td>{{ p.entity }}</td>
              <td>{{ p.attribute }}</td>
              <td>{{ p.oldValue || '-' }}</td>
              <td>{{ p.newValue }}</td>
              <td>ch#{{ p.chapterNo || '-' }}</td>
              <td class="row-actions">
                <button type="button" class="btn sm primary" @click="handleAccept(p.id)">采纳</button>
                <button type="button" class="btn sm" @click="handleReject(p.id)">驳回</button>
              </td>
            </tr>
            <tr v-if="pendings.length === 0"><td colspan="6" class="empty">暂无待确认</td></tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- 冲突清单弹层 -->
    <div v-if="conflicts.length" class="overlay" @click.self="conflicts = []">
      <div class="modal">
        <div class="modal-head">
          <span class="panel-title">待修正清单（{{ conflicts.length }} 处矛盾）</span>
          <button type="button" class="btn ghost sm" @click="conflicts = []">关闭</button>
        </div>
        <div class="modal-body">
          <div v-for="(c, i) in conflicts" :key="i" class="conflict-item">
            <div class="conflict-head mono">ch#{{ c.chapterNo }}</div>
            <div class="conflict-excerpt">{{ c.excerpt }}</div>
            <div class="hint">{{ c.reason }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 通用删除确认弹窗 -->
    <div v-if="confirm" class="overlay" @click.self="confirm = null">
      <div class="modal">
        <div class="modal-head">
          <span class="panel-title">{{ confirm.title }}</span>
          <button type="button" class="btn ghost sm" @click="confirm = null">关闭</button>
        </div>
        <div class="modal-body">
          <div class="delete-warn">{{ confirm.message }}</div>
          <div v-if="deleteError" class="error-box">{{ deleteError }}</div>
        </div>
        <div class="modal-foot">
          <button type="button" class="btn ghost" @click="confirm = null">取消</button>
          <button type="button" class="btn danger" :disabled="deleting" @click="confirm.onConfirm">
            {{ deleting ? '删除中…' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { listWorks, createWork, switchWork, deleteWork } from '../api/work';
import { listSettings, saveSetting, deleteSetting } from '../api/setting';
import { listForeshadows, createForeshadow, resolveForeshadow, deleteForeshadow } from '../api/foreshadow';
import { listPending, acceptPending, rejectPending } from '../api/review';
import { readAuth, writeAuth, clearAuth } from '../api/http';

const tabs = [
  { key: 'work', label: '作品' },
  { key: 'setting', label: '设定' },
  { key: 'foreshadow', label: '伏笔' },
  { key: 'pending', label: '待确认' }
];

const tab = ref('work');
const currentWorkId = ref(readAuth()?.workId || null);

// 作品
const works = ref([]);
const workTitle = ref('');
const workGenre = ref('');

// 设定
const settings = ref([]);
const sId = ref(null);
const sEntity = ref('');
const sAttribute = ref('');
const sValue = ref('');
const conflicts = ref([]);

// 伏笔
const foreshadows = ref([]);
const fContent = ref('');
const fChapter = ref('');

// 待确认
const pendings = ref([]);

const switchTab = (t) => {
  tab.value = t;
  if (t === 'work') loadWorks();
  else if (t === 'setting') loadSettings();
  else if (t === 'foreshadow') loadForeshadows();
  else loadPendings();
};

const loadWorks = async () => {
  works.value = await listWorks();
};

const loadSettings = async () => {
  settings.value = await listSettings();
};

const loadForeshadows = async () => {
  foreshadows.value = await listForeshadows();
};

const loadPendings = async () => {
  pendings.value = await listPending();
};

const handleCreateWork = async () => {
  if (!workTitle.value.trim()) return;
  await createWork(workTitle.value.trim(), workGenre.value.trim() || null);
  workTitle.value = '';
  workGenre.value = '';
  loadWorks();
};

const handleSwitch = async (id) => {
  const data = await switchWork(id);
  writeAuth(data); // 换 token
  window.location.reload(); // workId 变了，全站刷新
};

const resetSettingForm = () => {
  sId.value = null;
  sEntity.value = '';
  sAttribute.value = '';
  sValue.value = '';
};

const editSetting = (s) => {
  sId.value = s.id;
  sEntity.value = s.entity;
  sAttribute.value = s.attribute;
  sValue.value = s.value;
};

const handleSaveSetting = async () => {
  if (!sEntity.value.trim() || !sAttribute.value.trim() || !sValue.value.trim()) return;
  const r = await saveSetting({
    id: sId.value,
    entity: sEntity.value.trim(),
    attribute: sAttribute.value.trim(),
    value: sValue.value.trim()
  });
  resetSettingForm();
  await loadSettings();
  conflicts.value = r.conflicts || [];
};

const handleDeleteSetting = async (id) => {
  await deleteSetting(id);
  loadSettings();
};

const handleCreateForeshadow = async () => {
  if (!fContent.value.trim()) return;
  await createForeshadow(fContent.value.trim(), fChapter.value ? Number(fChapter.value) : null);
  fContent.value = '';
  fChapter.value = '';
  loadForeshadows();
};

const handleResolve = async (id) => {
  await resolveForeshadow(id, null);
  loadForeshadows();
};

const handleAccept = async (id) => {
  try {
    await acceptPending(id);
  } catch (e) {
    // 409 幂等：已处理，刷新即可
  }
  loadPendings();
};

const handleReject = async (id) => {
  try {
    await rejectPending(id);
  } catch (e) {
    // 409 幂等
  }
  loadPendings();
};

// 通用删除确认弹窗
const confirm = ref(null);
const deleting = ref(false);
const deleteError = ref('');

const askDelete = (title, message, onConfirm) => {
  deleteError.value = '';
  confirm.value = { title, message, onConfirm };
};

const doDelete = async (action) => {
  deleting.value = true;
  deleteError.value = '';
  try {
    await action();
    confirm.value = null;
  } catch (e) {
    deleteError.value = '删除失败：' + (e.response?.data?.message || e.message || '未知错误');
  } finally {
    deleting.value = false;
  }
};

const handleDeleteForeshadow = (id) => {
  askDelete('删除伏笔', '删除后不可恢复，该伏笔及其向量索引将被移除。', async () => {
    await doDelete(async () => {
      await deleteForeshadow(id);
      await loadForeshadows();
    });
  });
};

const handleDeleteWork = (work) => {
  const isCurrent = work.id === currentWorkId.value;
  askDelete(
    `删除作品「${work.title}」`,
    isCurrent
      ? '这是当前作品，删除后需重新登录。将级联删除该作品全部章节 / 设定 / 伏笔 / 时间线 / 向量，不可恢复。'
      : '将级联删除该作品全部章节 / 设定 / 伏笔 / 时间线 / 向量，不可恢复。',
    async () => {
      await doDelete(async () => {
        await deleteWork(work.id);
        if (isCurrent) {
          clearAuth();
          window.location.reload();
        } else {
          await loadWorks();
        }
      });
    }
  );
};

onMounted(loadWorks);
</script>

<style scoped>
.data-page {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.data-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.data-title {
  font-size: 15px;
  font-weight: 650;
}

.data-tabs {
  display: flex;
  gap: 4px;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 3px;
}

.data-tab {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  padding: 5px 14px;
  border-radius: 6px;
  cursor: pointer;
}

.data-tab.active {
  background: rgba(68, 147, 248, 0.16);
  color: var(--text);
}

.data-body {
  flex: 1;
  min-height: 0;
}

.form-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.form-row .input {
  flex: 1;
  min-width: 120px;
}

.row-actions {
  white-space: nowrap;
}

.row-actions .btn {
  margin-right: 4px;
}

.overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.55);
}

.modal {
  width: 100%;
  max-width: 640px;
  max-height: 84vh;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--bg-elevated);
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}

.modal-body {
  padding: 16px;
  overflow-y: auto;
  display: grid;
  gap: 10px;
}

.conflict-item {
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-inset);
}

.conflict-head {
  font-size: 12px;
  color: #e3b341;
  margin-bottom: 6px;
}

.conflict-excerpt {
  font-size: 13px;
  line-height: 1.6;
  padding: 8px 10px;
  margin: 6px 0;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.03);
  border-left: 2px solid var(--warn);
  white-space: pre-wrap;
}

.delete-warn {
  padding: 12px 14px;
  border: 1px solid rgba(248, 81, 73, 0.4);
  border-radius: var(--radius-sm);
  background: rgba(248, 81, 73, 0.06);
  font-size: 13px;
  line-height: 1.7;
}
</style>
