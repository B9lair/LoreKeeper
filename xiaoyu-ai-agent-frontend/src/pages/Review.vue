<template>
  <div class="review-page">
    <div class="review-head">
      <span class="review-title">一致性审查</span>
    </div>

    <section class="panel">
      <div class="panel-head">
        <span class="panel-title">审查输入</span>
      </div>
      <div class="panel-body">
        <div class="field">
          <label class="label">上传新章节（TXT / MD / PDF / Word）</label>
          <div class="upload-file">
            <input
              type="file"
              accept=".txt,.md,.markdown,.pdf,.docx"
              @change="onFile"
            />
          </div>
          <div v-if="file" class="file-item">
            <span class="file-name">{{ file.name }}</span>
            <span class="file-size mono">{{ fmtSize(file.size) }}</span>
            <button
              type="button"
              class="file-del"
              title="移除"
              @click="file = null"
            >
              ✕
            </button>
          </div>
        </div>

        <div class="field" style="margin-top: 12px">
          <label class="label">章节号（可选，用于标注矛盾出处）</label>
          <input
            v-model="chapterNo"
            class="input"
            type="number"
            min="1"
            placeholder="如 50"
            style="max-width: 140px"
          />
        </div>

        <div class="divider"><span>或直接粘贴正文</span></div>

        <div class="field">
          <label class="label">章节正文</label>
          <textarea
            v-model="chapterText"
            class="textarea"
            rows="6"
            placeholder="粘贴新章节原文…"
          ></textarea>
        </div>

        <div class="action-row">
          <button
            type="button"
            class="btn primary"
            :disabled="reviewing"
            @click="handleReview"
          >
            {{ reviewing ? "审查中…" : "开始审查" }}
          </button>
          <span v-if="reviewing" class="hint"
            >正在抽取实体并逐条比对已有设定，可能需要数十秒…</span
          >
        </div>

        <div v-if="error" class="error-box">{{ error }}</div>
      </div>
    </section>

    <section v-if="report" class="panel">
      <div class="panel-head">
        <span class="panel-title">矛盾清单</span>
        <span class="panel-sub">{{ report.conflicts.length }} 处</span>
      </div>
      <div class="panel-body">
        <template v-if="report.conflicts.length">
          <table class="table">
            <thead>
              <tr>
                <th>实体</th>
                <th>本章断言</th>
                <th>历史断言</th>
                <th>本章</th>
                <th>历史章节</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(c, i) in report.conflicts" :key="i">
                <td>{{ c.entity }}</td>
                <td>{{ c.descA }}</td>
                <td>{{ c.descB }}</td>
                <td class="mono">ch#{{ c.chapterA }}</td>
                <td class="mono">ch#{{ c.chapterB }}</td>
              </tr>
            </tbody>
          </table>
        </template>
        <div v-else class="empty">未发现矛盾，本章设定与已有设定一致。</div>
      </div>
    </section>

    <section v-if="report" class="panel">
      <div class="panel-head">
        <span class="panel-title">未回收伏笔</span>
        <span class="panel-sub"
          >{{ report.foreshadowReminders.length }} 个</span
        >
      </div>
      <div class="panel-body">
        <ul v-if="report.foreshadowReminders.length" class="reminder-list">
          <li v-for="(f, i) in report.foreshadowReminders" :key="i">{{ f }}</li>
        </ul>
        <div v-else class="empty">暂无未回收伏笔。</div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { reviewChapter, reviewFile } from "../api/review";

const file = ref(null);
const chapterNo = ref("");
const chapterText = ref("");
const reviewing = ref(false);
const error = ref("");
const report = ref(null);

const onFile = (e) => {
  const f = e.target.files && e.target.files[0];
  file.value = f || null;
  e.target.value = "";
};

const fmtSize = (s) =>
  s < 1024
    ? s + " B"
    : s < 1048576
      ? (s / 1024).toFixed(1) + " KB"
      : (s / 1048576).toFixed(1) + " MB";

const handleReview = async () => {
  const no = chapterNo.value ? Number(chapterNo.value) : null;
  if (!file.value && !chapterText.value.trim()) {
    error.value = "请选择文件或粘贴章节正文";
    return;
  }
  reviewing.value = true;
  error.value = "";
  report.value = null;
  try {
    if (file.value) {
      report.value = await reviewFile(file.value, no);
    } else {
      report.value = await reviewChapter(no, chapterText.value.trim());
    }
  } catch (e) {
    error.value =
      "审查失败：" + (e.response?.data?.message || e.message || "未知错误");
  } finally {
    reviewing.value = false;
  }
};
</script>

<style scoped>
.review-page {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.review-title {
  font-size: 15px;
  font-weight: 650;
}

.upload-file input[type="file"] {
  color: var(--text-muted);
  font-size: 13px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-inset);
  max-width: 480px;
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
  background: rgba(248, 81, 73, 0.14);
}

.divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 14px 0;
  color: var(--text-faint);
  font-size: 12px;
}

.divider::before,
.divider::after {
  content: "";
  flex: 1;
  height: 1px;
  background: var(--border);
}

.action-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
}

.reminder-list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 6px;
  color: var(--text);
  font-size: 13px;
  line-height: 1.6;
}
</style>
