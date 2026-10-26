<template>
  <div class="chat-shell">
    <!-- 左栏：会话列表 -->
    <aside class="chat-side">
      <div class="chat-work">
        <span class="chat-work-label">作品</span>
        <span class="chat-work-name">{{ workStore.current?.title || '未命名作品' }}</span>
      </div>
      <button type="button" class="btn ghost" style="width: 100%" @click="newConversation">+ 新对话</button>
      <div class="conv-list">
        <div
          v-for="conv in conversations"
          :key="conv.chatId"
          class="conv-item"
          :class="{ active: conv.chatId === currentChatId }"
          @click="openConversation(conv)"
        >
          <div class="conv-title">{{ conv.preview || '（空会话）' }}</div>
          <div class="conv-meta">{{ conv.messageCount }} 条 · {{ fmtTime(conv.updatedAt) }}</div>
          <button type="button" class="conv-del" title="删除会话" @click.stop="removeConversation(conv)">×</button>
        </div>
        <div v-if="conversations.length === 0" class="chat-side-note">
          <span class="hint">还没有历史会话，点「+ 新对话」开始。</span>
        </div>
      </div>
    </aside>

    <!-- 中栏：对话主区 -->
    <section class="chat-main">
      <div ref="scrollEl" class="chat-window">
        <div v-if="messages.length === 0" class="empty">
          写新章节前输入一段剧情，LoreKeeper 会路由意图、检索设定并生成提示卡。
        </div>

        <div
          v-for="msg in messages"
          :key="msg.id"
          class="chat-message"
          :class="msg.role === 'user' ? 'from-user' : 'from-ai'"
          @click="selectedId = msg.id"
        >
          <div class="msg-row">
            <div v-if="msg.role === 'ai'" class="avatar">L</div>

            <div class="bubble">
              <div class="bubble-head">
                <span class="sender">{{ msg.role === 'user' ? '我' : 'LoreKeeper' }}</span>
                <span v-if="msg.role === 'ai' && msg.intent" class="intent-badge" :class="intentCls(msg.intent)">
                  <span class="dot" aria-hidden="true"></span>{{ intentLabel(msg.intent) }} · {{ msg.intent }}
                </span>
              </div>

              <div v-if="msg.text" class="content">{{ msg.text }}</div>
              <div v-else-if="msg.streaming" class="content streaming">正在路由意图并检索设定…</div>
              <div v-if="msg.error" class="content err">{{ msg.error }}</div>

              <div v-if="msg.tools && msg.tools.length" class="trace-toggle" @click.stop="msg.showTools = !msg.showTools">
                {{ msg.showTools ? '▾' : '▸' }} Agent 过程（{{ msg.tools.length }} 步）
              </div>
            </div>

            <div v-if="msg.role === 'user'" class="avatar user">我</div>
          </div>
        </div>

        <div v-if="loading" class="chat-loading">LoreKeeper 思考中…</div>
      </div>

      <div class="chat-input-bar">
        <textarea
          v-model="input"
          class="input"
          rows="3"
          placeholder="输入剧情或问题，Enter 发送、Shift+Enter 换行"
          @keydown.enter.exact.prevent="handleSubmit"
        />
        <div class="input-actions">
          <button v-if="loading" type="button" class="btn danger sm" @click="stop">停止</button>
          <button type="button" class="btn primary" :disabled="!input.trim() || loading" @click="handleSubmit">
            发送
          </button>
        </div>
      </div>
    </section>

    <!-- 右栏：Inspector（可折叠） -->
    <aside class="inspector" :class="{ collapsed: !showInspector }">
      <div class="inspector-head">
        <span class="panel-title">Inspector</span>
        <button type="button" class="btn ghost sm" @click="showInspector = !showInspector">
          {{ showInspector ? '收起' : '展开' }}
        </button>
      </div>
      <div v-if="showInspector" class="inspector-body">
        <template v-if="selectedMsg">
          <div class="ins-section">
            <div class="ins-label">本轮意图</div>
            <div v-if="selectedMsg.intent" class="intent-badge" :class="intentCls(selectedMsg.intent)">
              <span class="dot" aria-hidden="true"></span>{{ intentLabel(selectedMsg.intent) }} · {{ selectedMsg.intent }}
            </div>
            <div v-else class="hint">等待路由…</div>
          </div>

          <div class="ins-section">
            <div class="ins-label">工具调用 Trace</div>
            <div v-if="selectedMsg.tools && selectedMsg.tools.length" class="trace-list">
              <div v-for="(t, i) in selectedMsg.tools" :key="i" class="tool-step" :class="'kind-' + t.kind">
                <div class="t-icon" aria-hidden="true">{{ toolIcon(t) }}</div>
                <div class="t-body">
                  <div class="t-name">{{ t.step ? `Step ${t.step} · ` : '' }}{{ t.name || 'note' }}</div>
                  <div v-if="t.result" class="t-result">{{ t.result }}</div>
                </div>
              </div>
            </div>
            <div v-else class="hint">暂无工具调用</div>
          </div>
        </template>
        <div v-else class="empty">点击左侧消息查看详情</div>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { streamAssist } from '../api/assist';
