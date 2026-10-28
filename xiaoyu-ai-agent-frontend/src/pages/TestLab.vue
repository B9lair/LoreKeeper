<template>
  <div class="test-page">
    <div class="test-title">测试台</div>

    <div class="test-grid">
      <!-- 卡片 1：MCP 工具测试 -->
      <section class="panel card">
        <div class="panel-head">
          <span class="panel-title">MCP 工具测试</span>
          <span class="panel-sub">
            <span class="chip" :class="mcp?.database ? 'ok' : 'danger'">database {{ mcp?.database ? '●' : '○' }}</span>
          </span>
        </div>
        <div class="panel-body">
          <div class="test-block">
            <div class="block-title mono">query_database</div>
            <div class="block-row">
              <select v-model="queryTable" class="select" style="max-width: 160px">
                <option v-for="t in tables" :key="t" :value="t">{{ t }}</option>
              </select>
              <input v-model="queryFields" class="input" placeholder="字段（逗号分隔，留空查全部）" />
              <input v-model="queryConditions" class="input" placeholder="条件（如 status=open）" />
              <button type="button" class="btn primary sm" :disabled="queryLoading" @click="runQuery">查询</button>
            </div>
            <pre v-if="queryResult" class="out">{{ queryResult }}</pre>
            <div v-if="queryError" class="error-box">{{ queryError }}</div>
          </div>
        </div>
      </section>

      <!-- 卡片 2：RAG 检索测试 -->
      <section class="panel card">
        <div class="panel-head">
          <span class="panel-title">RAG 检索测试</span>
        </div>
        <div class="panel-body">
          <div class="block-row">
            <input v-model="ragQuery" class="input" placeholder="检索查询，如：主角 青阳城 女二" />
            <input v-model="ragTopK" class="input" type="number" min="1" max="10" style="max-width: 70px" title="topK" />
            <button type="button" class="btn primary sm" :disabled="ragLoading" @click="runRag">检索</button>
          </div>
          <div v-if="ragHits.length" class="hit-list">
            <div v-for="(h, i) in ragHits" :key="i" class="hit">
              <div class="hit-rank">#{{ i + 1 }}</div>
              <div class="hit-body">
                <div class="hit-content">{{ h.content }}</div>
                <div class="hit-meta mono" v-if="h.metadata">
                  <span v-for="(v, k) in h.metadata" :key="k">{{ k }}={{ v }} </span>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="hint">输入 query 查看混合检索 top-k 命中</div>
        </div>
      </section>
    </div>

  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue';
import { mcpStatus, mcpQuery } from '../api/debug';
import { searchRag } from '../api/rag';
import { workStore } from '../stores/work';

const tables = ['character', 'world_setting', 'timeline_event', 'foreshadow'];

// MCP
const mcp = ref(null);
const queryTable = ref('world_setting');
const queryFields = ref('');
const queryConditions = ref('');
const queryResult = ref('');
const queryError = ref('');
const queryLoading = ref(false);

// RAG
const ragQuery = ref('');
const ragTopK = ref(3);
const ragHits = ref([]);
const ragLoading = ref(false);

const loadStatus = async () => {
  try {
    mcp.value = await mcpStatus();
  } catch (e) {
    mcp.value = { database: false };
  }
};

const runQuery = async () => {
  queryLoading.value = true;
  queryResult.value = '';
  queryError.value = '';
  try {
    const r = await mcpQuery(queryTable.value, queryFields.value, queryConditions.value);
    queryResult.value = r.result || r.error || '';
  } catch (e) {
    queryError.value = '查询失败：' + (e.message || '未知错误');
  } finally {
    queryLoading.value = false;
  }
};

const runRag = async () => {
  if (!ragQuery.value.trim()) return;
  ragLoading.value = true;
  try {
    ragHits.value = await searchRag(ragQuery.value.trim(), Number(ragTopK.value) || 3);
  } catch (e) {
    ragHits.value = [];
  } finally {
    ragLoading.value = false;
  }
};

onMounted(loadStatus);

// 切换作品：清空测试结果缓存，重拉 MCP 状态
watch(() => workStore.version, () => {
  queryResult.value = '';
  queryError.value = '';
  ragHits.value = [];
  loadStatus();
});
</script>

<style scoped>
.test-page {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.test-title {
  font-size: 15px;
  font-weight: 650;
}

.test-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  align-items: start;
}

.card {
  min-height: 0;
}

.panel-sub {
  display: inline-flex;
  gap: 6px;
}

.test-block {
  display: grid;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}

.test-block:last-child {
  border-bottom: none;
}

.block-title {
  font-size: 12px;
  color: var(--accent);
  font-weight: 600;
}

.block-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.block-row .input {
  flex: 1;
  min-width: 140px;
}

.out {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--bg-inset);
  border: 1px solid var(--border);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
  color: var(--text-muted);
}

.hit-list {
  display: grid;
  gap: 8px;
  margin-top: 6px;
}

.hit {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-inset);
}

.hit-rank {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--accent);
  flex: 0 0 auto;
}

.hit-body {
  min-width: 0;
}

.hit-content {
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.hit-meta {
  margin-top: 6px;
  font-size: 11px;
  color: var(--text-faint);
  word-break: break-all;
}

</style>
