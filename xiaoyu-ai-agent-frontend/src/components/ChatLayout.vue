<template>
  <div class="chat-page">
    <div class="chat-header">
      <h2>{{ title }}</h2>
      <div v-if="chatId" class="chat-id">会话 ID：{{ chatId }}</div>
    </div>

    <div class="chat-window">
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="chat-message"
        :class="msg.sender === 'user' ? 'from-user' : 'from-ai'"
      >
        <div class="message-row">
          <img
            v-if="msg.sender === 'ai'"
            class="avatar"
            :src="aiAvatar"
            :alt="aiName"
            loading="lazy"
          />

          <div class="bubble">
            <div class="sender">{{ msg.sender === 'user' ? '我' : aiName }}</div>
            <div class="content">{{ msg.text }}</div>
          </div>

          <img
            v-if="msg.sender === 'user'"
            class="avatar"
            :src="userAvatar"
            alt="我"
            loading="lazy"
          />
        </div>
      </div>

      <div v-if="loading" class="chat-loading">AI 正在思考中...</div>
    </div>

    <form class="chat-input-area" @submit.prevent="handleSubmit">
      <textarea
        v-model="input"
        class="chat-input"
        rows="3"
        placeholder="请输入你的消息，回车发送"
        @keydown.enter.exact.prevent="handleSubmit"
      />
      <button type="submit" class="send-button" :disabled="!input.trim() || loading">
        发送
      </button>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const defaultUserAvatar =
  'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22128%22 height=%22128%22 viewBox=%220 0 128 128%22%3E%3Cdefs%3E%3ClinearGradient id=%22g%22 x1=%2220%22 y1=%2216%22 x2=%22108%22 y2=%22116%22 gradientUnits=%22userSpaceOnUse%22%3E%3Cstop stop-color=%22%230EA5E9%22/%3E%3Cstop offset=%221%22 stop-color=%22%2322C55E%22/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect x=%2210%22 y=%2210%22 width=%22108%22 height=%22108%22 rx=%2228%22 fill=%22url(%23g)%22/%3E%3Cpath d=%22M64 66c12 0 22-10 22-22S76 22 64 22 42 32 42 44s10 22 22 22Z%22 fill=%22white%22 fill-opacity=%220.92%22/%3E%3Cpath d=%22M30 108c6-18 20-28 34-28s28 10 34 28%22 stroke=%22white%22 stroke-width=%2210%22 stroke-linecap=%22round%22 opacity=%220.92%22/%3E%3C/svg%3E';

const props = defineProps({
  title: { type: String, required: true },
  aiName: { type: String, default: 'AI' },
  aiAvatar: { type: String, default: '' },
  userAvatar: { type: String, default: defaultUserAvatar },
  chatId: { type: String, default: '' },
  aiOutputMode: {
    type: String,
    default: 'stream' // 'stream' | 'steps'
  },
  buildSseUrl: {
    type: Function,
    required: true
  }
});

const messages = ref([]);
const input = ref('');
const loading = ref(false);
let currentAiIndex = null;
let currentEventSource = null;

const appendUserMessage = (text) => {
  messages.value.push({
    id: Date.now() + Math.random(),
    sender: 'user',
    text
  });
};

const startAiMessage = (text) => {
  const id = Date.now() + Math.random();
  messages.value.push({
    id,
    sender: 'ai',
    text
  });
  currentAiIndex = messages.value.length - 1;
};

const appendAiStepMessage = (text) => {
  messages.value.push({
    id: Date.now() + Math.random(),
    sender: 'ai',
    text
  });
  currentAiIndex = null;
};

const appendToCurrentAiMessage = (chunk) => {
  if (currentAiIndex == null || !messages.value[currentAiIndex]) {
    startAiMessage(chunk);
    return;
  }
  messages.value[currentAiIndex].text += chunk;
};

const cleanupEventSource = () => {
  if (currentEventSource) {
    currentEventSource.close();
    currentEventSource = null;
  }
};

const handleSubmit = () => {
  const text = input.value.trim();
  if (!text || loading.value) return;

  appendUserMessage(text);
  input.value = '';

  cleanupEventSource();

  const url = props.buildSseUrl(text, props.chatId);
  loading.value = true;
  currentAiIndex = null;

  const es = new EventSource(url);
  currentEventSource = es;

  es.onmessage = (event) => {
    if (!event.data) return;
    if (event.data === '[DONE]') {
      loading.value = false;
      cleanupEventSource();
      return;
    }
    if (props.aiOutputMode === 'steps') {
      appendAiStepMessage(event.data);
      return;
    }
    appendToCurrentAiMessage(event.data);
  };

  es.onerror = () => {
    loading.value = false;
    cleanupEventSource();
  };
};
</script>