import { listConversations, getConversation, deleteConversation } from '../api/chat';
import { intentLabel, intentCls } from '../utils/intent';
import { workStore } from '../stores/work';

const messages = ref([]);
const input = ref('');
const loading = ref(false);
const scrollEl = ref(null);
const selectedId = ref(null);
const showInspector = ref(true);
// 多会话：当前会话 ID + 历史会话列表
const currentChatId = ref(null);
const conversations = ref([]);

let ctrl = null;

const uid = () => Date.now() + '-' + Math.random().toString(36).slice(2, 8);

const selectedMsg = computed(() => messages.value.find((m) => m.id === selectedId.value) || null);

// 工具事件解析：Step N: 工具xxx完成了它的任务！结果：...
function parseToolEvent(data) {
  const stepMatch = data.match(/^Step (\d+):\s*/);
  const step = stepMatch ? Number(stepMatch[1]) : null;
  const body = stepMatch ? data.slice(stepMatch[0].length) : data;
  const toolMatch = body.match(/^工具([A-Za-z_][\w]*)完成了它的任务！结果：([\s\S]*)$/);
  if (toolMatch) {
    return { step, name: toolMatch[1], result: toolMatch[2], kind: kindOf(toolMatch[1]) };
  }
  return { step, name: null, result: body, kind: 'note' };
}

function kindOf(name) {
  if (['parse_document', 'query_database', 'send_email'].includes(name)) return 'mcp';
  if (name === 'settingSearch') return 'rag';
  if (name === 'doTerminate') return 'end';
  return 'note';
}

function toolIcon(t) {
  if (t.kind === 'mcp') return 'MCP';
  if (t.kind === 'rag') return 'RAG';
  if (t.kind === 'end') return 'END';
  return '···';
}

const scrollToBottom = async () => {
  await nextTick();
  if (scrollEl.value) scrollEl.value.scrollTop = scrollEl.value.scrollHeight;
};

// 记住当前会话 ID 的 localStorage key（用于刷新后恢复）
const LAST_CHAT_KEY = 'lorekeeper_last_chat';

// 生成新的会话 ID
const genChatId = () => 'c-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8);

// 新建会话（空会话不落盘，首次发消息后才出现在历史列表）
const newConversation = () => {
  if (loading.value) stop();
  currentChatId.value = genChatId();
  localStorage.setItem(LAST_CHAT_KEY, currentChatId.value);
  messages.value = [];
  selectedId.value = null;
  scrollToBottom();
};

// 加载历史会话列表
const loadConversations = async () => {
  try {
    conversations.value = await listConversations();
  } catch (e) {
    // 后端不可用时静默，不影响对话
  }
};

// 打开历史会话，回放文本
const openConversation = async (conv) => {
  if (loading.value) stop();
  currentChatId.value = conv.chatId;
  localStorage.setItem(LAST_CHAT_KEY, conv.chatId);
  selectedId.value = null;
  try {
    const history = await getConversation(conv.chatId);
    messages.value = history.map((m) => ({
      id: uid(),
      role: m.role,
      text: m.content,
      intent: '',
      tools: [],
      error: '',
      streaming: false,
      showTools: false
    }));
  } catch (e) {
    messages.value = [];
  }
  scrollToBottom();
};

// 删除会话
const removeConversation = async (conv) => {
  try {
    await deleteConversation(conv.chatId);
  } catch (e) {
    // 忽略删除失败
  }
  if (currentChatId.value === conv.chatId) {
    newConversation();
  }
  loadConversations();
};

