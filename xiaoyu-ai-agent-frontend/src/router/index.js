import { createRouter, createWebHistory } from 'vue-router';
import Home from '../pages/Home.vue';
import LoveAppChat from '../pages/LoveAppChat.vue';
import ManusChat from '../pages/ManusChat.vue';

const routes = [
  {
    path: '/',
    name: 'home',
    component: Home,
    meta: {
      title: '小渝 AI 应用｜极客风格 AI 聊天',
      description: '一个极客风格的 AI 应用入口：AI 恋爱大师与 AI 超级智能体，SSE 实时流式对话。'
    }
  },
  {
    path: '/love-app',
    name: 'love-app',
    component: LoveAppChat,
    meta: {
      title: 'AI 恋爱大师｜小渝 AI 应用',
      description: '恋爱问题分析与建议：SSE 流式聊天，会话 chatId 区分不同对话。'
    }
  },
  {
    path: '/manus',
    name: 'manus',
    component: ManusChat,
    meta: {
      title: 'AI 超级智能体｜小渝 AI 应用',
      description: '超级智能体多步骤推理：SSE 实时输出，每个步骤独立气泡展示。'
    }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

function upsertMetaTag(attrName, attrValue, content) {
  if (!content) return;
  const selector = `meta[${attrName}="${attrValue}"]`;
  let tag = document.head.querySelector(selector);
  if (!tag) {
    tag = document.createElement('meta');
    tag.setAttribute(attrName, attrValue);
    document.head.appendChild(tag);
  }
  tag.setAttribute('content', content);
}

router.afterEach((to) => {
  const title = to.meta?.title;
  const description = to.meta?.description;

  if (title) document.title = title;
  if (description) {
    upsertMetaTag('name', 'description', description);
    upsertMetaTag('property', 'og:title', title || document.title);
    upsertMetaTag('property', 'og:description', description);
  }
});

export default router;

