<template>
  <ChatLayout
    :title="'LoreKeeper 设定守护者'"
    :ai-name="'设定守护者'"
    :ai-avatar="loreAvatar"
    :chat-id="chatId"
    :build-sse-url="buildSseUrl"
  />
</template>

<script setup>
import { onMounted, ref } from 'vue';
import ChatLayout from '../components/ChatLayout.vue';
import loreAvatar from '../assets/ai-lore.svg';

const chatId = ref('');

const generateChatId = () => {
  const random = Math.random().toString(36).slice(2, 8);
  const timestamp = Date.now().toString(36);
  return `lore-${timestamp}-${random}`;
};

onMounted(() => {
  chatId.value = generateChatId();
});

const buildSseUrl = (message, currentChatId) => {
  const apiBase =
    process.env.NODE_ENV === 'production' ? '/api' : 'http://localhost:8080/api';
  const base = `${apiBase}/ai/lore/chat/sse`;
  const params = new URLSearchParams({
    message,
    chatId: currentChatId
  });
  return `${base}?${params.toString()}`;
};
</script>
