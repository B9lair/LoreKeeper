<template>
  <ChatLayout
    :title="'AI 恋爱大师'"
    :ai-name="'恋爱大师'"
    :ai-avatar="loveAvatar"
    :chat-id="chatId"
    :build-sse-url="buildSseUrl"
  />
</template>

<script setup>
import { onMounted, ref } from 'vue';
import ChatLayout from '../components/ChatLayout.vue';
import loveAvatar from '../assets/ai-love.svg';

const chatId = ref('');

const generateChatId = () => {
  const random = Math.random().toString(36).slice(2, 8);
  const timestamp = Date.now().toString(36);
  return `love-${timestamp}-${random}`;
};

onMounted(() => {
  chatId.value = generateChatId();
});

const buildSseUrl = (message, currentChatId) => {
  const apiBase =
    process.env.NODE_ENV === 'production' ? '/api' : 'http://localhost:8123/api';
  const base = `${apiBase}/ai/love_app/chat/sse`;
  const params = new URLSearchParams({
    message,
    chatId: currentChatId
  });
  return `${base}?${params.toString()}`;
};
</script>

