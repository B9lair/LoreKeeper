<template>
  <ChatLayout
    :title="'AI 超级智能体'"
    :ai-name="'小渝 Manus'"
    :ai-avatar="manusAvatar"
    ai-output-mode="steps"
    chat-id=""
    :build-sse-url="buildSseUrl"
  />
</template>

<script setup>
import ChatLayout from '../components/ChatLayout.vue';
import manusAvatar from '../assets/ai-manus.svg';

const buildSseUrl = (message) => {
  const apiBase =
    process.env.NODE_ENV === 'production' ? '/api' : 'http://localhost:8080/api';
  const base = `${apiBase}/ai/manus/chat`;
  const params = new URLSearchParams({
    message
  });
  return `${base}?${params.toString()}`;
};
</script>