// 时间格式化：M/D HH:mm
const fmtTime = (ts) => {
  if (!ts) return '';
  const d = new Date(ts);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getMonth() + 1}/${d.getDate()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const handleSubmit = () => {
  const text = input.value.trim();
  if (!text || loading.value) return;
  input.value = '';

  const aiMsg = {
    id: uid(),
    role: 'ai',
    intent: '',
    text: '',
    tools: [],
    error: '',
    streaming: true,
    showTools: false
  };
  messages.value.push({ id: uid(), role: 'user', text });
  messages.value.push(aiMsg);
  selectedId.value = aiMsg.id;
  loading.value = true;
  scrollToBottom();

  ctrl = streamAssist(text, {
    onIntent: (d) => {
      aiMsg.intent = d;
    },
    onTool: (d) => {
      aiMsg.tools.push(parseToolEvent(d));
      scrollToBottom();
    },
    onAnswer: (d) => {
      aiMsg.text = d;
      aiMsg.streaming = false;
      scrollToBottom();
    },
    onError: (err) => {
      aiMsg.error = err.message || '未知错误';
      aiMsg.streaming = false;
      loading.value = false;
      scrollToBottom();
    },
    onDone: () => {
      loading.value = false;
      aiMsg.streaming = false;
      if (!aiMsg.text && !aiMsg.error) {
        aiMsg.error = '未生成最终回答（Agent 可能未正常结束）';
      }
      scrollToBottom();
      // 发送完成后刷新会话列表（新会话 / 更新预览）
      loadConversations();
    }
  }, currentChatId.value);
};

const stop = () => {
  if (ctrl) {
    ctrl.abort();
    ctrl = null;
  }
  loading.value = false;
};

onMounted(async () => {
  await loadConversations();
  // 刷新后恢复上次会话：从 localStorage 读 chatId，若仍在列表则回放，否则新建
  const lastChatId = localStorage.getItem(LAST_CHAT_KEY);
  const last = lastChatId ? conversations.value.find((c) => c.chatId === lastChatId) : null;
  if (last) {
    await openConversation(last);
  } else {
    newConversation();
  }
});

// 切换作品：中止进行中的对话流，清空会话上下文，重拉新作品的会话列表并新建
watch(() => workStore.version, async () => {
  if (ctrl) ctrl.abort();
  loading.value = false;
  messages.value = [];
  selectedId.value = null;
  currentChatId.value = null;
  localStorage.removeItem(LAST_CHAT_KEY);
  await loadConversations();
  newConversation();
});

onBeforeUnmount(() => {
  if (ctrl) ctrl.abort();
});
</script>

<style scoped>
.chat-shell {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 172px 1fr 300px;
  gap: 12px;
}

.chat-side {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-elevated);
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.chat-work {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 2px 4px 0;
  min-width: 0;
}

.chat-work-label {
  font-size: 11px;
  color: var(--text-faint);
  flex: 0 0 auto;
}

.chat-work-name {
  font-size: 12.5px;
  color: var(--text);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-side-note {
  padding: 0 4px;
}

.conv-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.conv-item {
  position: relative;
  padding: 8px 26px 8px 10px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-inset);
  cursor: pointer;
  transition: border-color 0.12s ease, background 0.12s ease;
}

.conv-item:hover {
  border-color: var(--border-strong);
}

.conv-item.active {
  border-color: var(--accent);
  background: rgba(68, 147, 248, 0.12);
}

.conv-title {
  font-size: 12.5px;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-meta {
  margin-top: 3px;
  font-size: 11px;
  color: var(--text-faint);
}

.conv-del {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 18px;
  height: 18px;
  line-height: 1;
  border: none;
  border-radius: 5px;
  background: transparent;
  color: var(--text-faint);
  font-size: 14px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.12s ease, color 0.12s ease, background 0.12s ease;
}

.conv-item:hover .conv-del {
  opacity: 1;
}

.conv-del:hover {
  color: #ffa198;
  background: var(--bg-elevated);
}

.chat-main {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-elevated);
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.chat-window {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.chat-message {
  cursor: pointer;
}

.msg-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.chat-message.from-user .msg-row {
  flex-direction: row-reverse;
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--accent), #1f6feb);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
}

.avatar.user {
  background: var(--bg-inset);
  border: 1px solid var(--border-strong);
  color: var(--text-muted);
  font-size: 11px;
}

.bubble {
  max-width: min(680px, 80%);
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--bg-inset);
  border: 1px solid var(--border);
}

.chat-message.from-user .bubble {
  background: rgba(68, 147, 248, 0.12);
  border-color: rgba(68, 147, 248, 0.3);
}

.bubble-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.sender {
  font-size: 12px;
  color: var(--text-muted);
}

.content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

.content.streaming {
  color: var(--text-faint);
  font-style: italic;
}

.content.err {
  color: #ffa198;
}

.trace-toggle {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border);
  font-size: 12px;
  color: var(--text-muted);
  cursor: pointer;
  user-select: none;
}

.trace-toggle:hover {
  color: var(--text);
}

.chat-loading {
  color: var(--text-faint);
  font-size: 13px;
  text-align: center;
}

.chat-input-bar {
  border-top: 1px solid var(--border);
  padding: 10px 12px;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.chat-input-bar .input {
  flex: 1;
}

.input-actions {
  display: flex;
  gap: 6px;
  flex-direction: column;
}

.inspector {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-elevated);
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.inspector.collapsed {
  width: auto;
}

.inspector-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
}

.inspector-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ins-section {
  display: grid;
  gap: 8px;
}

.ins-label {
  font-size: 11px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  color: var(--text-faint);
}

.trace-list {
  display: grid;
  gap: 8px;
}
</style>
