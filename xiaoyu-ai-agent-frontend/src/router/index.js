import { createRouter, createWebHistory } from 'vue-router';
import { readAuth } from '../api/http';
import { demoLogin } from '../api/auth';

const routes = [
  {
    path: '/',
    name: 'chat',
    component: () => import('../pages/Chat.vue'),
    meta: {
      title: '对话｜LoreKeeper 设定守护者',
      description: 'Agentic RAG 对话：意图路由 + 工具调用可观测。'
    }
  },
  {
    path: '/rag',
    name: 'rag',
    component: () => import('../pages/Knowledge.vue'),
    meta: {
      title: '知识库｜LoreKeeper 设定守护者',
      description: 'RAG 分块可视化：章节父子分块 + 设定独立块。'
    }
  },
  {
    path: '/review',
    name: 'review',
    component: () => import('../pages/Review.vue'),
    meta: {
      title: '审查｜LoreKeeper 设定守护者',
      description: '一致性审查：上传章节，逐条比对设定，输出矛盾清单，并处理待确认冲突。'
    }
  },
  {
    path: '/test',
    name: 'test',
    component: () => import('../pages/TestLab.vue'),
    meta: {
      title: '测试台｜LoreKeeper 设定守护者',
      description: 'MCP 工具测试 + RAG 检索测试 + 意图路由测试。'
    }
  },
  {
    path: '/data',
    name: 'data',
    component: () => import('../pages/DataManage.vue'),
    meta: {
      title: '数据｜LoreKeeper 设定守护者',
      description: '作品 / 设定 / 伏笔管理。'
    }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

// 路由守卫：无 token 时自动 demo 登录（登录注册暂时忽略）
router.beforeEach(async (to) => {
  const auth = readAuth();
  if (auth && auth.token) return true;
  try {
    await demoLogin();
  } catch (e) {
    // 后端不可用：放行，页面自行显示错误态
  }
  return true;
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
