<template>
  <div class="kb-page">
    <div class="kb-toolbar">
      <div class="kb-title">知识库 · RAG 分块</div>
      <button type="button" class="btn primary" @click="openUpload">+ 上传章节</button>
    </div>

    <div class="kb-grid">
      <!-- 左：列表 -->
      <aside class="panel kb-side">
        <div class="kb-tabs">
          <button type="button" class="kb-tab" :class="{ active: tab === 'chapter' }" @click="switchTab('chapter')">
            章节
          </button>
          <button type="button" class="kb-tab" :class="{ active: tab === 'setting' }" @click="switchTab('setting')">
            设定库
          </button>
          <button type="button" class="kb-tab" :class="{ active: tab === 'foreshadow' }" @click="switchTab('foreshadow')">
            伏笔库
          </button>
        </div>

        <div class="kb-list">
          <template v-if="tab === 'chapter'">
            <div
              v-for="ch in chapters"
              :key="ch.id"
              class="kb-item"
              :class="{ active: selectedChapterId === ch.id }"
              @click="selectChapter(ch)"
            >
              <span class="kb-item-title">{{ ch.title }}</span>
              <span class="kb-item-meta mono">seq {{ ch.seq }}</span>
              <button
                type="button"
                class="kb-item-replace"
                title="覆盖该章节（重传替换，级联覆盖设定/伏笔）"
                @click.stop="askReplace(ch)"
              >⇄</button>
              <button
                type="button"
                class="kb-item-del"
                title="删除该章节（级联删除设定/伏笔/向量）"
                @click.stop="askDelete(ch)"
              >✕</button>
            </div>
            <div v-if="chapters.length === 0" class="empty">暂无章节，先上传一章</div>
          </template>
          <div v-else class="kb-list-tip hint">
            {{ tab === 'setting' ? '设定独立块：一条设定 = 一块，绝不切碎。' : '伏笔块：伏笔片段独立成块。' }}
          </div>
        </div>
      </aside>

      <!-- 右：分块详情 -->
      <section class="panel kb-detail">
        <div class="panel-head">
          <span class="panel-title">{{ detailTitle }}</span>
          <span class="panel-sub">{{ chunks.length }} 块</span>
        </div>
        <div class="panel-body">
          <div v-if="loadingChunks" class="hint">加载中…</div>
          <div v-else-if="chunks.length === 0" class="empty">
            {{ tab === 'chapter' ? '选择左侧章节查看分块' : '该库暂无分块' }}
          </div>
          <div v-else class="chunk-list">
            <div v-for="c in chunks" :key="c.id" class="chunk-card" :class="'chunk-' + c.type">
              <div class="chunk-head">
                <span class="chip">{{ c.type }}</span>
                <span class="chunk-meta mono">ch#{{ c.chapterNo || '-' }} · {{ c.length }} 字</span>
                <span v-if="c.entityId" class="chunk-meta mono">entityId={{ c.entityId }}</span>
                <span v-if="c.parentId" class="chunk-meta mono">parent={{ c.parentId }}</span>
              </div>
              <div class="chunk-content">{{ c.content }}</div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 上传弹窗 -->
    <div v-if="showUpload" class="overlay" @click.self="showUpload = false">
      <div class="modal">
        <div class="modal-head">
          <span class="panel-title">上传章节</span>
          <button type="button" class="btn ghost sm" @click="showUpload = false">关闭</button>
        </div>
        <div class="modal-body">
          <div class="field">
            <label class="label">上传文件（TXT / MD / PDF / Word，可多选批量）</label>
            <div class="upload-file">
              <input type="file" multiple accept=".txt,.md,.markdown,.pdf,.docx" @change="onFiles" />
              <span class="hint">PDF / Word 由后端解析为纯文本；多选即批量，逐个入库</span>
            </div>
            <div v-if="files.length" class="file-list">
              <div v-for="(f, i) in files" :key="i" class="file-item">
                <span class="file-name">{{ f.name }}</span>
                <span class="file-size mono">{{ fmtSize(f.size) }}</span>
                <button type="button" class="file-del" title="移除" @click="removeFile(i)">✕</button>
              </div>
            </div>
          </div>

          <div class="divider"><span>或直接粘贴正文</span></div>

          <div class="field">
            <label class="label">章节标题（留空自动命名「第 N 章」）</label>
            <input v-model="uploadTitle" class="input" placeholder="如：第 3 章" />
          </div>
          <div class="field">
            <label class="label">章节内容</label>
            <textarea v-model="uploadText" class="textarea" rows="8" placeholder="粘贴章节原文…"></textarea>
          </div>
          <div v-if="uploadError" class="error-box">{{ uploadError }}</div>
          <div v-else-if="uploadResult" class="success-box">{{ uploadResult }}</div>
        </div>
        <div class="modal-foot">
          <button type="button" class="btn ghost" @click="showUpload = false">取消</button>
          <button type="button" class="btn primary" :disabled="uploading" @click="handleUpload">
            {{ uploading ? '上传中…' : '上传' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 删除章节确认弹窗 -->
    <div v-if="confirmDelete" class="overlay" @click.self="confirmDelete = null">
      <div class="modal">
        <div class="modal-head">
          <span class="panel-title">删除章节「{{ confirmDelete.title }}」</span>
          <button type="button" class="btn ghost sm" @click="confirmDelete = null">关闭</button>
        </div>
        <div class="modal-body">
          <div class="delete-warn">
            该操作<b>不可恢复</b>，将级联删除：
            <ul>
              <li>章节原文 + 章节向量分块</li>
              <li>该章确立的设定（含向量索引）</li>
              <li>该章埋设的伏笔（含向量索引）</li>
              <li>该章时间线、待确认冲突、审查报告</li>
            </ul>
          </div>
          <div v-if="deleteError" class="error-box">{{ deleteError }}</div>
        </div>
        <div class="modal-foot">
          <button type="button" class="btn ghost" @click="confirmDelete = null">取消</button>
          <button type="button" class="btn danger" :disabled="deleting" @click="handleDelete">
            {{ deleting ? '删除中…' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 覆盖章节弹窗 -->
    <div v-if="confirmReplace" class="overlay" @click.self="confirmReplace = null">
      <div class="modal">
        <div class="modal-head">
          <span class="panel-title">覆盖章节「{{ confirmReplace.title }}」</span>
          <button type="button" class="btn ghost sm" @click="confirmReplace = null">关闭</button>
        </div>
        <div class="modal-body">
          <div class="delete-warn">
            覆盖将<b>级联替换</b>该章（原 seq 不变）：
            <ul>
              <li>章节原文 + 章节向量分块</li>
              <li>该章确立的设定（含向量索引）</li>
              <li>该章埋设的伏笔（含向量索引）</li>
              <li>该章时间线</li>
            </ul>
            请粘贴修正后的章节正文：
          </div>
          <div class="field">
            <label class="label">章节标题（留空沿用原标题）</label>
            <input v-model="replaceTitle" class="input" :placeholder="confirmReplace.title" />
          </div>
          <div class="field">
            <label class="label">章节正文</label>
            <textarea v-model="replaceText" class="textarea" rows="10" placeholder="粘贴修正后的章节原文…"></textarea>
          </div>
          <div v-if="replaceError" class="error-box">{{ replaceError }}</div>
        </div>
        <div class="modal-foot">
          <button type="button" class="btn ghost" @click="confirmReplace = null">取消</button>
          <button type="button" class="btn primary" :disabled="replacing" @click="handleReplace">
            {{ replacing ? '覆盖中…' : '确认覆盖' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { listChapters, uploadChapter, uploadChapterFile, deleteChapter, replaceChapter } from '../api/chapter';
import { listChunks } from '../api/rag';
import { workStore } from '../stores/work';

const tab = ref('chapter');
const chapters = ref([]);
const chunks = ref([]);
const selectedChapterId = ref(null);
const loadingChunks = ref(false);

const showUpload = ref(false);
const uploadTitle = ref('');
const uploadText = ref('');
const uploading = ref(false);
const uploadError = ref('');
const uploadResult = ref('');
const files = ref([]); // { name, size, file }

const detailTitle = computed(() => {
  if (tab.value === 'setting') return '设定库 · 独立块';
  if (tab.value === 'foreshadow') return '伏笔库 · 独立块';
  const ch = chapters.value.find((c) => c.id === selectedChapterId.value);
  return ch ? `章节「${ch.title}」父子分块` : '章节分块';
});

const loadChapters = async () => {
  chapters.value = await listChapters();
  if (chapters.value.length && selectedChapterId.value == null) {
    selectChapter(chapters.value[0]);
  }
};

const loadChunks = async () => {
  loadingChunks.value = true;
  try {
    if (tab.value === 'chapter') {
      if (selectedChapterId.value == null) {
        chunks.value = [];
        return;
      }
      chunks.value = await listChunks({ chapterId: selectedChapterId.value });
    } else {
      chunks.value = await listChunks({ type: tab.value });
    }
  } finally {
    loadingChunks.value = false;
  }
};

const selectChapter = (ch) => {
  selectedChapterId.value = ch.id;
  loadChunks();
};

const switchTab = (t) => {
  tab.value = t;
  if (t === 'chapter') loadChapters();
  else loadChunks();
};

const openUpload = () => {
  uploadError.value = '';
  uploadResult.value = '';
  showUpload.value = true;
};

const onFiles = (e) => {
  const list = Array.from(e.target.files || []);
  for (const file of list) {
    files.value.push({ name: file.name, size: file.size, file });
  }
  e.target.value = ''; // 清空，允许重复选同一文件
};

const removeFile = (i) => files.value.splice(i, 1);

const fmtSize = (s) => s < 1024 ? s + ' B'
  : s < 1048576 ? (s / 1024).toFixed(1) + ' KB'
  : (s / 1048576).toFixed(1) + ' MB';

const handleUpload = async () => {
  const text = uploadText.value.trim();
  const hasFiles = files.value.length > 0;
  if (!text && !hasFiles) {
    uploadError.value = '请选择文件或粘贴章节内容';
    return;
  }
  uploading.value = true;
  uploadError.value = '';
  uploadResult.value = '';
  try {
    if (hasFiles) {
      // 批量：顺序逐个上传（单文件可整本切多章，逐章入库；失败隔离，进度可展示）
      const results = [];
      for (let i = 0; i < files.value.length; i++) {
        const f = files.value[i];
        const title = f.name.replace(/\.[^.]+$/, '');
        const r = await uploadChapterFile(f.file, title);
        results.push(
          `「${f.name}」：切分 ${r.chapterCount} 章 · 新增设定 ${r.totalNewSettings} · 追加 ${r.totalUpdatedSettings} · 冲突 ${r.totalConflicts} · 伏笔 ${r.totalForeshadows}`
        );
        uploadResult.value = `已上传 ${i + 1}/${files.value.length} …`;
      }
      uploadResult.value = results.join('\n');
      files.value = [];
    } else {
      const r = await uploadChapter(uploadTitle.value.trim() || null, text);
      uploadResult.value =
        `「${r.title}」入库完成：新增设定 ${r.newSettings} · 追加设定 ${r.updatedSettings} · ` +
        `冲突待确认 ${r.conflicts} · 伏笔 ${r.foreshadows}`;
    }
    uploadTitle.value = '';
    uploadText.value = '';
    await loadChapters();
  } catch (e) {
    uploadError.value = '上传失败：' + (e.response?.data?.message || e.message || '未知错误');
  } finally {
    uploading.value = false;
  }
};

const confirmDelete = ref(null);
const deleting = ref(false);
const deleteError = ref('');

const askDelete = (ch) => {
  deleteError.value = '';
  confirmDelete.value = ch;
};

const handleDelete = async () => {
  const ch = confirmDelete.value;
  if (!ch) return;
  deleting.value = true;
  deleteError.value = '';
  try {
    await deleteChapter(ch.id);
    confirmDelete.value = null;
    if (selectedChapterId.value === ch.id) {
      selectedChapterId.value = null;
      chunks.value = [];
    }
    await loadChapters();
  } catch (e) {
    deleteError.value = '删除失败：' + (e.response?.data?.message || e.message || '未知错误');
  } finally {
    deleting.value = false;
  }
};

const confirmReplace = ref(null);
const replaceTitle = ref('');
const replaceText = ref('');
const replacing = ref(false);
const replaceError = ref('');

const askReplace = (ch) => {
  replaceError.value = '';
  replaceTitle.value = '';
  replaceText.value = '';
  confirmReplace.value = ch;
};

const handleReplace = async () => {
  const ch = confirmReplace.value;
  if (!ch) return;
  const text = replaceText.value.trim();
  if (!text) {
    replaceError.value = '请粘贴修正后的章节正文';
    return;
  }
  replacing.value = true;
  replaceError.value = '';
  try {
    await replaceChapter(ch.id, replaceTitle.value.trim() || null, text);
    confirmReplace.value = null;
    replaceTitle.value = '';
    replaceText.value = '';
    // 覆盖后章节 id 会变，按原 seq 重新定位选中
    const list = await listChapters();
    chapters.value = list;
    const same = list.find((c) => c.seq === ch.seq);
    selectedChapterId.value = same ? same.id : null;
    if (same) await loadChunks();
    else chunks.value = [];
  } catch (e) {
    replaceError.value = '覆盖失败：' + (e.response?.data?.message || e.message || '未知错误');
  } finally {
    replacing.value = false;
  }
};

onMounted(loadChapters);

// 切换作品：清空章节与分块缓存，重拉新作品的章节列表
watch(() => workStore.version, () => {
  chapters.value = [];
  chunks.value = [];
  selectedChapterId.value = null;
  loadChapters();
});
</script>

<style scoped>
.kb-page {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.kb-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kb-title {
  font-size: 15px;
  font-weight: 650;
}

.kb-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 12px;
}

.kb-side {
  min-height: 0;
}

.kb-tabs {
  display: flex;
  gap: 4px;
  padding: 8px;
  border-bottom: 1px solid var(--border);
}

.kb-tab {
  flex: 1;
  padding: 6px 0;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
}

.kb-tab.active {
  background: var(--accent-soft);
  color: var(--text);
}

.kb-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.kb-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  color: var(--text-muted);
}

.kb-item:hover {
  background: var(--hover);
}

.kb-item.active {
  background: var(--accent-soft);
  color: var(--text);
}

.kb-item-title {
  font-size: 13px;
}

.kb-item-meta {
  font-size: 11px;
  color: var(--text-faint);
}

.kb-list-tip {
  padding: 10px;
}

.kb-detail {
  min-height: 0;
}

.chunk-list {
  display: grid;
  gap: 10px;
}

.chunk-card {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-inset);
  padding: 10px 12px;
}

.chunk-card.chunk-setting {
  border-color: rgba(122, 63, 208, 0.35);
}

.chunk-card.chunk-foreshadow {
  border-color: rgba(192, 88, 26, 0.35);
}

.chunk-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.chunk-meta {
  font-size: 11px;
  color: var(--text-faint);
}

.chunk-content {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
  font-family: var(--font-mono);
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
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.5);
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
  gap: 14px;
}

.modal-foot {
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.upload-file {
  display: grid;
  gap: 6px;
}

.file-list {
  display: grid;
  gap: 6px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-inset);
}

.file-name {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  font-size: 11px;
  color: var(--text-faint);
  flex: 0 0 auto;
}

.file-del {
  border: none;
  background: transparent;
  color: var(--text-faint);
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
  padding: 2px 6px;
  border-radius: 4px;
}

.file-del:hover {
  color: var(--danger);
  background: var(--danger-soft);
}

.divider {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-faint);
  font-size: 12px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border);
}

.kb-item-del,
.kb-item-replace {
  border: none;
  background: transparent;
  color: var(--text-faint);
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
  padding: 3px 7px;
  border-radius: 4px;
  opacity: 0;
  transition: opacity 0.15s;
}

.kb-item:hover .kb-item-del,
.kb-item:hover .kb-item-replace {
  opacity: 1;
}

.kb-item-del:hover {
  color: var(--danger);
  background: var(--danger-soft);
}

.kb-item-replace:hover {
  color: var(--accent);
  background: var(--accent-soft);
}

.delete-warn {
  padding: 12px 14px;
  border: 1px solid rgba(198, 47, 47, 0.35);
  border-radius: var(--radius-sm);
  background: var(--danger-soft);
  font-size: 13px;
  line-height: 1.7;
}

.delete-warn ul {
  margin: 8px 0 0;
  padding-left: 18px;
}
</style>
